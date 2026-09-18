package ir.weirdnet.client.core.parser

import android.util.Base64
import java.net.URLDecoder

internal object UriParsingUtils {

    fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split("&")
            .mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx == -1) null
                else {
                    val key = decode(pair.substring(0, idx))
                    val value = decode(pair.substring(idx + 1))
                    key to value
                }
            }
            .toMap()
    }

    fun decode(value: String): String = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (e: Exception) {
        value
    }

    fun decodeBase64(value: String): String? = try {
        val normalized = value.replace('-', '+').replace('_', '/')
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }
}
