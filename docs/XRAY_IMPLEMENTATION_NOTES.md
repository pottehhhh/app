# Xray Integration - Implementation Notes

Record of what changed when real Xray-core connectivity was added to the
already-audited WEIRDNET project (see docs/AUDIT_LOG.md for the prior audit
pass). Nothing described here touches WireGuard, the profile system, the
parsers, the UI, or branding -- all untouched, as required.

## New files

- `app/src/main/java/hev/htproxy/TProxyService.kt` -- hand-written JNI shim
  for hev-socks5-tunnel, matching its documented native contract exactly.
- `app/src/main/java/ir/weirdnet/client/core/xray/XrayCoreBridge.kt` --
  reflection-based bridge to AndroidLibXrayLite's gomobile-generated API.
- `app/src/main/java/ir/weirdnet/client/core/xray/HevTunnelConfigBuilder.kt`
  -- generates the YAML config hev-socks5-tunnel reads at startup.
- `app/src/test/java/ir/weirdnet/client/core/xray/HevTunnelConfigBuilderTest.kt`
- `app/libs/README.md` -- explains the two files that go in that directory.
- `docs/XRAY_INTEGRATION.md` -- rewritten from scratch with verified sources.
- `docs/XRAY_IMPLEMENTATION_NOTES.md` -- this file.

## Modified files

- `app/src/main/java/ir/weirdnet/client/core/xray/XrayEngine.kt` -- replaced
  the `NOT_INSTALLED`-only stub with the real implementation: establishes
  the TUN interface, self-excludes the app from its own VPN routing, starts
  Xray-core via `XrayCoreBridge`, starts hev-socks5-tunnel via
  `TProxyService`, reports real traffic stats, and tears both down cleanly
  on disconnect or error.
- `app/build.gradle.kts` -- added a `fileTree`-based dependency on any
  `.aar` in `app/libs/` (replacing the old placeholder JitPack-coordinate
  comment from the pre-research draft).
- `settings.gradle.kts` -- removed the JitPack repository (no longer
  needed; both components are added as local files, not resolved
  remotely -- confirmed during research that JitPack cannot build
  AndroidLibXrayLite automatically anyway, since it requires the Go/gomobile
  toolchain rather than a standard Gradle build).
- `app/proguard-rules.pro` -- replaced the placeholder comment with real
  `-keep` rules for `libv2ray.**` and `hev.htproxy.**`.
- `app/src/main/java/ir/weirdnet/client/ui/about/AboutScreen.kt` -- added
  license entries for AndroidLibXrayLite, Xray-core itself, and
  hev-socks5-tunnel; updated from "planned integration" wording.
- `README.md` -- updated the capability status table and supported-protocols
  section to reflect the real (code-complete, binary-pending) status.

## Design decisions worth knowing about

**Reflection, not a direct import, for AndroidLibXrayLite.** The first draft
of this integration imported `libv2ray.*` types directly in
`XrayCoreBridge.kt`. That was reverted: since `libv2ray.aar` is an optional,
manually-added file rather than a normal Gradle dependency, a direct import
would have meant this ONE missing file broke compilation of the entire app
module -- including WireGuard, the parsers, the UI, and every existing unit
test, since Gradle/Kotlin compile a module as a single unit with no way to
"optionally compile" one file within it. The reflection-based version in
`XrayCoreBridge.kt` reaches the exact same real API dynamically instead,
so the rest of the project (and its test suite) keeps building regardless
of whether the two AAR files have been added yet, with zero difference in
what actually runs once they are.

**`hev.htproxy.TProxyService.kt` did not need the same treatment.**
hev-socks5-tunnel's AAR ships only a native `.so`, no bundled Kotlin/Java
API -- the JNI shim is WEIRDNET's own hand-written code (matching the
upstream project's documented `external fun` contract), which compiles
regardless of whether the `.so` exists. It only fails at runtime, cleanly,
via a caught `UnsatisfiedLinkError`.

**Self-exclusion (`addDisallowedApplication`) instead of a `protect()`
callback.** The current `2dust/AndroidLibXrayLite` `CoreCallbackHandler`
interface -- confirmed by reading its current source directly -- has no
`Protect()` method (older V2Ray-era wrappers did). Excluding WEIRDNET's own
package from its own `VpnService.Builder()` achieves the same outcome
(Xray-core's outbound socket to the real proxy server bypasses the tunnel
instead of looping back into it) through a mechanism this app fully
controls, rather than depending on an internal behavior of the native
library that couldn't be directly verified.

**A real readiness handshake, not a fixed delay.** Xray-core's `StartLoop`
hands off to internal goroutines that bind its SOCKS inbound asynchronously.
Starting hev-socks5-tunnel immediately after `StartLoop` returns risked its
first connection attempt arriving before that inbound socket was actually
listening. `XrayCoreBridge.start()` blocks (with a bounded timeout) on the
core's own `Startup()` callback as a deterministic readiness signal instead
of guessing with `Thread.sleep`.

## Testing

`HevTunnelConfigBuilderTest.kt` covers the new pure YAML-generation logic.
`XrayCoreBridge` and the native-library-dependent parts of `XrayEngine` are
not unit-testable in this environment (they require the actual AARs and a
device/emulator with a real Xray-core process and TUN interface) -- this
matches how the previously-existing `XrayConfigBuilderTest.kt` already
covered everything that *is* testable without native binaries, and remains
valid and unchanged.

## What remains

Exactly the two files described in `docs/XRAY_INTEGRATION.md`. No further
code changes are anticipated to be required for basic VLESS/VMess/Trojan/
Shadowsocks connectivity once they're added, beyond the version-specific
double-checks that doc calls out explicitly (gomobile-generated name
verification, hev's YAML schema) in case the exact release you download has
drifted slightly from what was confirmed during this implementation.
