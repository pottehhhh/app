package ir.weirdnet.client.core.xray

import ir.weirdnet.client.data.db.SecretCipher
import ir.weirdnet.client.data.model.ProtocolType
import ir.weirdnet.client.data.model.SecurityType
import ir.weirdnet.client.data.model.TransportType
import ir.weirdnet.client.data.model.VpnProfile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Translates a [VpnProfile] into the JSON document format Xray-core's config
 * loader expects (`{"inbounds": [...], "outbounds": [...]}`). Structure matches
 * the official Xray-core configuration reference for each protocol.
 */
internal object XrayConfigBuilder {

    fun build(profile: VpnProfile): String {
        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "warning"))

        // A single SOCKS/tun2socks-facing inbound; the real TUN fd is bridged in
        // by the native layer, not represented in this JSON (see XrayEngine TODOs).
        val inbound = JSONObject().apply {
            put("tag", "tun-in")
            put("listen", "127.0.0.1")
            put("port", 10808)
            put("protocol", "socks")
            put("settings", JSONObject().put("udp", true))
        }

        val outbound = buildOutbound(profile)

        root.put("inbounds", JSONArray().put(inbound))
        root.put("outbounds", JSONArray().put(outbound).put(
            JSONObject().put("tag", "direct").put("protocol", "freedom")
        ))
        return root.toString()
    }

    private fun buildOutbound(profile: VpnProfile): JSONObject {
        val secret = SecretCipher.decrypt(profile.secret)
        val outbound = JSONObject()
        outbound.put("tag", "proxy")

        when (profile.protocol) {
            ProtocolType.VLESS -> {
                outbound.put("protocol", "vless")
                val user = JSONObject().apply {
                    put("id", secret)
                    put("encryption", profile.extra["encryption"] ?: "none")
                    profile.extra["flow"]?.let { put("flow", it) }
                }
                outbound.put("settings", JSONObject().put(
                    "vnext", JSONArray().put(
                        JSONObject()
                            .put("address", profile.server)
                            .put("port", profile.port)
                            .put("users", JSONArray().put(user))
                    )
                ))
            }
            ProtocolType.VMESS -> {
                outbound.put("protocol", "vmess")
                val user = JSONObject().apply {
                    put("id", secret)
                    put("alterId", profile.extra["alterId"]?.toIntOrNull() ?: 0)
                    put("security", profile.extra["cipher"] ?: "auto")
                }
                outbound.put("settings", JSONObject().put(
                    "vnext", JSONArray().put(
                        JSONObject()
                            .put("address", profile.server)
                            .put("port", profile.port)
                            .put("users", JSONArray().put(user))
                    )
                ))
            }
            ProtocolType.TROJAN -> {
                outbound.put("protocol", "trojan")
                outbound.put("settings", JSONObject().put(
                    "servers", JSONArray().put(
                        JSONObject()
                            .put("address", profile.server)
                            .put("port", profile.port)
                            .put("password", secret)
                    )
                ))
            }
            ProtocolType.SHADOWSOCKS -> {
                outbound.put("protocol", "shadowsocks")
                outbound.put("settings", JSONObject().put(
                    "servers", JSONArray().put(
                        JSONObject()
                            .put("address", profile.server)
                            .put("port", profile.port)
                            .put("password", secret)
                            .put("method", profile.extra["cipher"] ?: "chacha20-ietf-poly1305")
                    )
                ))
            }
            ProtocolType.WIREGUARD -> throw IllegalArgumentException("WireGuard is not an Xray protocol")
        }

        outbound.put("streamSettings", buildStreamSettings(profile))
        return outbound
    }

    private fun buildStreamSettings(profile: VpnProfile): JSONObject {
        val stream = JSONObject()
        stream.put("network", profile.transport.label)

        when (profile.transport) {
            TransportType.WEBSOCKET -> {
                stream.put("wsSettings", JSONObject().apply {
                    put("path", profile.extra["path"] ?: "/")
                    profile.extra["wsHost"]?.let {
                        put("headers", JSONObject().put("Host", it))
                    }
                })
            }
            TransportType.GRPC -> {
                stream.put("grpcSettings", JSONObject().apply {
                    put("serviceName", profile.extra["grpcServiceName"] ?: "")
                })
            }
            TransportType.XHTTP -> {
                stream.put("xhttpSettings", JSONObject().apply {
                    put("path", profile.extra["path"] ?: "/")
                    profile.extra["wsHost"]?.let { put("host", it) }
                })
            }
            TransportType.HTTP_UPGRADE -> {
                stream.put("httpupgradeSettings", JSONObject().apply {
                    put("path", profile.extra["path"] ?: "/")
                    profile.extra["wsHost"]?.let { put("host", it) }
                })
            }
            TransportType.KCP, TransportType.TCP -> { /* defaults are fine */ }
        }

        when (profile.security) {
            SecurityType.TLS -> {
                stream.put("security", "tls")
                stream.put("tlsSettings", JSONObject().apply {
                    put("serverName", profile.extra["sni"] ?: profile.server)
                    profile.extra["fingerprint"]?.let { put("fingerprint", it) }
                    profile.extra["alpn"]?.let { alpn ->
                        put("alpn", JSONArray(alpn.split(",")))
                    }
                })
            }
            SecurityType.REALITY -> {
                stream.put("security", "reality")
                stream.put("realitySettings", JSONObject().apply {
                    put("serverName", profile.extra["sni"] ?: profile.server)
                    put("fingerprint", profile.extra["fingerprint"] ?: "chrome")
                    put("publicKey", profile.extra["realityPublicKey"] ?: "")
                    put("shortId", profile.extra["realityShortId"] ?: "")
                })
            }
            SecurityType.NONE -> { /* plaintext transport, e.g. inside an already-encrypted tunnel */ }
        }
        return stream
    }
}
