package ir.weirdnet.client.core

import ir.weirdnet.client.data.model.VpnProfile
import ir.weirdnet.client.data.repository.AppSettings

/**
 * Applies the global VPN settings (Settings > VPN: "Allow IPv6", "Custom DNS")
 * to a profile immediately before it's handed to a [TunnelEngine], producing an
 * "effective" profile for that connection only -- the saved profile itself is
 * never mutated.
 *
 * This exists because those two settings were previously persisted by
 * [ir.weirdnet.client.data.repository.SettingsRepository] and displayed in the
 * UI but never actually read by anything that connects a tunnel -- toggling
 * them had no real effect. [VpnConnectionManager] calls this on every connect
 * so they do what the Settings screen says they do.
 */
internal object GlobalSettingsApplier {

    fun apply(profile: VpnProfile, settings: AppSettings): VpnProfile {
        var extra = profile.extra

        if (settings.customDnsEnabled && settings.customDns.isNotBlank()) {
            extra = extra + ("dns" to settings.customDns)
        }

        if (!settings.ipv6Enabled) {
            extra["allowedIps"]?.let { allowedIps ->
                extra = extra + ("allowedIps" to stripIpv6Entries(allowedIps))
            }
        }

        return if (extra === profile.extra) profile else profile.copy(extra = extra)
    }

    /**
     * Removes IPv6 CIDR entries from a comma-separated AllowedIPs string,
     * identified by the presence of ':' (no valid IPv4 CIDR contains one).
     * If every entry turns out to be IPv6 (a v6-only AllowedIPs list), the
     * original string is returned unchanged rather than producing an empty,
     * unroutable AllowedIPs -- disabling IPv6 should never silently break an
     * IPv6-only tunnel.
     */
    internal fun stripIpv6Entries(allowedIps: String): String {
        val entries = allowedIps.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val ipv4Only = entries.filterNot { it.contains(':') }
        return if (ipv4Only.isEmpty()) allowedIps else ipv4Only.joinToString(", ")
    }
}
