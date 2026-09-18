package ir.weirdnet.client.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/**
 * A single diagnostic log line. WeirdLogger (see util/WeirdLogger.kt) is the only
 * writer of this table and it actively redacts anything that looks like a secret
 * before a row is ever created -- see WeirdLogger.redact().
 */
@Entity(tableName = "log_entries")
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampEpochMs: Long,
    val level: LogLevel,
    val tag: String,
    val message: String
)
