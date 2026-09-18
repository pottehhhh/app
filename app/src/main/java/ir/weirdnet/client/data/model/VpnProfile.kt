package ir.weirdnet.client.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A single saved VPN configuration, protocol-agnostic at the storage layer.
 *
 * Secrets (private keys, passwords, UUIDs, pre-shared keys) live in [secret] only.
 * [secret] is encrypted at rest -- see [ir.weirdnet.client.data.db.SecretCipher] and
 * [ir.weirdnet.client.data.db.Converters]. Nothing in [extra] should ever contain a
 * credential; it's for non-sensitive transport metadata only (e.g. a WebSocket path,
 * a SNI hostname, an ALPN list).
 */
@Entity(tableName = "vpn_profiles")
data class VpnProfile(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocol: ProtocolType,
    val server: String,
    val port: Int,

    /** Encrypted blob: UUID/password/private-key/PSK depending on [protocol]. Never logged. */
    val secret: String,

    val transport: TransportType = TransportType.TCP,
    val security: SecurityType = SecurityType.NONE,

    /** Non-secret transport metadata as key/value pairs (SNI, ws path, service name, etc). */
    val extra: Map<String, String> = emptyMap(),

    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val lastConnectedEpochMs: Long? = null,
    val isFavorite: Boolean = false,
    val sortOrder: Int = 0,

    // Lifetime, not-currently-connected statistics. Live session stats while
    // connected are tracked separately by TrafficMonitor and not persisted here
    // until the session ends, to avoid excessive DB writes.
    val totalBytesDownloaded: Long = 0L,
    val totalBytesUploaded: Long = 0L
) {
    /** Server:port formatted for display, never includes [secret]. */
    fun displayAddress(): String = "$server:$port"
}
