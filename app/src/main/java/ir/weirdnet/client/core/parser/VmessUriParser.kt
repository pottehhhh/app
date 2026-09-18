package ir.weirdnet.client.core.parser

import ir.weirdnet.client.core.WeirdNetError
import ir.weirdnet.client.data.db.SecretCipher
import ir.weirdnet.client.data.model.ProtocolType
import ir.weirdnet.client.data.model.SecurityType
import ir.weirdnet.client.data.model.TransportType
import ir.weirdnet.client.data.model.VpnProfile
import org.json.JSONException
import org.json.JSONObject

/**
 * Parses `vmess://<base64 JSON>` links -- the de-facto standard format shared by
 * V2Ray/Xray-compatible clients. Expected JSON keys (all optional except noted):
 * v, ps (remark), add* (host), port* (port), id* (uuid), aid, net (transport),
 * type, host, path, tls, sni.
 * (* = required)
 */
object VmessUriParser {

    fun parse(uri: String): ParseResult {
        val payload = uri.removePrefix("vmess://")
        val json = UriParsingUtils.decodeBase64(payload)
            ?: return ParseResult.Failure(WeirdNetError.InvalidConfiguration("the vmess:// payload is not valid base64"))

        val obj = try {
            JSONObject(json)
        } catch (e: JSONException) {
            return ParseResult.Failure(WeirdNetError.InvalidConfiguration("the vmess payload is not valid JSON"))
        }

        val host = ParseValidation.requireNonBlank(obj.optString("add").ifBlank { null })
            ?: return ParseResult.Failure(WeirdNetError.MissingServer)

        val port = ParseValidation.parsePortOrNull(obj.optString("port"))
            ?: return ParseResult.Failure(WeirdNetError.InvalidPort(obj.optString("port")))

        val uuid = obj.optString("id").ifBlank { null }
            ?: return ParseResult.Failure(WeirdNetError.InvalidConfiguration("missing \"id\" (UUID) field"))
        if (!ParseValidation.isValidUuid(uuid)) {
            return ParseResult.Failure(WeirdNetError.InvalidUuid)
        }

        val transport = TransportType.fromWire(obj.optString("net").ifBlank { "tcp" })
        val tlsValue = obj.optString("tls")
        val security = if (tlsValue.equals("tls", ignoreCase = true)) SecurityType.TLS else SecurityType.NONE

        val extra = buildMap {
            obj.optString("sni").takeIf { it.isNotBlank() }?.let { put("sni", it) }
            obj.optString("host").takeIf { it.isNotBlank() }?.let { put("wsHost", it) }
            obj.optString("path").takeIf { it.isNotBlank() }?.let { put("path", it) }
            obj.optString("type").takeIf { it.isNotBlank() }?.let { put("headerType", it) }
            obj.optString("aid").takeIf { it.isNotBlank() }?.let { put("alterId", it) }
            obj.optString("scy").takeIf { it.isNotBlank() }?.let { put("cipher", it) }
        }

        val remark = ParseValidation.requireNonBlank(obj.optString("ps").ifBlank { null }) ?: host

        val profile = VpnProfile(
            name = remark,
            protocol = ProtocolType.VMESS,
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
