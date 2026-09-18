package ir.weirdnet.client.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ir.weirdnet.client.data.model.LogEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Query("SELECT * FROM log_entries ORDER BY timestampEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 500): Flow<List<LogEntry>>

    @Insert
    suspend fun insert(entry: LogEntry)

    @Query("DELETE FROM log_entries")
    suspend fun clear()

    /** Keeps the log table bounded so it never grows without limit (requirement #14). */
    @Query(
        "DELETE FROM log_entries WHERE id NOT IN " +
            "(SELECT id FROM log_entries ORDER BY timestampEpochMs DESC LIMIT :keep)"
    )
    suspend fun trimTo(keep: Int)
}
