package ir.weirdnet.client.core.parser

import android.util.Base64
import com.google.common.truth.Truth.assertThat
import ir.weirdnet.client.data.db.SecretCipher
import ir.weirdnet.client.data.model.ProtocolType
import ir.weirdnet.client.data.model.SecurityType
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TrojanAndShadowsocksParserTest {

    @Test
    fun `parses a trojan link and defaults to TLS`() {
        val uri = "trojan://p%40ssw0rd@trojan.example.com:443?sni=trojan.example.com#Trojan+Node"
        val result = TrojanUriParser.parse(uri)
        assertThat(result).isInstanceOf(ParseResult.Success::class.java)
        val profile = (result as ParseResult.Success).profile
        assertThat(profile.protocol).isEqualTo(ProtocolType.TROJAN)
        assertThat(profile.security).isEqualTo(SecurityType.TLS)
        assertThat(profile.name).isEqualTo("Trojan Node")
        assertThat(SecretCipher.decrypt(profile.secret)).isEqualTo("p@ssw0rd")
    }

    @Test
    fun `parses a SIP002-format shadowsocks link`() {
        val userInfo = Base64.encodeToString("aes-256-gcm:hunter2".toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)
        val uri = "ss://$userInfo@ss.example.com:8388#SS+Node"
        val result = ShadowsocksUriParser.parse(uri)
        assertThat(result).isInstanceOf(ParseResult.Success::class.java)
        val profile = (result as ParseResult.Success).profile
        assertThat(profile.server).isEqualTo("ss.example.com")
        assertThat(profile.port).isEqualTo(8388)
        assertThat(profile.extra["cipher"]).isEqualTo("aes-256-gcm")
        assertThat(SecretCipher.decrypt(profile.secret)).isEqualTo("hunter2")
    }

    @Test
    fun `parses a legacy fully-encoded shadowsocks link`() {
        val body = Base64.encodeToString(
            "chacha20-ietf-poly1305:legacyPass@legacy.example.com:9000".toByteArray(),
            Base64.NO_WRAP
        )
        val uri = "ss://$body#Legacy+Node"
        val result = ShadowsocksUriParser.parse(uri)
        assertThat(result).isInstanceOf(ParseResult.Success::class.java)
        val profile = (result as ParseResult.Success).profile
        assertThat(profile.server).isEqualTo("legacy.example.com")
        assertThat(profile.port).isEqualTo(9000)
        assertThat(profile.name).isEqualTo("Legacy Node")
    }

    @Test
    fun `rejects a trojan link missing password`() {
        val result = TrojanUriParser.parse("trojan://trojan.example.com:443")
        assertThat(result).isInstanceOf(ParseResult.Failure::class.java)
    }
}
