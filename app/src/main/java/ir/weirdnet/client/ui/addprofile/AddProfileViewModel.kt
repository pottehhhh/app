package ir.weirdnet.client.ui.addprofile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.weirdnet.client.core.WeirdNetError
import ir.weirdnet.client.core.parser.ConfigParser
import ir.weirdnet.client.core.parser.ParseResult
import ir.weirdnet.client.data.model.VpnProfile
import ir.weirdnet.client.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AddProfileUiState {
    data object Idle : AddProfileUiState()
    data class Preview(val profile: VpnProfile) : AddProfileUiState()
    data class Error(val error: WeirdNetError) : AddProfileUiState()
    data object Saved : AddProfileUiState()
}

class AddProfileViewModel(private val repository: ProfileRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<AddProfileUiState>(AddProfileUiState.Idle)
    val uiState: StateFlow<AddProfileUiState> = _uiState.asStateFlow()

    /** Used by every import path: pasted link, QR payload, or raw file content. */
    fun parseInput(rawInput: String, suggestedNameForFile: String? = null) {
        when (val result = ConfigParser.parse(rawInput, suggestedNameForFile)) {
            is ParseResult.Success -> _uiState.value = AddProfileUiState.Preview(result.profile)
            is ParseResult.Failure -> _uiState.value = AddProfileUiState.Error(result.error)
        }
    }

    fun updatePreviewName(newName: String) {
        val current = _uiState.value
        if (current is AddProfileUiState.Preview) {
            _uiState.value = current.copy(profile = current.profile.copy(name = newName))
        }
    }

    fun confirmSave() {
        val current = _uiState.value
        if (current !is AddProfileUiState.Preview) return
        viewModelScope.launch {
            repository.save(current.profile)
            _uiState.value = AddProfileUiState.Saved
        }
    }

    /** Used when a file picked via the file importer couldn't be read at all (permission revoked, provider error, etc). */
    fun reportFileReadError() {
        _uiState.value = AddProfileUiState.Error(
            WeirdNetError.InvalidConfiguration("the selected file could not be read")
        )
    }

    fun reset() {
        _uiState.value = AddProfileUiState.Idle
    }
}
