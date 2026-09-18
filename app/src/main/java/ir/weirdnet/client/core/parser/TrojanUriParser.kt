package ir.weirdnet.client.core.parser

import ir.weirdnet.client.core.WeirdNetError
import ir.weirdnet.client.data.db.SecretCipher
import ir.weirdnet.client.data.model.ProtocolType
import ir.weirdnet.client.data.model.SecurityType
import ir.weirdnet.client.data.model.TransportType
import ir.weirdnet.client.data.model.VpnProfile
import java.net.URI

/**
 * Parses `trojan://<password>@<host>:<port>?<params>#<remark>` links.
 * Trojan always runs over TLS by design, so [SecurityType.TLS] is the default
 * unless the link explicitly says otherwise.
 */
object TrojanUriParser {

    fun parse(uri: String): ParseResult {
        val parsed = try {
            URI(uri)
        } catch (e: Exception) {
            return ParseResult.Failure(WeirdNetError.InvalidConfiguration("could not parse the trojan:// link"))
        }

        val password = parsed.userInfo
            ?: return ParseResult.Failure(WeirdNetError.InvalidConfiguration("missing password before the @ sign"))

        val host = ParseValidation.requireNonBlank(parsed.host)
            ?: return ParseResult.Failure(WeirdNetError.MissingServer)

        if (parsed.port == -1) {
            return ParseResult.Failure(WeirdNetError.InvalidPort("(none provided)"))
        }

        val params = UriParsingUtils.parseQuery(parsed.rawQuery)
        val transport = TransportType.fromWire(params["type"])
        val security = SecurityType.fromWire(params["security"] ?: "tls")

        val extra = buildMap {
            params["sni"]?.let { put("sni", it) }
            params["fp"]?.let { put("fingerprint", it) }
            params["path"]?.let { put("path", it) }
            params["host"]?.let { put("wsHost", it) }
            params["alpn"]?.let { put("alpn", it) }
        }

        val remark = ParseValidation.requireNonBlank(
            parsed.rawFragment?.let { UriParsingUtils.decode(it) }
        ) ?: host

        val profile = VpnProfile(
            name = remark,
            protocol = ProtocolType.TROJAN,
            server = host,
            port = parsed.port,
            secret = SecretCipher.encrypt(password),
            transport = transport,
            security = security,
            extra = extra
        )
        return ParseResult.Success(profile)
    }
}
