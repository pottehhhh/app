package ir.weirdnet.client.core

import com.google.common.truth.Truth.assertThat
import ir.weirdnet.client.data.model.ProtocolType
import ir.weirdnet.client.data.model.VpnProfile
import ir.weirdnet.client.data.repository.AppSettings
import org.junit.Test

class GlobalSettingsApplierTest {

    private fun sampleProfile(extra: Map<String, String> = emptyMap()) = VpnProfile(
        name = "Test",
        protocol = ProtocolType.WIREGUARD,
        server = "vpn.example.com",
        port = 51820,
        secret = "irrelevant-for-this-test",
        extra = extra
    )

    @Test
    fun `custom dns overrides the profile's own dns when enabled`() {
        val profile = sampleProfile(mapOf("dns" to "10.0.0.1"))
        val settings = AppSettings(customDnsEnabled = true, customDns = "9.9.9.9")

        val result = GlobalSettingsApplier.apply(profile, settings)

        assertThat(result.extra["dns"]).isEqualTo("9.9.9.9")
    }

    @Test
    fun `profile dns is left untouched when custom dns is disabled`() {
        val profile = sampleProfile(mapOf("dns" to "10.0.0.1"))
        val settings = AppSettings(customDnsEnabled = false, customDns = "9.9.9.9")

        val result = GlobalSettingsApplier.apply(profile, settings)

        assertThat(result.extra["dns"]).isEqualTo("10.0.0.1")
    }

    @Test
    fun `disabling ipv6 strips ipv6 entries from allowedIps`() {
        val profile = sampleProfile(mapOf("allowedIps" to "0.0.0.0/0, ::/0"))
        val settings = AppSettings(ipv6Enabled = false)

        val result = GlobalSettingsApplier.apply(profile, settings)

        assertThat(result.extra["allowedIps"]).isEqualTo("0.0.0.0/0")
    }

    @Test
    fun `enabling ipv6 leaves allowedIps untouched`() {
        val profile = sampleProfile(mapOf("allowedIps" to "0.0.0.0/0, ::/0"))
        val settings = AppSettings(ipv6Enabled = true)

        val result = GlobalSettingsApplier.apply(profile, settings)

        assertThat(result.extra["allowedIps"]).isEqualTo("0.0.0.0/0, ::/0")
    }

    @Test
    fun `an ipv6-only allowedIps is never reduced to nothing`() {
        val profile = sampleProfile(mapOf("allowedIps" to "::/0"))
        val settings = AppSettings(ipv6Enabled = false)

        val result = GlobalSettingsApplier.apply(profile, settings)

        // Better to keep routing all traffic than to silently produce an
        // AllowedIPs value that routes nothing at all.
        assertThat(result.extra["allowedIps"]).isEqualTo("::/0")
    }

    @Test
    fun `profile is returned unchanged when no relevant settings are active`() {
        val profile = sampleProfile(mapOf("allowedIps" to "0.0.0.0/0"))
        val settings = AppSettings(customDnsEnabled = false, ipv6Enabled = true)

        val result = GlobalSettingsApplier.apply(profile, settings)

        assertThat(result).isSameInstanceAs(profile)
    }
}
