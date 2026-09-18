package hev.htproxy

/**
 * JNI bindings for `heiher/hev-socks5-tunnel` (MIT licensed), the native
 * tun2socks bridge used by this app's real Xray integration -- see
 * [ir.weirdnet.client.core.xray.XrayEngine] and docs/XRAY_INTEGRATION.md.
 *
 * This object's package (`hev.htproxy`), class name (`TProxyService`), method
 * names, and `System.loadLibrary` argument are dictated by the upstream
 * project's own JNI contract -- they are not WEIRDNET's choice and must not
 * be renamed, or the native `.so` bundled in the AAR (built with these exact
 * symbols) will fail to bind at runtime with an `UnsatisfiedLinkError`.
 *
 * Usage: [TProxyStartService] is handed a path to a YAML config file (see
 * [ir.weirdnet.client.core.xray.HevTunnelConfigBuilder]) and the raw TUN file
 * descriptor from [android.net.VpnService.Builder.establish]. It then reads
 * and writes that fd directly, translating IP packets into SOCKS5
 * connections against the address/port declared in the YAML config -- in
 * this app's case, Xray-core's own local SOCKS inbound.
 */
object TProxyService {
    @JvmStatic external fun TProxyStartService(configPath: String, fd: Int): Boolean
    @JvmStatic external fun TProxyStopService(): Boolean
    @JvmStatic external fun TProxyIsRunning(): Boolean

    /**
     * Returns cumulative tunnel-level counters as `[tx_packets, tx_bytes,
     * rx_packets, rx_bytes]`, or null if the native call fails for any reason
     * (e.g. the service isn't running). Used by [XrayEngine.currentTraffic]
     * for real, non-fabricated traffic stats -- matching how
     * [ir.weirdnet.client.core.wireguard.WireGuardEngine] reports real
     * interface-level counters rather than anything synthesized.
     */
    fun statsOrNull(): LongArray? = try {
        TProxyGetStats()
    } catch (e: Throwable) {
        null
    }

    @JvmStatic external fun TProxyGetStats(): LongArray

    init {
        System.loadLibrary("hev-socks5-tunnel")
    }
}
