package ir.weirdnet.client.core.xray

/**
 * Builds the YAML configuration file `hev-socks5-tunnel` reads at startup
 * (its `TProxyStartService(configPath, fd)` takes a *file path*, not inline
 * content, so [XrayEngine] writes this string to a temp file before calling
 * it). Schema matches the upstream project's documented format
 * (`heiher/hev-socks5-tunnel`) -- see docs/XRAY_INTEGRATION.md for the
 * verified source. If a future version of that project changes its schema,
 * this is the one place to update.
 *
 * [tunnelIpv4] must match the address WEIRDNET itself assigned via
 * `VpnService.Builder().addAddress(...)` in [XrayEngine], since
 * hev-socks5-tunnel uses it for internal packet bookkeeping even though the
 * TUN file descriptor itself is supplied externally (created by Android's
 * VpnService, not by hev-socks5-tunnel).
 */
internal object HevTunnelConfigBuilder {

    fun build(
        tunnelIpv4: String,
        mtu: Int,
        socks5Port: Int,
        socks5Address: String = "127.0.0.1"
    ): String = buildString {
        appendLine("tunnel:")
        appendLine("  name: tun0")
        appendLine("  mtu: $mtu")
        appendLine("  multi-queue: false")
        appendLine("  ipv4: $tunnelIpv4")
        appendLine("socks5:")
        appendLine("  port: $socks5Port")
        appendLine("  address: $socks5Address")
        appendLine("  udp: 'udp'")
    }
}
