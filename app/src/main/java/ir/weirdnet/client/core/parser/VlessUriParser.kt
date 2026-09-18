package ir.weirdnet.client.core.parser

import ir.weirdnet.client.core.WeirdNetError
import ir.weirdnet.client.data.db.SecretCipher
import ir.weirdnet.client.data.model.ProtocolType
import ir.weirdnet.client.data.model.SecurityType
import ir.weirdnet.client.data.model.TransportType
import ir.weirdnet.client.data.model.VpnProfile
import java.net.URI

/**
 * Parses `vless://<uuid>@<host>:<port>?<params>#<remark>` links.
 *
 * Reference format (as used by Xray-core and compatible clients):
 * vless://uuid@server:port?encryption=none&security=reality&sni=example.com
 *         &fp=chrome&pbk=<publicKey>&sid=<shortId>&type=ws&path=%2Fws&host=example.com#My+Server
 */
object VlessUriParser {

    fun parse(uri: String): ParseResult {
        val parsed = try {
            URI(uri)
        } catch (e: Exception) {
            return ParseResult.Failure(WeirdNetError.InvalidConfiguration("could not parse the vless:// link"))
        }

        val uuid = parsed.userInfo
            ?: return ParseResult.Failure(WeirdNetError.InvalidConfiguration("missing UUID before the @ sign"))
        if (!ParseValidation.isValidUuid(uuid)) {
            return ParseResult.Failure(WeirdNetError.InvalidUuid)
        }

        val host = ParseValidation.requireNonBlank(parsed.host)
            ?: return ParseResult.Failure(WeirdNetError.MissingServer)

        if (parsed.port == -1) {
            return ParseResult.Failure(WeirdNetError.InvalidPort("(none provided)"))
        }
        val port = parsed.port

        val params = UriParsingUtils.parseQuery(parsed.rawQuery)
        val transport = TransportType.fromWire(params["type"])
        val security = SecurityType.fromWire(params["security"])

        val extra = buildMap {
            params["sni"]?.let { put("sni", it) }
            params["fp"]?.let { put("fingerprint", it) }
            params["pbk"]?.let { put("realityPublicKey", it) }
            params["sid"]?.let { put("realityShortId", it) }
            params["path"]?.let { put("path", it) }
            params["host"]?.let { put("wsHost", it) }
            params["serviceName"]?.let { put("grpcServiceName", it) }
            params["flow"]?.let { put("flow", it) }
            params["alpn"]?.let { put("alpn", it) }
            put("encryption", params["encryption"] ?: "none")
        }

        val remark = ParseValidation.requireNonBlank(
            parsed.rawFragment?.let { UriParsingUtils.decode(it) }
        ) ?: host

        val profile = VpnProfile(
            name = remark,
            protocol = ProtocolType.VLESS,
            server = host,
            port = port,
            secret = SecretCipher.encrypt(uuid),
            transport = transport,
            security = security,
            extra = extra
        )
        return ParseResult.Success(profile)
    }
}
