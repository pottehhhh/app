package ir.weirdnet.client.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import ir.weirdnet.client.data.model.VpnProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM vpn_profiles ORDER BY sortOrder ASC, createdAtEpochMs DESC")
    fun observeAll(): Flow<List<VpnProfile>>

    @Query("SELECT * FROM vpn_profiles WHERE id = :id")
    suspend fun getById(id: String): VpnProfile?

    @Query("SELECT * FROM vpn_profiles WHERE id = :id")
    fun observeById(id: String): Flow<VpnProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: VpnProfile)

    @Update
    suspend fun update(profile: VpnProfile)

    @Delete
    suspend fun delete(profile: VpnProfile)

    @Query("DELETE FROM vpn_profiles WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE vpn_profiles SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE vpn_profiles SET lastConnectedEpochMs = :timestamp WHERE id = :id")
    suspend fun touchLastConnected(id: String, timestamp: Long)

    @Query(
        "UPDATE vpn_profiles SET totalBytesDownloaded = totalBytesDownloaded + :down, " +
            "totalBytesUploaded = totalBytesUploaded + :up WHERE id = :id"
    )
    suspend fun addSessionStats(id: String, down: Long, up: Long)

    @Query("UPDATE vpn_profiles SET sortOrder = :order WHERE id = :id")
    suspend fun updateSortOrder(id: String, order: Int)

    @Query("SELECT COUNT(*) FROM vpn_profiles WHERE name = :name AND id != :excludingId")
    suspend fun countWithName(name: String, excludingId: String = ""): Int
}
