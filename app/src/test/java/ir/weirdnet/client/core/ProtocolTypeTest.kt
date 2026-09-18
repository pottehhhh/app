package ir.weirdnet.client.core

import com.google.common.truth.Truth.assertThat
import ir.weirdnet.client.data.model.EngineFamily
import ir.weirdnet.client.data.model.ProtocolType
import org.junit.Test

class ProtocolTypeTest {

    @Test
    fun `resolves known schemes case-insensitively`() {
        assertThat(ProtocolType.fromScheme("vless")).isEqualTo(ProtocolType.VLESS)
        assertThat(ProtocolType.fromScheme("VLESS")).isEqualTo(ProtocolType.VLESS)
        assertThat(ProtocolType.fromScheme("ss")).isEqualTo(ProtocolType.SHADOWSOCKS)
    }

    @Test
    fun `returns null for unknown schemes`() {
        assertThat(ProtocolType.fromScheme("http")).isNull()
    }

    @Test
    fun `assigns the correct engine family to each protocol`() {
        assertThat(ProtocolType.VLESS.engine).isEqualTo(EngineFamily.XRAY)
        assertThat(ProtocolType.VMESS.engine).isEqualTo(EngineFamily.XRAY)
        assertThat(ProtocolType.TROJAN.engine).isEqualTo(EngineFamily.XRAY)
        assertThat(ProtocolType.SHADOWSOCKS.engine).isEqualTo(EngineFamily.XRAY)
        assertThat(ProtocolType.WIREGUARD.engine).isEqualTo(EngineFamily.WIREGUARD)
    }

    @Test
    fun `every error carries a non-blank, specific message`() {
        val errors = listOf(
            WeirdNetError.InvalidConfiguration("bad field"),
            WeirdNetError.UnsupportedProtocol("ftp"),
            WeirdNetError.MissingServer,
            WeirdNetError.InvalidPort("abc"),
            WeirdNetError.InvalidUuid,
            WeirdNetError.VpnPermissionDenied,
            WeirdNetError.CoreFailedToStart("Xray", "native library missing"),
            WeirdNetError.ConnectionTimedOut,
            WeirdNetError.DnsResolutionFailed("example.com"),
            WeirdNetError.TunnelFailedToEstablish("handshake failed"),
            WeirdNetError.EngineNotInstalled
        )
        errors.forEach { error ->
            assertThat(error.message).isNotEmpty()
            assertThat(error.message.lowercase()).doesNotContain("something went wrong")
        }
    }
}
