package ir.weirdnet.client.core.parser

import ir.weirdnet.client.core.WeirdNetError

/**
 * Single entry point for turning "some text the user gave us" into a [ParseResult].
 * Used identically by manual link paste, QR scan payloads, clipboard import, and
 * (for WireGuard) file import content.
 */
object ConfigParser {

    private val SUPPORTED_SCHEMES = setOf("vless", "vmess", "trojan", "ss")

    fun parse(rawInput: String, suggestedNameForFile: String? = null): ParseResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return ParseResult.Failure(WeirdNetError.InvalidConfiguration("the input is empty"))
        }

        // WireGuard .conf content doesn't have a URI scheme -- detect by structure.
        if (looksLikeWireGuardConf(trimmed)) {
            return WireGuardConfParser.parse(trimmed, suggestedNameForFile)
        }

        val scheme = trimmed.substringBefore("://", missingDelimiterValue = "").lowercase()
        if (scheme.isEmpty()) {
            return ParseResult.Failure(WeirdNetError.InvalidConfiguration("no recognizable link, or the input isn't a supported format"))
        }

        return when (scheme) {
            "vless" -> VlessUriParser.parse(trimmed)
            "vmess" -> VmessUriParser.parse(trimmed)
            "trojan" -> TrojanUriParser.parse(trimmed)
            "ss" -> ShadowsocksUriParser.parse(trimmed)
            else -> ParseResult.Failure(WeirdNetError.UnsupportedProtocol(scheme))
        }
    }

    /** True if [text] contains recognizable `[Interface]`/`[Peer]` INI sections. */
    private fun looksLikeWireGuardConf(text: String): Boolean =
        text.contains("[Interface]", ignoreCase = true) && text.contains("[Peer]", ignoreCase = true)

    /** Used by clipboard-import detection to decide whether to show the "Import" chip at all. */
    fun looksImportable(text: String): Boolean {
        val trimmed = text.trim()
        if (looksLikeWireGuardConf(trimmed)) return true
        val scheme = trimmed.substringBefore("://", missingDelimiterValue = "").lowercase()
        return scheme in SUPPORTED_SCHEMES
    }
}
