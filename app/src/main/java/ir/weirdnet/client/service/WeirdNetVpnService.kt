package ir.weirdnet.client.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.wireguard.android.backend.GoBackend
import ir.weirdnet.client.MainActivity
import ir.weirdnet.client.R
import ir.weirdnet.client.WeirdNetApplication
import ir.weirdnet.client.core.VpnConnectionManager
import ir.weirdnet.client.core.VpnStateRepository
import ir.weirdnet.client.util.WeirdLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * WEIRDNET's single VPN tunnel service (requirement #10).
 *
 * Extends [GoBackend.VpnService] rather than the bare Android [android.net.VpnService]
 * for a specific, deliberate reason: the official WireGuard-for-Android library
 * (`com.wireguard.android:tunnel`) is architected so its [GoBackend] manages the TUN
 * interface through a service that IS-A `GoBackend.VpnService`. Since
 * `GoBackend.VpnService` is itself a thin subclass of `android.net.VpnService`
 * (it only adds bookkeeping for `onRevoke`/process death), this class is fully
 * usable as a normal `VpnService` too -- which is what [ir.weirdnet.client.core.xray.XrayEngine]
 * will do once the native Xray-core dependency is added (see docs/XRAY_INTEGRATION.md):
 * it will call `Builder()`/`establish()` directly on this same instance.
 *
 * Only one [VpnConnectionManager] session runs at a time; see that class for the
 * mutex that guarantees it.
 */
class WeirdNetVpnService : GoBackend.VpnService() {

    companion object {
        const val ACTION_CONNECT = "ir.weirdnet.client.action.CONNECT"
        const val ACTION_DISCONNECT = "ir.weirdnet.client.action.DISCONNECT"
        const val EXTRA_PROFILE_ID = "profile_id"

        private const val NOTIFICATION_CHANNEL_ID = "weirdnet_vpn_status"
        private const val NOTIFICATION_ID = 1001
    }

    // SupervisorJob, not a plain Job: if one launched coroutine throws (e.g. a
    // connect attempt fails unexpectedly), it must not cancel this whole scope --
    // otherwise a later disconnect request, or the TrafficMonitor coroutine
    // VpnConnectionManager starts on this same scope, would silently stop working
    // for the rest of the service's lifetime.
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob)
    private lateinit var connectionManager: VpnConnectionManager

    override fun onCreate() {
        super.onCreate()
        val app = WeirdNetApplication.from(this)
        connectionManager = VpnConnectionManager(
            vpnService = this,
            profileRepository = app.profileRepository,
            settingsRepository = app.settingsRepository,
            scope = serviceScope
        )
        createNotificationChannel()
        WeirdLogger.i("WeirdNetVpnService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                // startForeground() must be called synchronously, before any
                // suspending work, whenever the service may have been started via
                // startForegroundService() -- Android 12+ kills the service with
                // ForegroundServiceDidNotStartInTimeException if this doesn't
                // happen within ~5 seconds. It's called unconditionally here, even
                // if the profile ID extra turns out to be missing/invalid, and the
                // invalid case is then cleaned up inside the coroutine below.
                startForegroundCompat(buildNotification(connecting = true))
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
                serviceScope.launch {
                    if (profileId == null) {
                        WeirdLogger.e("WeirdNetVpnService", "ACTION_CONNECT received without a profile id")
                        stopSelfCleanly()
                    } else {
                        handleConnect(profileId)
                    }
                }
            }
            ACTION_DISCONNECT -> {
                serviceScope.launch { handleDisconnect() }
            }
            else -> {
                // Covers a null intent/action, which the platform can deliver if
                // this service process is restarted by the system (e.g. after Android
                // enables "Always-on VPN" for WEIRDNET and later relaunches the
                // process). WEIRDNET does not currently auto-reconnect to a
                // remembered profile in that case -- see docs/TROUBLESHOOTING.md --
                // but it must not crash or leave a dangling foreground-service
                // start, so it simply stops itself cleanly.
                if (intent != null) {
                    WeirdLogger.w("WeirdNetVpnService", "Unrecognized action: ${intent.action}")
                }
                stopSelfCleanly()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun handleConnect(profileId: String) {
        val app = WeirdNetApplication.from(this)
        val profile = app.profileRepository.getById(profileId)
        if (profile == null) {
            WeirdLogger.e("WeirdNetVpnService", "Profile $profileId not found")
            stopSelfCleanly()
            return
        }
        val success = connectionManager.connect(profile)
        if (success) {
            updateNotification(connecting = false)
        } else {
            // Engine reported failure (already reflected in VpnStateRepository as
            // ConnectionState.Error by VpnConnectionManager); stop the foreground
            // service since there's no live tunnel to keep it alive for.
            stopSelfCleanly()
        }
    }

    private suspend fun handleDisconnect() {
        connectionManager.disconnect()
        stopSelfCleanly()
    }

    private fun stopSelfCleanly() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Called by Android when the user revokes VPN permission from system settings. */
    override fun onRevoke() {
        WeirdLogger.w("WeirdNetVpnService", "VPN permission revoked by system")
        serviceScope.launch {
            connectionManager.disconnect()
            stopSelfCleanly()
        }
        super.onRevoke()
    }

    override fun onDestroy() {
        // Best-effort synchronous teardown: if the service is being destroyed
        // without having gone through handleDisconnect() first (e.g. the system
        // reclaims the process, or an unexpected exception unwound the service),
        // this still tears down the live engine rather than leaking a tunnel that
        // the rest of the app now has no way to reach or stop.
        if (::connectionManager.isInitialized) {
            runBlocking { connectionManager.disconnect() }
        }
        VpnStateRepository.reset()
        serviceJob.cancel()
        super.onDestroy()
        WeirdLogger.i("WeirdNetVpnService", "Service destroyed")
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.vpn_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(connecting: Boolean): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val disconnectIntent = PendingIntent.getService(
            this, 0,
            Intent(this, WeirdNetVpnService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (connecting) {
            getString(R.string.vpn_notification_title_connecting)
        } else {
            getString(R.string.vpn_notification_title_connected)
        }
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_shield)
            .setContentTitle(title)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.vpn_notification_disconnect_action), disconnectIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(connecting: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(connecting))
    }

    /**
     * Wraps [ServiceCompat.startForeground] so the "specialUse" foreground-service
     * type declared in the manifest is also asserted explicitly at the call site --
     * belt-and-braces against Android 14's stricter foreground-service-type
     * enforcement, rather than relying solely on the manifest declaration.
     *
     * Gated on API 34 (UPSIDE_DOWN_CAKE) specifically, not just "API 29+ supports
     * the 4-arg startForeground overload": FOREGROUND_SERVICE_TYPE_SPECIAL_USE
     * itself was only introduced in API 34. On API 29-33 there is no equivalent
     * "special use" type to declare, so 0 (no explicit type) is passed and the
     * manifest's own foregroundServiceType is what applies.
     */
    private fun startForegroundCompat(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }
}

