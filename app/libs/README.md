# app/libs/

This directory is intentionally empty in this checkout except for this file.
Drop the two files below in here to enable real VLESS/VMess/Trojan/Shadowsocks
connectivity. Both are picked up automatically by `app/build.gradle.kts`'s
`fileTree` dependency -- no Gradle file needs editing.

Full instructions, exact versions, and why these two specific projects were
chosen: see `docs/XRAY_INTEGRATION.md` at the repository root.

## 1. `libv2ray.aar`

- Source: https://github.com/2dust/AndroidLibXrayLite/releases
- Download the `libv2ray.aar` asset from the latest release.
- License: LGPL-3.0 (wraps Xray-core itself, MPL-2.0).
- **Required for this project to compile at all** -- WEIRDNET's Xray engine
  code imports this library's generated classes directly.

## 2. `hev-socks5-tunnel.aar` (filename may differ slightly by release)

- Source: https://github.com/heiher/hev-socks5-tunnel/releases
- Download the prebuilt Android AAR asset from the latest release.
- License: MIT.
- Only required at runtime, not for compilation -- the project builds
  without it, but Xray-based profiles will report "engine not installed"
  until it's added.

After adding both files, re-sync Gradle in Android Studio (or re-run
`./gradlew build`) and rebuild.
