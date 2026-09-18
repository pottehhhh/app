package ir.weirdnet.client.core

import ir.weirdnet.client.data.model.VpnProfile

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data class Connecting(val profile: VpnProfile) : ConnectionState()
    data class Connected(val profile: VpnProfile, val connectedAtEpochMs: Long) : ConnectionState()
    data class Disconnecting(val profile: VpnProfile) : ConnectionState()
    data class Error(val profile: VpnProfile?, val error: WeirdNetError) : ConnectionState()
}

/** A single point-in-time traffic reading, produced by TrafficMonitor. */
data class TrafficSample(
    val downloadSpeedBps: Long = 0L,
    val uploadSpeedBps: Long = 0L,
    val totalDownloadedBytes: Long = 0L,
    val totalUploadedBytes: Long = 0L
)

/** Whether a given engine is actually usable in this build. */
enum class EngineAvailability {
    AVAILABLE,
    NOT_INSTALLED
}
