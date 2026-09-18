package ir.weirdnet.client.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.weirdnet.client.data.model.VpnProfile
import ir.weirdnet.client.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ProfileFilter { ALL, FAVORITES, WIREGUARD, XRAY }

data class ProfilesUiState(
    val profiles: List<VpnProfile> = emptyList(),
    val searchQuery: String = "",
    val filter: ProfileFilter = ProfileFilter.ALL
) {
    /** Reordering only makes sense against the full, unfiltered, unsearched list. */
    val canReorder: Boolean get() = filter == ProfileFilter.ALL && searchQuery.isBlank()
}

class ProfilesViewModel(private val repository: ProfileRepository) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val filter = MutableStateFlow(ProfileFilter.ALL)

    val uiState: StateFlow<ProfilesUiState> = combine(
        repository.observeAll(), searchQuery, filter
    ) { profiles, query, currentFilter ->
        val filtered = profiles
            .filter { profile ->
                when (currentFilter) {
                    ProfileFilter.ALL -> true
                    ProfileFilter.FAVORITES -> profile.isFavorite
                    ProfileFilter.WIREGUARD -> profile.protocol.name == "WIREGUARD"
                    ProfileFilter.XRAY -> profile.protocol.name != "WIREGUARD"
                }
            }
            .filter { profile ->
                query.isBlank() ||
                    profile.name.contains(query, ignoreCase = true) ||
                    profile.server.contains(query, ignoreCase = true)
            }
        ProfilesUiState(profiles = filtered, searchQuery = query, filter = currentFilter)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfilesUiState())

    fun setSearchQuery(query: String) { searchQuery.value = query }
    fun setFilter(newFilter: ProfileFilter) { filter.value = newFilter }

    fun toggleFavorite(profile: VpnProfile) = viewModelScope.launch {
        repository.setFavorite(profile.id, !profile.isFavorite)
    }

    fun rename(profile: VpnProfile, newName: String) = viewModelScope.launch {
        repository.update(profile.copy(name = newName))
    }

    fun duplicate(profile: VpnProfile) = viewModelScope.launch {
        repository.duplicate(profile)
    }

    fun delete(profile: VpnProfile) = viewModelScope.launch {
        repository.delete(profile)
    }

    /**
     * Moves [profile] one position up ([direction] = -1) or down ([direction] =
     * +1) in the currently visible (unfiltered, unsearched -- see
     * [ProfilesUiState.canReorder]) list, then re-numbers every profile in that
     * list sequentially and persists it. Newly imported profiles all start with
     * the same default `sortOrder` (0), so the first reorder also doubles as a
     * one-time normalization pass -- re-deriving a coherent order from whatever
     * was currently displayed rather than assuming a prior order already existed.
     */
    fun moveProfile(profile: VpnProfile, direction: Int) = viewModelScope.launch {
        val currentList = uiState.value.profiles
        val fromIndex = currentList.indexOfFirst { it.id == profile.id }
        val toIndex = fromIndex + direction
        if (fromIndex == -1 || toIndex !in currentList.indices) return@launch

        val reordered = currentList.toMutableList()
        val moved = reordered.removeAt(fromIndex)
        reordered.add(toIndex, moved)
        reordered.forEachIndexed { index, p -> repository.reorder(p.id, index) }
    }
}
