package ir.weirdnet.client.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ir.weirdnet.client.util.WeirdLogger

/**
 * WEIRDNET does not auto-start a tunnel on boot by itself (requirement #12's
 * "Auto-connect" is an in-app, explicit opt-in handled by MainActivity on launch,
 * not a background boot hook). This receiver exists solely so that if the user
 * has separately enabled Android's own system-level "Always-on VPN" for WEIRDNET
 * in Settings > Network > VPN, the OS's own Always-on mechanism (which requires
 * RECEIVE_BOOT_COMPLETED to be declared) is not blocked by a missing receiver.
 * It intentionally does nothing else -- no silent background connection.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            WeirdLogger.d("BootCompletedReceiver", "Boot completed observed; no automatic action taken")
        }
    }
}
