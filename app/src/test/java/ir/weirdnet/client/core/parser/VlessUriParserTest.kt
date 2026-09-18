package ir.weirdnet.client.core.parser

import com.google.common.truth.Truth.assertThat
import ir.weirdnet.client.core.WeirdNetError
import ir.weirdnet.client.data.db.SecretCipher
import ir.weirdnet.client.data.model.ProtocolType
import ir.weirdnet.client.data.model.SecurityType
import ir.weirdnet.client.data.model.TransportType
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VlessUriParserTest {

    private val validUuid = "123e4567-e89b-12d3-a456-426614174000"

    @Test
    fun `parses a well-formed vless link with reality`() {
        val uri = "vless://$validUuid@example.com:443?encryption=none&security=reality" +
            "&sni=www.microsoft.com&fp=chrome&pbk=abc123&sid=de&type=tcp&flow=xtls-rprx-vision#My+Server"

        val result = VlessUriParser.parse(uri)

        assertThat(result).isInstanceOf(ParseResult.Success::class.java)
        val profile = (result as ParseResult.Success).profile
        assertThat(profile.protocol).isEqualTo(ProtocolType.VLESS)
        assertThat(profile.server).isEqualTo("example.com")
        assertThat(profile.port).isEqualTo(443)
        assertThat(profile.security).isEqualTo(SecurityType.REALITY)
        assertThat(profile.transport).isEqualTo(TransportType.TCP)
        assertThat(profile.name).isEqualTo("My Server")
        assertThat(profile.extra["realityPublicKey"]).isEqualTo("abc123")
        assertThat(profile.extra["flow"]).isEqualTo("xtls-rprx-vision")
        // The UUID must never be stored in plaintext.
        assertThat(profile.secret).isNotEqualTo(validUuid)
        assertThat(SecretCipher.decrypt(profile.secret)).isEqualTo(validUuid)
    }

    @Test
    fun `rejects an invalid uuid`() {
        val uri = "vless://not-a-uuid@example.com:443?encryption=none"
        val result = VlessUriParser.parse(uri)
        assertThat(result).isInstanceOf(ParseResult.Failure::class.java)
        assertThat((result as ParseResult.Failure).error).isEqualTo(WeirdNetError.InvalidUuid)
    }

    @Test
    fun `rejects a link with no port`() {
        val uri = "vless://$validUuid@example.com?encryption=none"
        val result = VlessUriParser.parse(uri)
        assertThat(result).isInstanceOf(ParseResult.Failure::class.java)
        assertThat((result as ParseResult.Failure).error).isInstanceOf(WeirdNetError.InvalidPort::class.java)
    }

    @Test
    fun `falls back to host as name when no remark is present`() {
        val uri = "vless://$validUuid@example.com:443?encryption=none"
        val result = VlessUriParser.parse(uri) as ParseResult.Success
        assertThat(result.profile.name).isEqualTo("example.com")
    }
}
