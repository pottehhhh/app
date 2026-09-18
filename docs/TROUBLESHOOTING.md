# Troubleshooting

## Build issues

**"Gradle sync failed" / missing wrapper JAR on first open**
Let Android Studio regenerate it: it will prompt "Use Gradle wrapper... fix
this?" — click OK. If it doesn't prompt, go to **File → Settings → Build,
Execution, Deployment → Gradle**, confirm a Gradle JVM is selected, then
**File → Sync Project with Gradle Files**.

**"SDK not found" / "compileSdk 35 not installed"**
Open **Tools → SDK Manager**, check the box for the Android version shown in
the error (35 / Android 15), click Apply.

**Xray-based profiles fail to connect with "Xray engine is not bundled"**
Expected in this build — see `docs/XRAY_INTEGRATION.md`. WireGuard profiles
are unaffected.

## Runtime issues

**Tapping Connect does nothing / no permission dialog appears**
Android only shows the "Connection request" dialog once per app until you
revoke it. If you previously tapped "Don't ask again" or denied it, go to
Android **Settings → Network & internet → VPN**, tap the gear next to
WEIRDNET, and check its permission there directly. `WeirdNetError.VpnPermissionDenied`
in the Diagnostics tab confirms this is what happened.

**Connected but no internet access**
Check the profile's DNS is reachable — try toggling **Settings → VPN →
Custom DNS** to `1.1.1.1`. For WireGuard, also confirm the `AllowedIPs` in the
original `.conf` actually includes `0.0.0.0/0` (or the specific ranges you
need) — a restrictive `AllowedIPs` will connect successfully but route no
traffic outside the VPN's own subnet.

**The notification says "Connecting…" indefinitely**
Check **Diagnostics → Copy diagnostics** for the specific error being logged.
Common causes: wrong port, server unreachable from your current network (some
networks block outbound UDP for WireGuard, or block the exact port a Trojan
server listens on), or an expired/rotated server-side key.

**App was force-closed / "not responding" while connected**
The VPN tunnel is torn down automatically when the service is destroyed
(`WeirdNetVpnService.onDestroy()` resets `VpnStateRepository`), so you won't
be left in a broken half-connected state — just reopen WEIRDNET and reconnect.

## Where to look for more detail

**Settings → Advanced → Debug logging** turns on verbose log capture (never
including secrets — see `WeirdLogger.redact()`). Then **Diagnostics → Copy
diagnostics** produces a paste-ready report for a support request or GitHub
issue, again with all UUIDs/keys/passwords stripped automatically.
