# WEIRDNET Architecture

## Layering

```
UI (Compose screens)  --reads-->  VpnStateRepository (StateFlow, singleton)
       |                                    ^
       | (Intents)                          | (writes)
       v                                    |
WeirdNetVpnService  ---owns--->  VpnConnectionManager  ---selects--->  TunnelEngine
                                                                         /        \
                                                              WireGuardEngine   XrayEngine
                                                              (real, functional) (config-ready,
                                                                                   native binary
                                                                                   not bundled)
```

The UI never talks to an engine directly. It reads connection state and
traffic from `VpnStateRepository` (a plain singleton exposing `StateFlow`s)
and sends commands by starting/stopping `WeirdNetVpnService` with an
`Intent`. This mirrors how every real Android VPN app is structured, because
`android.net.VpnService` fundamentally requires a `Service`, and only a
foreground service can safely own a long-lived VPN tunnel.

## Why a single service handles both engines

`WeirdNetVpnService` extends `com.wireguard.android.backend.GoBackend.VpnService`
rather than the bare `android.net.VpnService`. `GoBackend.VpnService` is
itself a thin subclass of `VpnService` (it adds bookkeeping for
`onRevoke()`/process death that `GoBackend` needs), so it's fully usable as an
ordinary `VpnService` for other purposes too. This means once the Xray engine
gets its native dependency (see `docs/XRAY_INTEGRATION.md`), it can call
`Builder()`/`establish()` on the very same service instance to set up its own
TUN interface -- no second service, no duplicated foreground-notification
logic, and `VpnConnectionManager`'s mutex already guarantees the two engines
never run at once.

## Data model

`VpnProfile` (Room entity) is intentionally protocol-agnostic at the storage
layer: `protocol`, `transport`, and `security` are enums, credentials live in
a single encrypted `secret` string, and anything else protocol-specific (SNI,
WebSocket path, Reality public key, WireGuard address/DNS/MTU...) lives in a
flat `extra: Map<String, String>` column. This is what lets the UI (profile
list, home screen, diagnostics) be written once and work for every protocol.

## Secrets

`SecretCipher` wraps AES-256-GCM with a key generated inside the Android
Keystore (`KeyGenParameterSpec`, hardware-backed where the device supports
it). The key material never leaves secure storage; only ciphertext is ever
written to the Room database. Parsers encrypt a profile's credential
immediately as part of building the `VpnProfile`, so plaintext secrets never
exist outside of: (a) the moment they're read from a pasted link/QR/file, and
(b) the moment `XrayConfigBuilder`/`WireGuardEngine` decrypt them right before
handing them to the tunnel engine.

## Adding a new protocol

1. Add an entry to `ProtocolType` (data/model/ProtocolType.kt) with its scheme
   and `EngineFamily`.
2. Write a parser implementing the same shape as the existing ones in
   `core/parser/`, returning `ParseResult`.
3. Register it in `ConfigParser.parse()`'s `when` block.
4. If it needs a new `EngineFamily`, implement `TunnelEngine` and wire it into
   `VpnConnectionManager.engineFor()`.

No UI code needs to change for a new protocol under an existing engine family
(e.g., adding a new Xray-based protocol) -- the profile list, home screen, and
add-profile preview are already generic.

## Future extensibility (explicitly not built yet)

The seams that let future account/subscription/sync features be added without
a rewrite:

- `SettingsRepository`/`ProfileRepository` are the only two places that read
  or write persisted data -- a future remote-sync feature would add a third
  repository (or a sync layer wrapping these two) without touching the UI.
- `VpnProfile` has no `ownerId`/`accountId` field yet, but Room migrations
  (`AppDatabase`'s `version` field) are the intended mechanism for adding one
  later without losing existing local profiles.
- `WeirdNetApplication`'s manual DI container is small enough today that
  swapping in Hilt/Dagger later (if a login/session-scoped dependency graph
  becomes necessary) is a contained, mechanical change.
