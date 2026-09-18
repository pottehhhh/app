package ir.weirdnet.client.core

import android.net.VpnService
import ir.weirdnet.client.core.wireguard.WireGuardEngine
import ir.weirdnet.client.core.xray.XrayEngine
import ir.weirdnet.client.data.model.EngineFamily
import ir.weirdnet.client.data.model.VpnProfile
import ir.weirdnet.client.data.repository.ProfileRepository
import ir.weirdnet.client.data.repository.SettingsRepository
import ir.weirdnet.client.util.WeirdLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one place that decides "which engine handles this profile" and enforces
 * "only one tunnel at a time" (requirement #10). Owned by
 * [ir.weirdnet.client.service.WeirdNetVpnService]; the UI never talks to engines
 * directly, only to [VpnStateRepository] (read) and this manager indirectly via
 * service Intents (write).
 */
class VpnConnectionManager(
    private val vpnService: VpnService,
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
    scope: CoroutineScope
) {
    private val wireGuardEngine = WireGuardEngine(vpnService)
    private val xrayEngine = XrayEngine()
    private val trafficMonitor = TrafficMonitor(scope)
    private val connectMutex = Mutex()

    private var activeEngine: TunnelEngine? = null
    private var activeProfile: VpnProfile? = null

    suspend fun connect(profile: VpnProfile): Boolean = connectMutex.withLock {
        // Enforce requirement #10: never let two tunnels race. If something is
        // already connecting/connected, tear it down cleanly first.
        if (activeEngine != null) {
            disconnectInternal()
        }

        VpnStateRepository.update(ConnectionState.Connecting(profile))
        val engine = engineFor(profile)

        if (engine.availability == EngineAvailability.NOT_INSTALLED) {
            WeirdLogger.w("VpnConnectionManager", "Engine unavailable for protocol ${profile.protocol}")
            VpnStateRepository.update(ConnectionState.Error(profile, WeirdNetError.EngineNotInstalled))
            return false
        }

        // Global VPN settings (Settings > VPN: Allow IPv6, Custom DNS) are merged
        // in here, at the last possible moment, rather than being baked into the
        // saved profile -- so they always reflect whatever the user's current
        // setting is, apply uniformly to every profile, and never get persisted
        // as if they were part of the profile itself.
        val settings = settingsRepository.settings.first()
        val effectiveProfile = GlobalSettingsApplier.apply(profile, settings)

        val result = engine.connect(effectiveProfile, vpnService)
        return when (result) {
            is EngineResult.Success -> {
                activeEngine = engine
                activeProfile = profile
                val connectedAt = System.currentTimeMillis()
                VpnStateRepository.update(ConnectionState.Connected(profile, connectedAt))
                profileRepository.touchLastConnected(profile.id, connectedAt)
                trafficMonitor.start(engineProvider = { activeEngine })
                WeirdLogger.i("VpnConnectionManager", "Connected profile ${profile.id} via ${profile.protocol}")
                true
            }
            is EngineResult.Failure -> {
                WeirdLogger.e("VpnConnectionManager", "Connect failed: ${result.error.message}")
                VpnStateRepository.update(ConnectionState.Error(profile, result.error))
                false
            }
        }
    }

    suspend fun disconnect() = connectMutex.withLock {
        val profile = activeProfile
        if (profile != null) {
            VpnStateRepository.update(ConnectionState.Disconnecting(profile))
        }
        disconnectInternal()
        VpnStateRepository.update(ConnectionState.Disconnected)
    }

    private suspend fun disconnectInternal() {
        trafficMonitor.stop()
        val traffic = VpnStateRepository.traffic.value
        val profile = activeProfile
        if (profile != null && (traffic.totalDownloadedBytes > 0 || traffic.totalUploadedBytes > 0)) {
            profileRepository.addSessionStats(profile.id, traffic.totalDownloadedBytes, traffic.totalUploadedBytes)
        }
        activeEngine?.disconnect()
        activeEngine = null
        activeProfile = null
        // Without this, VpnStateRepository kept showing the just-ended session's
        // totals -- either briefly (if connect() is switching straight to a new
        // profile, until the new TrafficMonitor's first tick a second later) or
        // indefinitely (on a final disconnect, since nothing else ever clears it).
        VpnStateRepository.updateTraffic(TrafficSample())
    }

    fun isConnected(): Boolean = activeEngine?.isActive() == true

    private fun engineFor(profile: VpnProfile): TunnelEngine = when (profile.protocol.engine) {
        EngineFamily.WIREGUARD -> wireGuardEngine
        EngineFamily.XRAY -> xrayEngine
    }
}
