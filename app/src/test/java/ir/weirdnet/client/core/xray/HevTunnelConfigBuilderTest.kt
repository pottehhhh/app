package ir.weirdnet.client.core.xray

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HevTunnelConfigBuilderTest {

    @Test
    fun `generates expected yaml structure`() {
        val yaml = HevTunnelConfigBuilder.build(
            tunnelIpv4 = "10.10.14.1",
            mtu = 1500,
            socks5Port = 10808
        )

        assertThat(yaml).contains("tunnel:")
        assertThat(yaml).contains("ipv4: 10.10.14.1")
        assertThat(yaml).contains("mtu: 1500")
        assertThat(yaml).contains("socks5:")
        assertThat(yaml).contains("port: 10808")
        assertThat(yaml).contains("address: 127.0.0.1")
    }

    @Test
    fun `uses a custom socks5 address when provided`() {
        val yaml = HevTunnelConfigBuilder.build(
            tunnelIpv4 = "10.10.14.1",
            mtu = 1400,
            socks5Port = 10808,
            socks5Address = "127.0.0.2"
        )
        assertThat(yaml).contains("address: 127.0.0.2")
    }

    @Test
    fun `socks5 port always matches XrayEngine's hardcoded local inbound port`() {
        val yaml = HevTunnelConfigBuilder.build(
            tunnelIpv4 = "10.10.14.1",
            mtu = 1500,
            socks5Port = XrayEngine.XRAY_LOCAL_SOCKS_PORT
        )
        assertThat(yaml).contains("port: ${XrayEngine.XRAY_LOCAL_SOCKS_PORT}")
    }
}
