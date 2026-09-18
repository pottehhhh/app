package ir.weirdnet.client.data.repository

import ir.weirdnet.client.data.db.ProfileDao
import ir.weirdnet.client.data.model.VpnProfile
import kotlinx.coroutines.flow.Flow

class ProfileRepository(private val dao: ProfileDao) {

    fun observeAll(): Flow<List<VpnProfile>> = dao.observeAll()

    fun observeById(id: String): Flow<VpnProfile?> = dao.observeById(id)

    suspend fun getById(id: String): VpnProfile? = dao.getById(id)

    /** Returns true if [name] is already used by a different profile (used for the rename/duplicate UX). */
    suspend fun isNameTaken(name: String, excludingId: String = ""): Boolean =
        dao.countWithName(name, excludingId) > 0

    suspend fun save(profile: VpnProfile) = dao.insert(profile)

    suspend fun update(profile: VpnProfile) = dao.update(profile)

    suspend fun delete(profile: VpnProfile) = dao.delete(profile)

    suspend fun setFavorite(id: String, favorite: Boolean) = dao.setFavorite(id, favorite)

    suspend fun touchLastConnected(id: String, timestamp: Long) = dao.touchLastConnected(id, timestamp)

    suspend fun addSessionStats(id: String, down: Long, up: Long) = dao.addSessionStats(id, down, up)

    suspend fun reorder(id: String, order: Int) = dao.updateSortOrder(id, order)

    suspend fun duplicate(profile: VpnProfile): VpnProfile {
        var candidateName = "${profile.name} (copy)"
        var suffix = 2
        while (isNameTaken(candidateName)) {
            candidateName = "${profile.name} (copy $suffix)"
            suffix++
        }
        val copy = profile.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = candidateName,
            createdAtEpochMs = System.currentTimeMillis(),
            lastConnectedEpochMs = null,
            totalBytesDownloaded = 0L,
            totalBytesUploaded = 0L
        )
        save(copy)
        return copy
    }
}
