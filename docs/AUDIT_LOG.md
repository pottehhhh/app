# Audit Log

This document records the findings of a full project audit performed after
the initial build, before starting Xray-core integration.

## Critical (would have broken the build or crashed at runtime)

1. **Gradle version mismatch**: `org.jetbrains.kotlin.plugin.compose` was
   pinned to Kotlin 1.9.24, but that plugin does not exist before Kotlin
   2.0.0 -- Gradle sync would have failed at plugin resolution. Fixed by
   bumping Kotlin and KSP to 2.0.21 / 2.0.21-1.0.28.
2. **Invalid XML in AndroidManifest.xml**: two doc-comments used `--`
   (double hyphen) inside `<!-- -->` blocks, which the XML spec forbids
   anywhere in a comment's body. This would have failed AAPT2 resource
   compilation. Fixed by rewording those comments.
3. **Foreground service start ordering**: `startForeground()` was only
   called when a valid profile ID extra was present, but the service can
   still be launched via `startForegroundService()` with an invalid/missing
   extra, risking `ForegroundServiceDidNotStartInTimeException` on Android
   12+. Fixed: it's now called unconditionally and synchronously first.
4. **Plain `Job()` instead of `SupervisorJob()`** in the VPN service's
   coroutine scope -- one failed coroutine (e.g. a bad connect attempt)
   would have cancelled the whole scope, silently breaking traffic
   monitoring and any later disconnect request for the rest of the
   service's life.
5. **`WireGuardEngine.disconnect()` had no exception handling**, unlike
   `connect()` -- a `BackendException` during teardown could propagate
   uncaught and crash the service.
6. **`onDestroy()` never tore down an active tunnel** if the service died
   unexpectedly (not via the normal disconnect path) -- added synchronous
   best-effort cleanup.
7. **Incorrect API-level gate for `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`** --
   initially gated on API 29 (Q); that constant didn't exist until API 34.
   Fixed to gate on `UPSIDE_DOWN_CAKE`.

## Real functional gaps ("incomplete" or "fake" per the audit brief)

8. **Deep-link handling was a documented no-op.** Built a real
   `DeepLinkRepository` and wired `MainActivity` → `WeirdNetNavHost` →
   `AddProfileScreen` end-to-end, for both scheme links and `.conf` file
   opens (including reading file content off the main thread).
9. **The manifest advertised a `wireguard://` deep-link scheme that nothing
   parsed** -- no such standardized URI format exists in practice (unlike
   vless/vmess/trojan/ss). Removed the false claim; real WireGuard import
   remains fully supported via `.conf` files and QR-scanned config text.
10. **Camera/ML Kit resource leak** in the QR scanner: the camera was bound
    to the Activity's lifecycle, not the screen's, so it kept running (and
    the camera indicator light stayed on) after navigating away. Fixed with
    a `DisposableEffect` that unbinds on leaving the screen.
11. **Permanently-denied camera permission was a dead end** -- added a
    fallback to open the app's system settings page.
12. **File import read the picked document on the main thread**, risking an
    ANR with cloud-backed document providers. Moved to a background
    coroutine, and added a missing error path (a failed read previously did
    nothing visible to the user at all).
13. **Traffic stats were never reset between sessions** -- switching
    profiles or disconnecting could leave stale download/upload numbers on
    screen.
14. **"Change profile" on the Home screen was a dead end**: `selectProfile()`
    existed on `HomeViewModel` but nothing in the UI ever called it, and it
    had its own reactivity bug (a plain `var` mutated outside the `combine()`
    that builds UI state, so calling it wouldn't have visibly done anything
    anyway). Replaced with a proper `StateFlow` and built a real quick-picker
    dialog.
15. **"Reorder profiles" (an explicit requirement) had no UI at all** --
    `reorder()` existed on `ProfilesViewModel` but was never called from
    anywhere, and every profile defaulted to the same `sortOrder`, so it
    would not have behaved predictably even once wired up. Implemented real
    Move Up/Down actions that re-normalize the whole visible list, disabled
    automatically when a search/filter would make list-position-based moves
    incoherent.
16. **Tapping Connect during "DISCONNECTING…" silently triggered an
    immediate reconnect** instead of doing nothing, because the button's
    click handler didn't have a case for that transitional state.
17. **Settings > VPN's "Allow IPv6" and "Custom DNS" toggles did nothing.**
    They were persisted to DataStore and displayed correctly, but nothing
    in the connection path ever read them -- textbook "fake functionality."
    Built `GlobalSettingsApplier`, wired it into `VpnConnectionManager` so
    both settings now genuinely affect the WireGuard tunnel that's actually
    established (DNS override, IPv6 route stripping), and added unit tests.
18. **Canceling the Custom DNS dialog after enabling the toggle left it
    enabled with no value ever confirmed** -- "Cancel" didn't cancel.
    Decoupled enabling the setting (the switch) from editing its value (a
    separate row), so canceling the editor now has no side effect on
    whether the setting is on.

## Cleanup

- Removed several unused imports and one dead field (`activeProfileId` in
  `WireGuardEngine`) surfaced by a project-wide static scan.
- Verified: no package/directory mismatches, no missing string/drawable
  resources, no other stray `TODO`/placeholder markers outside the
  documented Xray native-binding seam, and every manifest-declared
  component class actually exists.

## Logo integrity (verified programmatically, not just visually)

- The in-app logo drawable (`drawable-nodpi/weirdnet_logo.png`, used on the
  Home header and About screen) is **byte-for-byte pixel-identical** to the
  originally supplied artwork (MD5 hash match).
- The legacy launcher icon is a pure LANCZOS resize of the original with no
  other modification.
- The adaptive-icon foreground layer only had its near-black background
  pixels keyed to transparent (required for Android's adaptive icon mask
  system to avoid a visible seam) -- the artwork itself was not redrawn,
  recolored, or reinterpreted.

## What still remains before VLESS/VMess/Trojan/Shadowsocks can connect

Nothing in this audit changed that picture: it is still exactly the one gap
described in `docs/XRAY_INTEGRATION.md`. Everything *around* Xray -- parsing,
storage, the UI, the exact config JSON Xray-core expects, and the two
`TODO(xray-native)`-marked call sites in `XrayEngine.kt` -- is complete and
untouched by fake functionality. What's missing is specifically:

1. **The compiled Xray-core native binary itself** (an AAR built with
   `gomobile`), which this environment cannot fetch or compile without
   network access. Adding it is a Gradle dependency line plus following
   `docs/XRAY_INTEGRATION.md`'s two marked TODOs in `XrayEngine.kt` --
   establishing the TUN interface via `vpnService.Builder()` (mechanically
   identical to what `WireGuardEngine` already does) and handing the
   already-complete `buildClientConfigJson()` output to that library's start
   function.
2. **A tun2socks bridge** to route raw IP packets from the TUN file
   descriptor into the SOCKS inbound the generated config already declares
   -- some Xray AARs bundle one; if not, one more small dependency.

No other architecture, storage, UI, or parsing work remains for those four
protocols.
