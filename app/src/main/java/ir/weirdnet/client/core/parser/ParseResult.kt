package ir.weirdnet.client.core.parser

import ir.weirdnet.client.core.WeirdNetError
import ir.weirdnet.client.data.model.VpnProfile

sealed class ParseResult {
    data class Success(val profile: VpnProfile) : ParseResult()
    data class Failure(val error: WeirdNetError) : ParseResult()
}

/** Shared validation helpers used by every protocol parser. */
object ParseValidation {
    private val UUID_REGEX =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    fun isValidUuid(value: String): Boolean = UUID_REGEX.matches(value)

    fun parsePortOrNull(raw: String?): Int? {
        val port = raw?.toIntOrNull() ?: return null
        return if (port in 1..65535) port else null
    }

    fun requireNonBlank(value: String?): String? = value?.takeIf { it.isNotBlank() }
}
