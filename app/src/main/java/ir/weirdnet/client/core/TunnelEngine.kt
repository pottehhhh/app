package ir.weirdnet.client.core

import android.net.VpnService
import ir.weirdnet.client.data.model.VpnProfile

/**
 * The contract every protocol engine (WireGuard, Xray) implements. This is the
 * seam that keeps the UI, VPNService, and profile system protocol-agnostic --
 * see requirement #4. Nothing above this interface knows or cares which engine
 * is actually running.
 */
interface TunnelEngine {

    val availability: EngineAvailability

    /**
     * Establishes the tunnel for [profile] using [vpnService] to create/configure
     * the TUN interface (via [VpnService.Builder]). Suspends until the tunnel is
     * either up or has definitively failed.
     */
    suspend fun connect(profile: VpnProfile, vpnService: VpnService): EngineResult

    /** Tears down whatever tunnel is currently active for this engine, if any. */
    suspend fun disconnect()

    /** True if this engine currently believes it has a live tunnel. */
    fun isActive(): Boolean

    /** Best-effort current traffic counters for the active tunnel, or null if inactive. */
    fun currentTraffic(): TrafficSample?
}

sealed class EngineResult {
    data class Success(val tunFd: Int) : EngineResult()
    data class Failure(val error: WeirdNetError) : EngineResult()
}
