package ir.weirdnet.client.core

/**
 * Every user-facing error in WEIRDNET is one of these. Each carries a plain-English
 * [message] and, where one exists, a concrete [suggestion] for what to do about it --
 * satisfying requirement #16 ("no vague errors").
 */
sealed class WeirdNetError(val message: String, val suggestion: String? = null) {

    data class InvalidConfiguration(val details: String) :
        WeirdNetError("Invalid configuration: $details", "Check the link or file and try importing it again.")

    data class UnsupportedProtocol(val scheme: String) :
        WeirdNetError(
            "Unsupported protocol: \"$scheme\"",
            "WEIRDNET currently supports vless, vmess, trojan, ss, and WireGuard configurations."
        )

    object MissingServer :
        WeirdNetError("The configuration is missing a server address.", "Re-export the configuration from its source and try again.")

    data class InvalidPort(val rawValue: String) :
        WeirdNetError("\"$rawValue\" is not a valid port number.", "A port must be a number between 1 and 65535.")

    object InvalidUuid :
        WeirdNetError("The configuration's UUID is not formatted correctly.", "UUIDs look like 8-4-4-4-12 hexadecimal groups, e.g. 123e4567-e89b-12d3-a456-426614174000.")

    object VpnPermissionDenied :
        WeirdNetError(
            "WEIRDNET needs the system VPN permission to create a tunnel.",
            "Tap Connect again and accept the \"Connection request\" dialog from Android."
        )

    data class CoreFailedToStart(val engineName: String, val reason: String) :
        WeirdNetError("The $engineName engine failed to start: $reason")

    object ConnectionTimedOut :
        WeirdNetError("The connection attempt timed out.", "Check the server address and port, and confirm the server is reachable.")

    data class DnsResolutionFailed(val host: String) :
        WeirdNetError("Could not resolve \"$host\".", "Check your internet connection and that the hostname is correct.")

    data class TunnelFailedToEstablish(val reason: String) :
        WeirdNetError("The VPN tunnel could not be established: $reason")

    object EngineNotInstalled :
        WeirdNetError(
            "The Xray engine is not bundled in this build.",
            "See docs/XRAY_INTEGRATION.md for how to add the Xray-core dependency, then rebuild."
        )

    data class Unknown(val details: String) :
        WeirdNetError("Something specific went wrong: $details")
}
