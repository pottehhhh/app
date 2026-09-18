package ir.weirdnet.client.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.weirdnet.client.core.ConnectionState
import ir.weirdnet.client.core.VpnStateRepository
import ir.weirdnet.client.data.model.VpnProfile
import ir.weirdnet.client.data.repository.ProfileRepository
import ir.weirdnet.client.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val selectedProfile: VpnProfile? = null,
    val allProfiles: List<VpnProfile> = emptyList(),
    val downloadSpeedBps: Long = 0,
    val uploadSpeedBps: Long = 0,
    val totalDownloaded: Long = 0,
    val totalUploaded: Long = 0
)

class HomeViewModel(
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    // A StateFlow, not a plain var: it has to be one of combine()'s upstream
    // sources for selectProfile() to actually cause a recomposition. A plain
    // mutable field mutated outside the combine() would silently have no visible
    // effect on uiState until one of the *other* three flows happened to emit.
    private val manuallySelectedProfileId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        VpnStateRepository.connectionState,
        VpnStateRepository.traffic,
        profileRepository.observeAll(),
        settingsRepository.settings,
        manuallySelectedProfileId
    ) { state, traffic, profiles, settings, manualId ->
        val currentFromState = (state as? ConnectionState.Connected)?.profile
            ?: (state as? ConnectionState.Connecting)?.profile
            ?: (state as? ConnectionState.Disconnecting)?.profile
        val selected = currentFromState
            ?: profiles.firstOrNull { it.id == manualId }
            ?: profiles.firstOrNull { it.id == settings.defaultProfileId }
            ?: profiles.firstOrNull()

        HomeUiState(
            connectionState = state,
            selectedProfile = selected,
            allProfiles = profiles,
            downloadSpeedBps = traffic.downloadSpeedBps,
            uploadSpeedBps = traffic.uploadSpeedBps,
            totalDownloaded = traffic.totalDownloadedBytes,
            totalUploaded = traffic.totalUploadedBytes
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    /** Called from the Home screen's quick profile-picker; see HomeScreen.kt. */
    fun selectProfile(profile: VpnProfile) {
        manuallySelectedProfileId.value = profile.id
    }
}
