package ir.weirdnet.client.core.xray

import com.google.common.truth.Truth.assertThat
import ir.weirdnet.client.data.db.SecretCipher
import ir.weirdnet.client.data.model.ProtocolType
import ir.weirdnet.client.data.model.SecurityType
import ir.weirdnet.client.data.model.TransportType
import ir.weirdnet.client.data.model.VpnProfile
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class XrayConfigBuilderTest {

    @Test
    fun `builds a valid vless plus websocket plus tls outbound`() {
        val profile = VpnProfile(
            name = "Test",
            protocol = ProtocolType.VLESS,
            server = "example.com",
            port = 443,
            secret = SecretCipher.encrypt("123e4567-e89b-12d3-a456-426614174000"),
            transport = TransportType.WEBSOCKET,
            security = SecurityType.TLS,
            extra = mapOf("path" to "/ray", "wsHost" to "cdn.example.com", "sni" to "cdn.example.com")
        )

        val json = JSONObject(XrayConfigBuilder.build(profile))
        val outbound = json.getJSONArray("outbounds").getJSONObject(0)

        assertThat(outbound.getString("protocol")).isEqualTo("vless")
        val vnext = outbound.getJSONObject("settings").getJSONArray("vnext").getJSONObject(0)
        assertThat(vnext.getString("address")).isEqualTo("example.com")
        assertThat(vnext.getInt("port")).isEqualTo(443)
        val user = vnext.getJSONArray("users").getJSONObject(0)
        assertThat(user.getString("id")).isEqualTo("123e4567-e89b-12d3-a456-426614174000")

        val stream = outbound.getJSONObject("streamSettings")
        assertThat(stream.getString("network")).isEqualTo("ws")
        assertThat(stream.getString("security")).isEqualTo("tls")
        assertThat(stream.getJSONObject("wsSettings").getString("path")).isEqualTo("/ray")
    }

    @Test
    fun `builds a valid shadowsocks outbound`() {
        val profile = VpnProfile(
            name = "SS",
            protocol = ProtocolType.SHADOWSOCKS,
            server = "ss.example.com",
            port = 8388,
            secret = SecretCipher.encrypt("hunter2"),
            extra = mapOf("cipher" to "aes-256-gcm")
        )
        val json = JSONObject(XrayConfigBuilder.build(profile))
        val outbound = json.getJSONArray("outbounds").getJSONObject(0)
        val server = outbound.getJSONObject("settings").getJSONArray("servers").getJSONObject(0)
        assertThat(server.getString("method")).isEqualTo("aes-256-gcm")
        assertThat(server.getString("password")).isEqualTo("hunter2")
    }

    @Test
    fun `always includes a direct freedom outbound as fallback`() {
        val profile = VpnProfile(
            name = "T", protocol = ProtocolType.TROJAN, server = "t.example.com", port = 443,
            secret = SecretCipher.encrypt("pw")
        )
        val json = JSONObject(XrayConfigBuilder.build(profile))
        val outbounds = json.getJSONArray("outbounds")
        val tags = (0 until outbounds.length()).map { outbounds.getJSONObject(it).getString("tag") }
        assertThat(tags).contains("direct")
    }
}
