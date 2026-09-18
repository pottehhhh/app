package ir.weirdnet.client.util

import android.util.Log
import ir.weirdnet.client.data.db.AppDatabase
import ir.weirdnet.client.data.model.LogEntry
import ir.weirdnet.client.data.model.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The single logging entry point for the whole app (requirement #14).
 *
 * - Debug-level logs are only persisted when [debugLoggingEnabled] is true (wired to
 *   the Settings screen's "Enable debug logging" toggle).
 * - INFO/WARN/ERROR are always persisted so diagnostics remain useful by default.
 * - Every message is passed through [redact] before it touches Logcat or the
 *   database, so UUIDs, keys, and passwords never end up in a bug report.
 * - [LogDao.trimTo] is invoked after every write to keep on-disk log size bounded.
 */
object WeirdLogger {
    private const val MAX_STORED_LOGS = 2000
    private const val TAG_PREFIX = "WeirdNet"

    @Volatile var debugLoggingEnabled: Boolean = false

    private lateinit var database: AppDatabase
    private val scope = CoroutineScope(Dispatchers.IO)

    fun init(database: AppDatabase) {
        this.database = database
    }

    fun d(tag: String, message: String) {
        if (!debugLoggingEnabled) return
        Log.d("$TAG_PREFIX/$tag", redact(message))
        persist(LogLevel.DEBUG, tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i("$TAG_PREFIX/$tag", redact(message))
        persist(LogLevel.INFO, tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w("$TAG_PREFIX/$tag", redact(message))
        persist(LogLevel.WARN, tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e("$TAG_PREFIX/$tag", redact(message), throwable)
        persist(LogLevel.ERROR, tag, message + (throwable?.let { " :: ${it.javaClass.simpleName}: ${it.message}" } ?: ""))
    }

    private fun persist(level: LogLevel, tag: String, rawMessage: String) {
        if (!::database.isInitialized) return
        val message = redact(rawMessage)
        scope.launch {
            database.logDao().insert(
                LogEntry(timestampEpochMs = System.currentTimeMillis(), level = level, tag = tag, message = message)
            )
            database.logDao().trimTo(MAX_STORED_LOGS)
        }
    }

    /**
     * Strips anything resembling a secret out of a log line before it's written
     * anywhere. This is intentionally aggressive: false positives (over-redacting)
     * are an acceptable trade-off for a VPN client's diagnostic logs.
     */
    fun redact(message: String): String {
        var result = message
        // UUIDs (used as VLESS/VMess IDs)
        result = result.replace(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"), "«uuid-redacted»")
        // WireGuard-style base64 keys (44 chars ending in '=')
        result = result.replace(Regex("[A-Za-z0-9+/]{42,44}="), "«key-redacted»")
        // password=..., pwd=..., pass=..., secret=..., key=... query-style params
        result = result.replace(Regex("(?i)(password|pwd|pass|secret|key|token)=([^&\\s]+)")) { m -> "${m.groupValues[1]}=«redacted»" }
        // userinfo before @ in URIs (vless://uuid@host, trojan://pass@host)
        result = result.replace(Regex("://[^@/\\s]+@")) { "://«redacted»@" }
        return result
    }
}
