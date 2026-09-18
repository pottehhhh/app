package ir.weirdnet.client.core.parser

import com.google.common.truth.Truth.assertThat
import ir.weirdnet.client.core.wireguard.WireGuardSecrets
import ir.weirdnet.client.data.model.ProtocolType
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WireGuardConfParserTest {

    private val sampleConf = """
        [Interface]
        PrivateKey = 6PYIWklQtESVQKLxeUwUKAmgOP+Fs5tBk3Wp5EGL9nY=
        Address = 10.66.66.2/32
        DNS = 1.1.1.1, 1.0.0.1

        [Peer]
        PublicKey = xTIBA5rboUvnH4htodjb6e697QjLERt1NAB4mZqp8Dg=
        PresharedKey = FpCyhws9cxwWoV4xELtfJvjJN+zQVCEdRdslgOWyWMg=
        Endpoint = vpn.example.com:51820
        AllowedIPs = 0.0.0.0/0, ::/0
    """.trimIndent()

    @Test
    fun `parses a standard wg-quick conf`() {
        val result = WireGuardConfParser.parse(sampleConf, suggestedName = "My WireGuard")
        assertThat(result).isInstanceOf(ParseResult.Success::class.java)
        val profile = (result as ParseResult.Success).profile
        assertThat(profile.protocol).isEqualTo(ProtocolType.WIREGUARD)
        assertThat(profile.server).isEqualTo("vpn.example.com")
        assertThat(profile.port).isEqualTo(51820)
        assertThat(profile.extra["address"]).isEqualTo("10.66.66.2/32")
        assertThat(profile.extra["dns"]).isEqualTo("1.1.1.1, 1.0.0.1")

        val secrets = WireGuardSecrets.fromEncryptedString(profile.secret)
        assertThat(secrets.privateKey).isEqualTo("6PYIWklQtESVQKLxeUwUKAmgOP+Fs5tBk3Wp5EGL9nY=")
        assertThat(secrets.presharedKey).isEqualTo("FpCyhws9cxwWoV4xELtfJvjJN+zQVCEdRdslgOWyWMg=")
    }

    @Test
    fun `rejects a conf missing the Peer section`() {
        val invalid = "[Interface]\nPrivateKey = abc\nAddress = 10.0.0.1/32"
        val result = WireGuardConfParser.parse(invalid)
        assertThat(result).isInstanceOf(ParseResult.Failure::class.java)
    }

    @Test
    fun `rejects an endpoint without a port`() {
        val invalid = sampleConf.replace("vpn.example.com:51820", "vpn.example.com")
        val result = WireGuardConfParser.parse(invalid)
        assertThat(result).isInstanceOf(ParseResult.Failure::class.java)
    }

    @Test
    fun `detected correctly by the top-level ConfigParser facade`() {
        val result = ConfigParser.parse(sampleConf)
        assertThat(result).isInstanceOf(ParseResult.Success::class.java)
    }
}
