package ir.weirdnet.client.data

import ir.weirdnet.client.data.db.ProfileDao
import ir.weirdnet.client.data.model.VpnProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FakeProfileDao : ProfileDao {
    private val state = MutableStateFlow<List<VpnProfile>>(emptyList())

    override fun observeAll(): Flow<List<VpnProfile>> = state

    override suspend fun getById(id: String): VpnProfile? = state.value.firstOrNull { it.id == id }

    override fun observeById(id: String): Flow<VpnProfile?> = state.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun insert(profile: VpnProfile) {
        state.value = state.value.filterNot { it.id == profile.id } + profile
    }

    override suspend fun update(profile: VpnProfile) {
        state.value = state.value.map { if (it.id == profile.id) profile else it }
    }

    override suspend fun delete(profile: VpnProfile) {
        state.value = state.value.filterNot { it.id == profile.id }
    }

    override suspend fun deleteById(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun setFavorite(id: String, favorite: Boolean) {
        state.value = state.value.map { if (it.id == id) it.copy(isFavorite = favorite) else it }
    }

    override suspend fun touchLastConnected(id: String, timestamp: Long) {
        state.value = state.value.map { if (it.id == id) it.copy(lastConnectedEpochMs = timestamp) else it }
    }

    override suspend fun addSessionStats(id: String, down: Long, up: Long) {
        state.value = state.value.map {
            if (it.id == id) it.copy(
                totalBytesDownloaded = it.totalBytesDownloaded + down,
                totalBytesUploaded = it.totalBytesUploaded + up
            ) else it
        }
    }

    override suspend fun updateSortOrder(id: String, order: Int) {
        state.value = state.value.map { if (it.id == id) it.copy(sortOrder = order) else it }
    }

    override suspend fun countWithName(name: String, excludingId: String): Int =
        state.value.count { it.name == name && it.id != excludingId }
}
