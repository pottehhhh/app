package ir.weirdnet.client.ui.diagnostics

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.weirdnet.client.BuildConfig
import ir.weirdnet.client.core.ConnectionState
import ir.weirdnet.client.core.VpnStateRepository
import ir.weirdnet.client.data.db.LogDao
import ir.weirdnet.client.data.model.LogEntry
import ir.weirdnet.client.data.repository.ProfileRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DiagnosticsUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val logs: List<LogEntry> = emptyList()
)

class DiagnosticsViewModel(
    private val logDao: LogDao,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    val uiState: StateFlow<DiagnosticsUiState> = combine(
        VpnStateRepository.connectionState, logDao.observeRecent(200)
    ) { state, logs -> DiagnosticsUiState(state, logs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DiagnosticsUiState())

    fun clearLogs() = viewModelScope.launch { logDao.clear() }

    /**
     * Builds a plain-text diagnostics report safe to paste into a support
     * request. Never includes profile secrets -- only what's already visible
     * elsewhere in the UI (name, protocol, server, port) plus system info.
     */
    fun buildDiagnosticsReport(state: DiagnosticsUiState): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.appendLine("WEIRDNET Diagnostics Report")
        sb.appendLine("Generated: ${sdf.format(Date())}")
        sb.appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        sb.appendLine("Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine()

        val profile = when (val cs = state.connectionState) {
            is ConnectionState.Connected -> cs.profile
            is ConnectionState.Connecting -> cs.profile
            is ConnectionState.Disconnecting -> cs.profile
            is ConnectionState.Error -> cs.profile
            else -> null
        }
        sb.appendLine("Connection state: ${state.connectionState::class.simpleName}")
        if (profile != null) {
            sb.appendLine("Profile: ${profile.name}")
            sb.appendLine("Protocol: ${profile.protocol.displayName}")
            sb.appendLine("Server: ${profile.server}")
            sb.appendLine("Port: ${profile.port}")
            sb.appendLine("Transport: ${profile.transport.label}")
            sb.appendLine("Security: ${profile.security.name}")
        }
        if (state.connectionState is ConnectionState.Error) {
            sb.appendLine("Last error: ${(state.connectionState as ConnectionState.Error).error.message}")
        }
        sb.appendLine()
        sb.appendLine("--- Recent log lines (secrets already redacted) ---")
        state.logs.take(100).forEach { entry ->
            sb.appendLine("[${sdf.format(Date(entry.timestampEpochMs))}] ${entry.level} ${entry.tag}: ${entry.message}")
        }
        return sb.toString()
    }
}
