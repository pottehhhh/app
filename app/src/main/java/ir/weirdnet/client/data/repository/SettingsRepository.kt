package ir.weirdnet.client.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ir.weirdnet.client.util.WeirdLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "weirdnet_settings")

enum class AppTheme { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val theme: AppTheme = AppTheme.SYSTEM,
    val autoConnectOnLaunch: Boolean = false,
    val confirmBeforeDisconnect: Boolean = true,
    val defaultProfileId: String? = null,
    val ipv6Enabled: Boolean = true,
    val customDnsEnabled: Boolean = false,
    val customDns: String = "1.1.1.1",
    val debugLoggingEnabled: Boolean = false
)

/**
 * Settings requirement #12: General / Appearance / VPN / Advanced, all persisted
 * locally via Jetpack DataStore (no cloud sync, no backend -- requirement #1).
 */
class SettingsRepository(context: Context) {
    private val dataStore = context.dataStore

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect_on_launch")
        val CONFIRM_DISCONNECT = booleanPreferencesKey("confirm_before_disconnect")
        val DEFAULT_PROFILE_ID = stringPreferencesKey("default_profile_id")
        val IPV6_ENABLED = booleanPreferencesKey("ipv6_enabled")
        val CUSTOM_DNS_ENABLED = booleanPreferencesKey("custom_dns_enabled")
        val CUSTOM_DNS = stringPreferencesKey("custom_dns")
        val DEBUG_LOGGING = booleanPreferencesKey("debug_logging_enabled")
    }

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        val settings = AppSettings(
            theme = prefs[Keys.THEME]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: AppTheme.SYSTEM,
            autoConnectOnLaunch = prefs[Keys.AUTO_CONNECT] ?: false,
            confirmBeforeDisconnect = prefs[Keys.CONFIRM_DISCONNECT] ?: true,
            defaultProfileId = prefs[Keys.DEFAULT_PROFILE_ID],
            ipv6Enabled = prefs[Keys.IPV6_ENABLED] ?: true,
            customDnsEnabled = prefs[Keys.CUSTOM_DNS_ENABLED] ?: false,
            customDns = prefs[Keys.CUSTOM_DNS] ?: "1.1.1.1",
            debugLoggingEnabled = prefs[Keys.DEBUG_LOGGING] ?: false
        )
        WeirdLogger.debugLoggingEnabled = settings.debugLoggingEnabled
        settings
    }

    suspend fun setTheme(theme: AppTheme) = dataStore.edit { it[Keys.THEME] = theme.name }
    suspend fun setAutoConnect(enabled: Boolean) = dataStore.edit { it[Keys.AUTO_CONNECT] = enabled }
    suspend fun setConfirmBeforeDisconnect(enabled: Boolean) = dataStore.edit { it[Keys.CONFIRM_DISCONNECT] = enabled }
    suspend fun setDefaultProfile(id: String?) = dataStore.edit {
        if (id == null) it.remove(Keys.DEFAULT_PROFILE_ID) else it[Keys.DEFAULT_PROFILE_ID] = id
    }
    suspend fun setIpv6Enabled(enabled: Boolean) = dataStore.edit { it[Keys.IPV6_ENABLED] = enabled }
    suspend fun setCustomDnsEnabled(enabled: Boolean) = dataStore.edit { it[Keys.CUSTOM_DNS_ENABLED] = enabled }
    suspend fun setCustomDns(dns: String) = dataStore.edit { it[Keys.CUSTOM_DNS] = dns }
    suspend fun setDebugLogging(enabled: Boolean) = dataStore.edit { it[Keys.DEBUG_LOGGING] = enabled }

    suspend fun resetAll() = dataStore.edit { it.clear() }
}
