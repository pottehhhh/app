package ir.weirdnet.client.data.model

/**
 * Every protocol WEIRDNET's UI, database, and parser layer know how to represent.
 *
 * IMPORTANT: modeling a protocol here does NOT by itself mean a live tunnel can be
 * established for it. Whether a protocol can actually connect depends on which
 * [ir.weirdnet.client.core.TunnelEngine] backs it at runtime:
 *
 *  - [WIREGUARD] is backed by [ir.weirdnet.client.core.wireguard.WireGuardEngine],
 *    which wraps the official `com.wireguard.android:tunnel` library. This is a
 *    real, functional engine.
 *
 *  - [VLESS], [VMESS], [TROJAN], [SHADOWSOCKS] are backed by
 *    [ir.weirdnet.client.core.xray.XrayEngine]. That engine requires the Xray-core
 *    native binary/AAR described in docs/XRAY_INTEGRATION.md. Until that dependency
 *    is added by the developer, [ir.weirdnet.client.core.xray.XrayEngine] will
 *    report [ir.weirdnet.client.core.EngineAvailability.NOT_INSTALLED] and refuse
 *    to pretend a connection succeeded -- see that class for the exact behavior.
 */
enum class ProtocolType(val scheme: String, val displayName: String, val engine: EngineFamily) {
    VLESS("vless", "VLESS", EngineFamily.XRAY),
    VMESS("vmess", "VMess", EngineFamily.XRAY),
    TROJAN("trojan", "Trojan", EngineFamily.XRAY),
    SHADOWSOCKS("ss", "Shadowsocks", EngineFamily.XRAY),
    WIREGUARD("wireguard", "WireGuard", EngineFamily.WIREGUARD);

    companion object {
        fun fromScheme(scheme: String): ProtocolType? =
            entries.firstOrNull { it.scheme.equals(scheme, ignoreCase = true) }
    }
}

enum class EngineFamily {
    XRAY,
    WIREGUARD
}

/** Sub-transport used by an Xray-family profile (stream settings). */
enum class TransportType(val label: String) {
    TCP("tcp"),
    WEBSOCKET("ws"),
    GRPC("grpc"),
    XHTTP("xhttp"),
    HTTP_UPGRADE("httpupgrade"),
    KCP("kcp");

    companion object {
        fun fromWire(value: String?): TransportType =
            entries.firstOrNull { it.label.equals(value, ignoreCase = true) } ?: TCP
    }
}

/** Security layer used on top of the transport. */
enum class SecurityType(val label: String) {
    NONE("none"),
    TLS("tls"),
    REALITY("reality");

    companion object {
        fun fromWire(value: String?): SecurityType =
            entries.firstOrNull { it.label.equals(value, ignoreCase = true) } ?: NONE
    }
}
