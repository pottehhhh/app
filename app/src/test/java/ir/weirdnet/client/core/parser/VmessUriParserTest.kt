package ir.weirdnet.client.core.parser

import android.util.Base64
import com.google.common.truth.Truth.assertThat
import ir.weirdnet.client.data.model.ProtocolType
import ir.weirdnet.client.data.model.SecurityType
import ir.weirdnet.client.data.model.TransportType
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VmessUriParserTest {

    private fun buildVmessUri(json: JSONObject): String {
        val encoded = Base64.encodeToString(json.toString().toByteArray(), Base64.NO_WRAP)
        return "vmess://$encoded"
    }

    @Test
    fun `parses a standard vmess payload`() {
        val json = JSONObject().apply {
            put("v", "2")
            put("ps", "Test Server")
            put("add", "vmess.example.com")
            put("port", "8443")
            put("id", "123e4567-e89b-12d3-a456-426614174000")
            put("aid", "0")
            put("net", "ws")
            put("type", "none")
            put("host", "cdn.example.com")
            put("path", "/ray")
            put("tls", "tls")
            put("sni", "cdn.example.com")
        }

        val result = ConfigParser.parse(buildVmessUri(json))

        assertThat(result).isInstanceOf(ParseResult.Success::class.java)
        val profile = (result as ParseResult.Success).profile
        assertThat(profile.protocol).isEqualTo(ProtocolType.VMESS)
        assertThat(profile.name).isEqualTo("Test Server")
        assertThat(profile.port).isEqualTo(8443)
        assertThat(profile.transport).isEqualTo(TransportType.WEBSOCKET)
        assertThat(profile.security).isEqualTo(SecurityType.TLS)
        assertThat(profile.extra["path"]).isEqualTo("/ray")
    }

    @Test
    fun `rejects malformed base64`() {
        val result = VmessUriParser.parse("vmess://not-valid-base64-!!!")
        assertThat(result).isInstanceOf(ParseResult.Failure::class.java)
    }

    @Test
    fun `rejects missing server`() {
        val json = JSONObject().apply {
            put("id", "123e4567-e89b-12d3-a456-426614174000")
            put("port", "443")
        }
        val result = VmessUriParser.parse(buildVmessUri(json))
        assertThat(result).isInstanceOf(ParseResult.Failure::class.java)
    }
}
