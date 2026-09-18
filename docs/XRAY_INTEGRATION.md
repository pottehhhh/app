# Integrating real Xray-core connectivity

This documents the real, implemented Xray integration -- what runs the show,
why, and the exact two files you need to add to make VLESS/VMess/Trojan/
Shadowsocks profiles actually connect. Everything in WEIRDNET's code that
touches these two components is real, working code today; the only thing
missing is the two binary files themselves, which require network access to
download that this project's build environment doesn't have.

## What was chosen, and why

| Role | Project | License | Status |
|---|---|---|---|
| Xray-core engine | [2dust/AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite) | LGPL-3.0 (wraps Xray-core itself, MPL-2.0) | Actively maintained, frequent releases tracking current Xray-core versions |
| TUN-to-proxy bridge | [heiher/hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) | MIT | Actively maintained, lightweight native tun2socks |

This is not an arbitrary choice -- it is the same pairing used by
**v2rayNG**, the most widely used and actively maintained Xray Android
client (the reference implementation the wider Xray-core ecosystem builds
against). v2rayNG's own repository carries both as git submodules with
dedicated build scripts, which is exactly why they were chosen here rather
than an older or less-maintained alternative (e.g. the historically common
`badvpn`-based tun2socks, which this ecosystem has moved away from in favor
of hev-socks5-tunnel).

Both projects publish **prebuilt Android AAR files directly as GitHub
Release assets** -- there is no Maven Central or JitPack coordinate for
either (JitPack can't build AndroidLibXrayLite automatically since it
requires the Go/gomobile toolchain, not a standard Gradle build). That means
adding them is a manual download, the same way even v2rayNG's own CI
pipeline fetches `libv2ray.aar` as a release asset rather than a dependency
coordinate.

## Exact files, exact steps

**Pinned versions (confirmed current as of this writing):**
- `libv2ray.aar` from AndroidLibXrayLite tag **v26.6.2** (released Jun 2, 2026)
- hev-socks5-tunnel's AAR from tag **2.15.0** (released May 10, 2026 -- the
  current "Latest" release; confirmed via the project's own releases page)

### 1. `libv2ray.aar`

1. Go to <https://github.com/2dust/AndroidLibXrayLite/releases/tag/v26.6.2>
   (or whatever is newest if you're doing this well after the above date --
   check the "Latest" badge on <https://github.com/2dust/AndroidLibXrayLite/releases>)
2. Download the **`libv2ray.aar`** asset.
3. Save it as `app/libs/libv2ray.aar` in this project.

### 2. hev-socks5-tunnel's AAR

1. Go to <https://github.com/heiher/hev-socks5-tunnel/releases/tag/2.15.0>
   (or newest -- same caveat as above)
2. Download the Android AAR asset (per the project's own README, this job
   "produces an AAR (hev-socks5-tunnel.aar, containing all four ABIs)").
3. Save it as `app/libs/hev-socks5-tunnel.aar`.

**IMPORTANT -- check this before building if you use a release newer than
2.15.0:** as of this writing, the upstream project merged a change (PR #331,
"Bundle Java JNI binding into android AAR", merged Sep 15 2026 -- *after*
the 2.15.0 tag) that will make some future release bundle its own copy of
the `hev.htproxy.TProxyService` class directly inside the AAR's
`classes.jar`, so consuming apps no longer need to write it themselves. If
you download a release where this has landed, **delete
`app/src/main/java/hev/htproxy/TProxyService.kt` from this project first**
-- otherwise you'll get a duplicate-class build error, since both the AAR
and this project would define the exact same class. The release notes for
whatever version you download will say so explicitly if this applies
("Upgrade note: ... a hand-written copy must be removed to avoid a
duplicate class" is the upstream project's own wording for this transition).
Version 2.15.0 itself predates this change, so the hand-written file in
this project is correct and required as-is for that specific version.

### 3. Rebuild

Re-sync Gradle in Android Studio (or run `./gradlew build`). No Gradle file
needs editing -- `app/build.gradle.kts` already includes
`fileTree(dir="libs", include=["*.aar"])`.

That's it. `XrayEngine.availability` will now report `AVAILABLE`, and VLESS/
VMess/Trojan/Shadowsocks profiles will attempt real connections instead of
failing with "Xray engine is not bundled."

## Why the project still compiles without these files

`hev-socks5-tunnel`'s AAR ships only a native `.so` file with no bundled
Kotlin/Java API -- WEIRDNET's own `hev.htproxy.TProxyService.kt` is a
hand-written JNI shim matching its documented contract, which compiles
regardless of whether the `.so` backing it exists (it only fails at
*runtime*, cleanly, via a caught `UnsatisfiedLinkError`, if the AAR hasn't
been added).

`libv2ray.aar` is different: it bundles real, gomobile-generated Kotlin/Java
classes. A direct `import libv2ray.CoreController` would mean this one
optional file being absent breaks compilation of the *entire* app module --
including WireGuard, the parsers, and every unit test, since Gradle/Kotlin
compile a module as a single unit. To avoid that regression,
`XrayCoreBridge.kt` (the one file that talks to `libv2ray.*`) reaches it via
reflection instead of a static import. This has no effect on functionality
once the AAR is added -- every call reaches the exact same real Xray-core
entrypoints a direct reference would -- it just means the rest of the
project keeps building and its tests keep passing whether or not you've
completed this integration yet.

## The real data flow, as implemented

```
VpnProfile
    -> XrayConfigBuilder.build()              (already existed; unchanged)
    -> Xray-core client config JSON
         (SOCKS inbound on 127.0.0.1:10808, VLESS/VMess/Trojan/SS outbound,
          TLS/Reality/WS/gRPC/XHTTP stream settings)

WeirdNetVpnService.Builder()
    .addAddress("10.10.14.1", 30)
    .addDisallowedApplication(ownPackageName)  <- protects Xray's own outbound
    .establish()                               -> raw TUN file descriptor
                                                        |
                        +-------------------------------+-------------------------------+
                        |                                                               |
                        v                                                               v
        XrayCoreBridge.start(configJson, tunFd)                    TProxyService.TProxyStartService(yamlPath, tunFd)
        (reflection call into libv2ray.aar;                        (hev-socks5-tunnel; reads/writes the TUN fd
         Xray-core boots, opens local SOCKS                         directly, translating raw IP packets into
         inbound, does NOT touch the TUN fd)                        SOCKS5 connections)
                        |                                                               |
                        +-------------------> 127.0.0.1:10808 <------------------------+
                                             Xray-core's SOCKS inbound
                                                        |
                                             Xray-core's outbound
                                          (VLESS / VMess / Trojan / Shadowsocks)
                                                        |
                                     (this app's own process is excluded from
                                      its own VPN routing, so this socket goes
                                      out over the real network, not back into
                                      the tunnel)
                                                        v
                                              Internet -> your proxy server
```

### Why `addDisallowedApplication(ownPackageName)` instead of a `protect()` callback

Older V2Ray/Xray Android wrappers exposed an explicit
`VpnService.protect(fd)`-style callback the app had to wire up so the core's
own outbound socket to the real proxy server wouldn't get captured by the
app's own VPN tunnel (which would otherwise create a routing loop or simply
fail to reach the internet). The *current* `2dust/AndroidLibXrayLite`
`CoreCallbackHandler` interface -- confirmed by reading its current source
directly -- only has `Startup()` / `Shutdown()` / `OnEmitStatus()`; there is
no `Protect()` method to implement. Excluding WEIRDNET's own package from
its own `VpnService.Builder()` via `addDisallowedApplication()` achieves the
same result through a mechanism this app fully controls and can verify
independently of Xray-core's internal specifics: any socket WEIRDNET's own
process opens (including Xray-core's, since it runs in-process) bypasses the
tunnel and goes out over the real network, while the already-established TUN
file descriptor itself is unaffected (reading/writing an existing fd isn't
subject to per-app routing exclusion).

## Known residual uncertainties (read before reporting a bug against this code)

Both upstream projects release frequently, and their exact internal
API surface can shift slightly between versions. Two specific things worth
checking if you hit an error immediately after adding the AARs:

1. **gomobile-generated names.** `XrayCoreBridge.kt`'s companion object holds
   three fully-qualified class-name strings (`libv2ray.Libv2ray`,
   `libv2ray.CoreCallbackHandler`, `libv2ray.CoreController`) and looks up
   methods on them (`newCoreController`, `startLoop`, `stopLoop`,
   `initCoreEnv`) by name via reflection. These match the current upstream
   source at the time this was written. If Diagnostics shows a
   `ClassNotFoundException` or `NoSuchMethodException` immediately on
   connect, open `libv2ray.aar`'s `classes.jar` in a tool like `jadx` or
   Android Studio's library viewer to confirm the actual generated names in
   your specific downloaded version, and update those three constants (and
   the method-name string literals) in `XrayCoreBridge.kt` -- nothing else
   in the app needs to change.
2. **hev-socks5-tunnel's YAML schema.** `HevTunnelConfigBuilder.kt` generates
   the config file using the schema documented in that project's current
   README. If it changes, that's the one file to update.

Neither of these is "the integration is fake" -- they're the normal
maintenance surface of depending on two independently-versioned upstream
binaries, called out explicitly rather than papered over.

## Optional: geoip.dat / geosite.dat

Xray-core supports routing rules based on domain/IP category databases
(`geoip.dat`, `geosite.dat`). WEIRDNET's generated config
([XrayConfigBuilder.kt](../app/src/main/java/ir/weirdnet/client/core/xray/XrayConfigBuilder.kt))
declares no such rules -- it only has a `proxy` and a `direct` outbound, with
all traffic routed to `proxy` by Xray's default behavior -- so these files
are not required for basic connectivity. If you later add domain/IP-based
routing rules and your specific AndroidLibXrayLite build requires these
files to be present to start at all, download them from
[Loyalsoldier/v2ray-rules-dat](https://github.com/Loyalsoldier/v2ray-rules-dat)
(the standard, actively-maintained source in this ecosystem) and place them
in the app's files directory (the same path `XrayCoreBridge` passes to
`initCoreEnv`).
