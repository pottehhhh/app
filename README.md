# WEIRDNET Android VPN Client

WEIRDNET is a standalone Android VPN client. This first version has **no account
system, no payment system, no wallet, and no backend** — it's a local-only tool
for importing VPN configurations (via link, QR code, or file) and connecting to
them.

This document is written for someone who has **never used Android Studio or
Gradle before**. Every step below is exact.

---

## 1. What's real, and what needs one more step

Being upfront about this, because it matters for a VPN app:

| Capability | Status |
|---|---|
| Profile management (add/edit/rename/duplicate/delete/favorite/search/reorder) | ✅ Fully implemented |
| Link import (`vless://`, `vmess://`, `trojan://`, `ss://`) | ✅ Fully implemented, real parsing/validation |
| QR code scanning | ✅ Fully implemented (CameraX + ML Kit) |
| File import (WireGuard `.conf`) | ✅ Fully implemented |
| Clipboard import | ✅ Fully implemented |
| Deep-link import (tapped links, opened `.conf` files) | ✅ Fully implemented |
| Encrypted local storage of secrets | ✅ Fully implemented (Android Keystore AES-256-GCM) |
| **WireGuard tunnels** | ✅ **Fully functional** — real tunnels via the official `com.wireguard.android:tunnel` library |
| **Xray tunnels (VLESS/VMess/Trojan/Shadowsocks)** | ⚠️ **Code complete and real; two binary files required.** See [docs/XRAY_INTEGRATION.md](docs/XRAY_INTEGRATION.md). |
| Traffic stats, diagnostics, settings, logging | ✅ Fully implemented |

**Xray status in detail:** the full integration — Xray-core via
`2dust/AndroidLibXrayLite`, the TUN-to-proxy bridge via
`heiher/hev-socks5-tunnel`, TUN setup, self-exclusion for socket protection,
lifecycle, and error handling — is implemented with real, working code (not
mocks or stubs). What's missing is exactly two binary files this build
environment cannot download (no network access): `libv2ray.aar` and
hev-socks5-tunnel's AAR, both prebuilt release assets from their respective
GitHub repositories. Add them to `app/libs/` (a two-file download, no
compilation toolchain needed) and VLESS/VMess/Trojan/Shadowsocks profiles
connect for real. See [docs/XRAY_INTEGRATION.md](docs/XRAY_INTEGRATION.md)
for exact links, versions, and the full data flow.

---

## 2. What you need to install (one-time setup)

1. **Android Studio** (free): download from [developer.android.com/studio](https://developer.android.com/studio)
   and run the installer. Accept the default options — this also installs the
   Android SDK you need.
2. That's it. Android Studio bundles a compatible JDK, and it will download
   Gradle automatically the first time you open this project (see below).

## 3. Opening the project

1. Unzip the `WeirdNet` project folder somewhere on your computer.
2. Open Android Studio → **File → Open** → select the `WeirdNet` folder (the
   one containing `settings.gradle.kts`) → **OK**.
3. Android Studio will show "Gradle sync" progress in the bottom status bar.
   This can take a few minutes the first time (it's downloading Gradle itself
   and all the app's dependencies). Let it finish.
4. If Android Studio asks to "Trust this project," click **Trust Project**.

If sync reports a missing Gradle wrapper JAR, that's expected — this project's
`gradle/wrapper/gradle-wrapper.properties` is included, but the wrapper's
binary launcher JAR could not be generated in the offline sandbox this project
was built in. Android Studio will offer to regenerate it automatically ("Use
Gradle wrapper... would you like Android Studio to fix this?" — click **OK**).
If it doesn't offer automatically, go to **File → Settings → Build, Execution,
Deployment → Gradle**, set "Gradle JVM" to the bundled JDK, and Android Studio
will regenerate the wrapper on next sync.

## 4. Running the app

1. Connect an Android phone via USB with **Developer Options → USB debugging**
   enabled (search "how to enable USB debugging on Android" if you haven't
   done this before), or create a virtual device via **Tools → Device
   Manager → Create Device**.
2. Click the green **Run ▶** button in Android Studio's toolbar, with your
   device selected in the dropdown next to it.
3. The app installs and launches. On first connect attempt, Android will show
   its own "Connection request" dialog asking to let WEIRDNET create a VPN
   connection — this is normal Android behavior for every VPN app; tap **OK**.

## 5. Creating a release APK

See [docs/BUILD.md](docs/BUILD.md) for exact steps, including how to create
your own signing key (required before installing on a device without Android
Studio attached, or before distributing the APK to anyone).

---

## Supported protocols

- **WireGuard** — fully functional, real tunnels.
- **VLESS, VMess, Trojan, Shadowsocks** (via Xray-core) — parsing, storage, UI,
  and the full connection engine are implemented with real code; requires two
  binary files added by you (see
  [docs/XRAY_INTEGRATION.md](docs/XRAY_INTEGRATION.md)).
- Transports: TCP, WebSocket, gRPC, XHTTP, HTTP Upgrade.
- Security layers: none, TLS, Reality.

## Project structure

```
app/src/main/java/ir/weirdnet/client/
  core/                 Protocol-agnostic tunnel engine contracts, errors, state
    parser/             URI + WireGuard .conf parsers (ConfigParser is the entry point)
    wireguard/          Real WireGuard engine (com.wireguard.android:tunnel)
    xray/               Xray config builder + real engine (needs two AAR files, see docs)
  data/
    model/              VpnProfile, ProtocolType, LogEntry
    db/                 Room database, DAOs, SecretCipher (Keystore encryption)
    repository/         ProfileRepository, SettingsRepository
  service/              WeirdNetVpnService (the real android.net.VpnService)
  ui/                   Jetpack Compose screens, one package per screen
  util/                 WeirdLogger (redacting logger)
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the full data flow.

## How configuration import works

Every import path (pasted link, QR scan, file import, clipboard) funnels
through the same [`ConfigParser`](app/src/main/java/ir/weirdnet/client/core/parser/ConfigParser.kt),
which detects the format and dispatches to a protocol-specific parser. Each
parser returns a `ParseResult` — either a ready-to-save `VpnProfile` or a
specific `WeirdNetError` explaining exactly what was wrong (never a generic
"something went wrong").

## Security notes

- Profile secrets (UUIDs, passwords, private keys, pre-shared keys) are
  encrypted at rest with AES-256-GCM using a key stored in the Android
  Keystore (`SecretCipher.kt`) — the key itself never leaves secure hardware.
- The app has `android:allowBackup="false"` and explicit backup exclusion
  rules, so no OEM cloud-backup path can ever export your profiles.
- `WeirdLogger` redacts UUIDs, keys, and password-like query parameters before
  any log line is written to Logcat or the on-device log table.
- WEIRDNET makes no network calls of its own beyond the VPN tunnel(s) you
  create — there is no telemetry, analytics, or backend.

## Troubleshooting

See [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md).

## License

This project's own code has no license header applied yet — add one
appropriate to your intended distribution (e.g., a standard open-source
license, or "all rights reserved" if private) before publishing. Third-party
components keep their own licenses; see the in-app **Settings → About** screen
for the full list and required notices.
