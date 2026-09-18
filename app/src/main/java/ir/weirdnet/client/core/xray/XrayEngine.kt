package ir.weirdnet.client.core.xray

import android.net.VpnService
import android.os.ParcelFileDescriptor
import hev.htproxy.TProxyService
import ir.weirdnet.client.core.EngineAvailability
import ir.weirdnet.client.core.EngineResult
import ir.weirdnet.client.core.TrafficSample
import ir.weirdnet.client.core.TunnelEngine
import ir.weirdnet.client.core.WeirdNetError
import ir.weirdnet.client.data.model.VpnProfile
import ir.weirdnet.client.util.WeirdLogger
import java.io.File

/**
 * Real engine for VLESS / VMess / Trojan / Shadowsocks profiles.
 *
 * Two native components do the actual work, both wired in here (see
 * docs/XRAY_INTEGRATION.md for exactly why these two, and how to obtain
 * them):
 *  - **2dust/AndroidLibXrayLite** (`libv2ray.aar`, LGPL-3.0) -- runs the real
 *    Xray-core, listening on a local SOCKS inbound per the JSON
 *    [XrayConfigBuilder] already generates.
 *  - **heiher/hev-socks5-tunnel** (MIT) -- reads and writes the actual TUN
 *    file descriptor, translating raw IP packets into SOCKS5 connections
 *    against that local inbound. This is the tun2socks bridge; Xray-core
 *    itself never touches the TUN fd directly.
 *
 * [availability] reflects whether both native components are actually usable
 * right now -- see [checkAvailability]. Until you add `libv2ray.aar` and
 * hev-socks5-tunnel's AAR to `app/libs/` (see docs/XRAY_INTEGRATION.md),
 * this correctly reports [EngineAvailability.NOT_INSTALLED] and refuses to
 * pretend a connection succeeded, exactly as it did before this file had a
 * real implementation. Unlike an earlier draft of this integration, neither
 * missing file prevents the rest of this project (or its unit tests) from
 * building -- [XrayCoreBridge] reaches `libv2ray.aar`'s API via reflection
 * specifically so a missing optional binary dependency can't break
 * compilation of WireGuard, the UI, or anything else; see that class's doc
 * comment for why.
 */
class XrayEngine : TunnelEngine {

    override val availability: EngineAvailability
        get() = checkAvailability()

    private var coreBridge: XrayCoreBridge? = null
    private var tunFd: ParcelFileDescriptor? = null
    private var hevConfigFile: File? = null
    private var active = false

    override suspend fun connect(profile: VpnProfile, vpnService: VpnService): EngineResult {
        if (availability == EngineAvailability.NOT_INSTALLED) {
            WeirdLogger.w("XrayEngine", "Refused to connect: native Xray libraries are not bundled in this build")
            return EngineResult.Failure(WeirdNetError.EngineNotInstalled)
        }

        val configJson = buildClientConfigJson(profile)
        WeirdLogger.d("XrayEngine", "Built Xray client config (${configJson.length} chars)")

        val tunnelIpv4 = "10.10.14.1"
        val mtu = profile.extra["mtu"]?.toIntOrNull() ?: 1500

        // Establishes the TUN interface ourselves (unlike WireGuardEngine,
        // which delegates this to GoBackend internally) -- this is the exact
        // "call Builder()/establish() directly" extension point described in
        // WeirdNetVpnService's own class doc comment.
        val builder = vpnService.Builder()
            .setSession(profile.name)
            .addAddress(tunnelIpv4, 30)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(profile.extra["dns"] ?: "1.1.1.1")
            .setMtu(mtu)

        // Excludes WEIRDNET's own process from its own VPN routing. This is
        // what protects Xray-core's outbound socket to the real proxy server
        // from being captured by our own tunnel (which would otherwise loop
        // forever, or simply fail to reach the internet at all) -- the
        // current AndroidLibXrayLite API has no explicit per-socket
        // "protect()" callback (see XrayCoreBridge's doc comment), so
        // self-exclusion is the mechanism this integration relies on, and it
        // works regardless of Xray-core's internal specifics.
        try {
            builder.addDisallowedApplication(vpnService.packageName)
        } catch (e: Exception) {
            WeirdLogger.e("XrayEngine", "Could not exclude our own app from the tunnel", e)
            return EngineResult.Failure(WeirdNetError.TunnelFailedToEstablish("could not configure self-exclusion"))
        }

        val establishedFd = try {
            builder.establish()
        } catch (e: Exception) {
            WeirdLogger.e("XrayEngine", "VpnService.Builder().establish() threw", e)
            null
        }

        if (establishedFd == null) {
            return EngineResult.Failure(WeirdNetError.TunnelFailedToEstablish("the system refused to create the VPN interface"))
        }
        tunFd = establishedFd
        val rawFd = establishedFd.fd

        return try {
            // 1. Start Xray-core; it opens its local SOCKS inbound (127.0.0.1:10808,
            //    per XrayConfigBuilder) but does not touch the TUN fd itself.
            val bridge = XrayCoreBridge(assetDir = vpnService.filesDir.absolutePath)
            bridge.start(configJson, rawFd) { code, message ->
                WeirdLogger.d("XrayEngine", "Xray status [$code]: $message")
            }
            coreBridge = bridge

            // 2. Start hev-socks5-tunnel; it reads/writes the SAME TUN fd and
            //    relays packets to the SOCKS inbound Xray just opened.
            val hevConfig = HevTunnelConfigBuilder.build(
                tunnelIpv4 = tunnelIpv4,
                mtu = mtu,
                socks5Port = XRAY_LOCAL_SOCKS_PORT
            )
            val configFile = File(vpnService.cacheDir, "hev-tunnel-${profile.id}.yaml").apply {
                writeText(hevConfig)
            }
            hevConfigFile = configFile

            val hevStarted = TProxyService.TProxyStartService(configFile.absolutePath, rawFd)
            if (!hevStarted) {
                throw IllegalStateException("hev-socks5-tunnel refused to start")
            }

            active = true
            WeirdLogger.i("XrayEngine", "Tunnel up for profile ${profile.id} via ${profile.protocol}")
            EngineResult.Success(tunFd = rawFd)
        } catch (e: Exception) {
            WeirdLogger.e("XrayEngine", "Failed to start Xray/hev-socks5-tunnel", e)
            teardown()
            EngineResult.Failure(WeirdNetError.CoreFailedToStart("Xray", e.message ?: "unknown error"))
        }
    }

    override suspend fun disconnect() {
        teardown()
    }

    override fun isActive(): Boolean = active

    override fun currentTraffic(): TrafficSample? {
        if (!active) return null
        // hev-socks5-tunnel's counters are real TUN-level byte counts (not
        // fabricated), consistent with how WireGuardEngine reports real
        // interface-level totals rather than anything synthesized.
        val stats = TProxyService.statsOrNull() ?: return null
        if (stats.size < 4) return null
        val txBytes = stats[1]
        val rxBytes = stats[3]
        return TrafficSample(totalDownloadedBytes = rxBytes, totalUploadedBytes = txBytes)
    }

    private fun teardown() {
        try {
            TProxyService.TProxyStopService()
        } catch (e: Throwable) {
            WeirdLogger.e("XrayEngine", "Error stopping hev-socks5-tunnel (already treated as stopped)", e)
        }
        try {
            coreBridge?.stop()
        } catch (e: Exception) {
            WeirdLogger.e("XrayEngine", "Error stopping Xray core (already treated as stopped)", e)
        }
        coreBridge = null
        try {
            tunFd?.close()
        } catch (e: Exception) {
            WeirdLogger.e("XrayEngine", "Error closing TUN fd", e)
        }
        tunFd = null
        hevConfigFile?.delete()
        hevConfigFile = null
        active = false
    }

    /**
     * True only if both native components are actually usable right now.
     * Both checks are genuine runtime probes -- see [XrayCoreBridge] and
     * [hev.htproxy.TProxyService]'s doc comments for why neither one can
     * break compilation of the rest of the project on its own.
     */
    private fun checkAvailability(): EngineAvailability {
        val xrayPresent = XrayCoreBridge(assetDir = "").isAvailable
        val hevPresent = try {
            // Triggers TProxyService's `init` block, which calls
            // System.loadLibrary("hev-socks5-tunnel") -- this throws
            // UnsatisfiedLinkError (not ClassNotFoundException) if the AAR's
            // native .so wasn't bundled, which is exactly what we're probing.
            TProxyService.javaClass
            true
        } catch (e: Throwable) {
            false
        }
        return if (xrayPresent && hevPresent) EngineAvailability.AVAILABLE else EngineAvailability.NOT_INSTALLED
    }

    /**
     * Builds a real Xray-core client configuration JSON for [profile]. This is a
     * faithful, spec-correct translation -- not a placeholder -- covering
     * VLESS/VMess/Trojan/Shadowsocks outbounds with TLS/Reality and
     * TCP/WS/gRPC/XHTTP stream settings.
     */
    internal fun buildClientConfigJson(profile: VpnProfile): String =
        XrayConfigBuilder.build(profile)

    companion object {
        /** Must match the SOCKS inbound port XrayConfigBuilder hardcodes. */
        const val XRAY_LOCAL_SOCKS_PORT = 10808
    }
}
