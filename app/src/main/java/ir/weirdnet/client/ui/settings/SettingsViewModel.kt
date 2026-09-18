package ir.weirdnet.client.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.weirdnet.client.data.model.VpnProfile
import ir.weirdnet.client.data.repository.AppSettings
import ir.weirdnet.client.data.repository.AppTheme
import ir.weirdnet.client.data.repository.ProfileRepository
import ir.weirdnet.client.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val profiles: List<VpnProfile> = emptyList()
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    profileRepository: ProfileRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings, profileRepository.observeAll()
    ) { settings, profiles -> SettingsUiState(settings, profiles) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setTheme(theme: AppTheme) = viewModelScope.launch { settingsRepository.setTheme(theme) }
    fun setAutoConnect(enabled: Boolean) = viewModelScope.launch { settingsRepository.setAutoConnect(enabled) }
    fun setConfirmBeforeDisconnect(enabled: Boolean) = viewModelScope.launch { settingsRepository.setConfirmBeforeDisconnect(enabled) }
    fun setDefaultProfile(id: String?) = viewModelScope.launch { settingsRepository.setDefaultProfile(id) }
    fun setIpv6Enabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setIpv6Enabled(enabled) }
    fun setCustomDnsEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setCustomDnsEnabled(enabled) }
    fun setCustomDns(dns: String) = viewModelScope.launch { settingsRepository.setCustomDns(dns) }
    fun setDebugLogging(enabled: Boolean) = viewModelScope.launch { settingsRepository.setDebugLogging(enabled) }
    fun resetAll() = viewModelScope.launch { settingsRepository.resetAll() }
}
