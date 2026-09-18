package ir.weirdnet.client.core.parser

import ir.weirdnet.client.core.WeirdNetError
import ir.weirdnet.client.data.db.SecretCipher
import ir.weirdnet.client.data.model.ProtocolType
import ir.weirdnet.client.data.model.TransportType
import ir.weirdnet.client.data.model.VpnProfile
import java.net.URI

/**
 * Parses `ss://` links in both formats used in the wild:
 *  - SIP002: ss://base64(method:password)@host:port#remark
 *  - Legacy: ss://base64(method:password@host:port)#remark
 */
object ShadowsocksUriParser {

    fun parse(uri: String): ParseResult {
        // Legacy form has no literal '@' outside of the base64 blob, and the whole
        // authority section is base64. Try SIP002 first (has a real URI structure).
        val sip002 = tryParseSip002(uri)
        if (sip002 != null) return sip002
        return tryParseLegacy(uri) ?: ParseResult.Failure(
            WeirdNetError.InvalidConfiguration("the ss:// link is not in a recognized Shadowsocks format")
        )
    }

    private fun tryParseSip002(uri: String): ParseResult? {
        val parsed = try {
            URI(uri)
        } catch (e: Exception) {
            return null
        }
        val userInfoRaw = parsed.rawUserInfo ?: return null
        val decodedUserInfo = UriParsingUtils.decodeBase64(userInfoRaw) ?: userInfoRaw
        val methodAndPassword = decodedUserInfo.split(":", limit = 2)
        if (methodAndPassword.size != 2) return null
        val (method, password) = methodAndPassword

        val host = ParseValidation.requireNonBlank(parsed.host) ?: return ParseResult.Failure(WeirdNetError.MissingServer)
        if (parsed.port == -1) return ParseResult.Failure(WeirdNetError.InvalidPort("(none provided)"))

        val remark = ParseValidation.requireNonBlank(
            parsed.rawFragment?.let { UriParsingUtils.decode(it) }
        ) ?: host

        return ParseResult.Success(
            VpnProfile(
                name = remark,
                protocol = ProtocolType.SHADOWSOCKS,
                server = host,
                port = parsed.port,
                secret = SecretCipher.encrypt(password),
                transport = TransportType.TCP,
                extra = mapOf("cipher" to method)
            )
        )
    }

    private fun tryParseLegacy(uri: String): ParseResult? {
        val body = uri.removePrefix("ss://").substringBefore("#")
        val decoded = UriParsingUtils.decodeBase64(body) ?: return null
        // method:password@host:port
        val atIndex = decoded.lastIndexOf('@')
        if (atIndex == -1) return null
        val methodPassword = decoded.substring(0, atIndex)
        val hostPort = decoded.substring(atIndex + 1)

        val methodAndPassword = methodPassword.split(":", limit = 2)
        if (methodAndPassword.size != 2) return null
        val (method, password) = methodAndPassword

        val hostPortParts = hostPort.split(":")
        if (hostPortParts.size != 2) return null
        val host = ParseValidation.requireNonBlank(hostPortParts[0])
            ?: return ParseResult.Failure(WeirdNetError.MissingServer)
        val port = ParseValidation.parsePortOrNull(hostPortParts[1])
            ?: return ParseResult.Failure(WeirdNetError.InvalidPort(hostPortParts[1]))

        val fragment = uri.substringAfter("#", "")
        val remark = ParseValidation.requireNonBlank(
            fragment.takeIf { it.isNotBlank() }?.let { UriParsingUtils.decode(it) }
        ) ?: host

        return ParseResult.Success(
            VpnProfile(
                name = remark,
                protocol = ProtocolType.SHADOWSOCKS,
                server = host,
                port = port,
                secret = SecretCipher.encrypt(password),
                transport = TransportType.TCP,
                extra = mapOf("cipher" to method)
            )
        )
    }
}
