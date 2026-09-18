package ir.weirdnet.client.core.wireguard

import android.content.Context
import android.net.VpnService
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import ir.weirdnet.client.core.EngineAvailability
import ir.weirdnet.client.core.EngineResult
import ir.weirdnet.client.core.TrafficSample
import ir.weirdnet.client.core.TunnelEngine
import ir.weirdnet.client.core.WeirdNetError
import ir.weirdnet.client.data.model.VpnProfile
import ir.weirdnet.client.util.WeirdLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real WireGuard tunnel engine backed by `com.wireguard.android:tunnel`
 * (userspace `wireguard-go` via JNI, Apache-2.0 licensed, official upstream
 * library at https://git.zx2c4.com/wireguard-android/). This is a functional
 * implementation -- calling [connect] genuinely negotiates and establishes a
 * WireGuard tunnel, it does not simulate one.
 *
 * [vpnService] passed to [connect] must be [WeirdNetVpnService], which extends
 * [GoBackend.VpnService] specifically so this engine can hand tunnel ownership
 * to it -- see that class for why.
 */
class WireGuardEngine(context: Context) : TunnelEngine {

    private val backend: Backend = GoBackend(context.applicationContext)
    private var activeTunnel: SimpleTunnel? = null

    override val availability: EngineAvailability = EngineAvailability.AVAILABLE

    override suspend fun connect(profile: VpnProfile, vpnService: VpnService): EngineResult =
        withContext(Dispatchers.IO) {
            try {
                val secrets = WireGuardSecrets.fromEncryptedString(profile.secret)
                val config = buildConfig(profile, secrets)
                    ?: return@withContext EngineResult.Failure(
                        WeirdNetError.InvalidConfiguration("one or more WireGuard keys are malformed")
                    )

                val tunnel = SimpleTunnel(profile.name)
                backend.setState(tunnel, Tunnel.State.UP, config)
                activeTunnel = tunnel
                WeirdLogger.i("WireGuardEngine", "Tunnel up for profile ${profile.id}")
                // GoBackend owns the underlying tun fd internally; we don't get a
                // raw fd back (unlike the Xray path), so we report success with a
                // sentinel value. Callers must use isActive()/currentTraffic() for
                // subsequent state, not the returned fd.
                EngineResult.Success(tunFd = -1)
            } catch (e: com.wireguard.android.backend.BackendException) {
                WeirdLogger.e("WireGuardEngine", "BackendException while establishing tunnel", e)
                EngineResult.Failure(WeirdNetError.TunnelFailedToEstablish(e.message ?: e.reason.toString()))
            } catch (e: Exception) {
                WeirdLogger.e("WireGuardEngine", "Unexpected failure establishing tunnel", e)
                EngineResult.Failure(WeirdNetError.TunnelFailedToEstablish(e.message ?: "unknown error"))
            }
        }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        val tunnel = activeTunnel ?: return@withContext
        try {
            backend.setState(tunnel, Tunnel.State.DOWN, null)
        } catch (e: Exception) {
            // Deliberately swallowed (after logging): disconnect() is called from
            // VpnConnectionManager during error recovery and from the service's
            // onDestroy() best-effort cleanup, in both of which a teardown failure
            // must not itself throw -- there is nothing further the caller could
            // usefully do in response, and letting this propagate risked crashing
            // the service mid-cleanup.
            WeirdLogger.e("WireGuardEngine", "Error while tearing down tunnel (already treated as disconnected)", e)
        } finally {
            activeTunnel = null
        }
    }

    override fun isActive(): Boolean =
        activeTunnel?.let { backend.getState(it) == Tunnel.State.UP } ?: false

    override fun currentTraffic(): TrafficSample? {
        val tunnel = activeTunnel ?: return null
        val stats = (backend as? GoBackend)?.getStatistics(tunnel) ?: return null
        // com.wireguard.android.backend.Statistics exposes cumulative per-peer rx/tx;
        // we sum across peers. Speed (bytes/sec) is derived by TrafficMonitor by
        // differencing successive totals, not computed here.
        var totalRx = 0L
        var totalTx = 0L
        for (peer in stats.peers()) {
            totalRx += stats.rx(peer)
            totalTx += stats.tx(peer)
        }
        return TrafficSample(totalDownloadedBytes = totalRx, totalUploadedBytes = totalTx)
    }

    private fun buildConfig(profile: VpnProfile, secrets: WireGuardSecrets): Config? = try {
        val ifaceBuilder = Interface.Builder()
            .parsePrivateKey(secrets.privateKey)
            .parseAddresses(profile.extra["address"] ?: "")
        profile.extra["dns"]?.let { ifaceBuilder.parseDnsServers(it) }
        profile.extra["mtu"]?.toIntOrNull()?.let { ifaceBuilder.setMtu(it) }

        val peerBuilder = Peer.Builder()
            .parsePublicKey(profile.extra["publicKey"] ?: "")
            .parseEndpoint("${profile.server}:${profile.port}")
            .parseAllowedIPs(profile.extra["allowedIps"] ?: "0.0.0.0/0, ::/0")
        secrets.presharedKey?.let { peerBuilder.parsePreSharedKey(it) }

        Config.Builder()
            .setInterface(ifaceBuilder.build())
            .addPeer(peerBuilder.build())
            .build()
    } catch (e: Exception) {
        WeirdLogger.e("WireGuardEngine", "Failed building WireGuard Config", e)
        null
    }

    /**
     * Minimal [Tunnel] identity object; WEIRDNET only ever runs one tunnel at a time.
     *
     * NOTE: `com.wireguard.android:tunnel`'s exact `Tunnel` interface shape has
     * changed across versions (older releases exposed `getName()`/`onStateChange()`
     * as plain methods; newer Kotlin-first releases may expose `name` as a
     * property). If Android Studio flags a mismatch here after Gradle sync,
     * check the installed version's `Tunnel.kt`/`Tunnel.java` source (via
     * "Go to declaration" on the `Tunnel` import) and adjust the two overrides
     * below to match -- the logic itself does not change, only the exact
     * method/property signatures.
     */
    private class SimpleTunnel(private val tunnelName: String) : Tunnel {
        override fun getName(): String = tunnelName
        override fun onStateChange(newState: Tunnel.State) {
            WeirdLogger.d("WireGuardEngine", "Tunnel '$tunnelName' state -> $newState")
        }
    }
}
