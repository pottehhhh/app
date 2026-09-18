package ir.weirdnet.client.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridges an incoming deep-link (a tapped `vless://`/`vmess://`/`trojan://`/`ss://`
 * link, or a `.conf` file opened from another app) into the Compose navigation
 * graph. [ir.weirdnet.client.MainActivity] is the only writer (via [offer]);
 * the Add Profile screen is the only reader (via [consume]), so a payload is
 * only ever acted on once even if the composition recreates.
 */
object DeepLinkRepository {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun offer(payload: String) {
        _pending.value = payload
    }

    /** Reads and clears the pending payload in one step, so it's only consumed once. */
    fun consume(): String? {
        val value = _pending.value
        _pending.value = null
        return value
    }
}
