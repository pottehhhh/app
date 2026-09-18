package ir.weirdnet.client.core.parser

import ir.weirdnet.client.core.WeirdNetError
import ir.weirdnet.client.core.wireguard.WireGuardSecrets
import ir.weirdnet.client.data.model.ProtocolType
import ir.weirdnet.client.data.model.VpnProfile

/**
 * Parses a standard WireGuard `.conf` file (INI-style [Interface]/[Peer] sections),
 * exactly as produced by `wg-quick` or any WireGuard config generator.
 */
object WireGuardConfParser {

    fun parse(content: String, suggestedName: String? = null): ParseResult {
        val sections = splitIntoSections(content)
        val interfaceSection = sections["interface"]
            ?: return ParseResult.Failure(WeirdNetError.InvalidConfiguration("missing [Interface] section"))
        val peerSection = sections["peer"]
            ?: return ParseResult.Failure(WeirdNetError.InvalidConfiguration("missing [Peer] section"))

        val privateKey = interfaceSection["privatekey"]
            ?: return ParseResult.Failure(WeirdNetError.InvalidConfiguration("[Interface] is missing PrivateKey"))
        val address = interfaceSection["address"]
            ?: return ParseResult.Failure(WeirdNetError.InvalidConfiguration("[Interface] is missing Address"))
        val dns = interfaceSection["dns"]
        val mtu = interfaceSection["mtu"]

        val publicKey = peerSection["publickey"]
            ?: return ParseResult.Failure(WeirdNetError.InvalidConfiguration("[Peer] is missing PublicKey"))
        val presharedKey = peerSection["presharedkey"]
        val endpoint = peerSection["endpoint"]
            ?: return ParseResult.Failure(WeirdNetError.MissingServer)
        val allowedIps = peerSection["allowedips"] ?: "0.0.0.0/0, ::/0"

        val endpointParts = endpoint.split(":")
        if (endpointParts.size != 2) {
            return ParseResult.Failure(WeirdNetError.InvalidConfiguration("Endpoint must be in host:port format"))
        }
        val host = ParseValidation.requireNonBlank(endpointParts[0])
            ?: return ParseResult.Failure(WeirdNetError.MissingServer)
        val port = ParseValidation.parsePortOrNull(endpointParts[1])
            ?: return ParseResult.Failure(WeirdNetError.InvalidPort(endpointParts[1]))

        val secrets = WireGuardSecrets(privateKey = privateKey, presharedKey = presharedKey)

        val extra = buildMap {
            put("publicKey", publicKey)
            put("address", address)
            put("allowedIps", allowedIps)
            dns?.let { put("dns", it) }
            mtu?.let { put("mtu", it) }
        }

        val profile = VpnProfile(
            name = suggestedName ?: host,
            protocol = ProtocolType.WIREGUARD,
            server = host,
            port = port,
            secret = secrets.toEncryptedString(),
            extra = extra
        )
        return ParseResult.Success(profile)
    }

    /** Returns a map of lower-cased section name -> (lower-cased key -> raw value). */
    private fun splitIntoSections(content: String): Map<String, Map<String, String>> {
        val sections = mutableMapOf<String, MutableMap<String, String>>()
        var currentSection: String? = null

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.substringBefore("#").substringBefore(";").trim()
            if (line.isEmpty()) return@forEach

            if (line.startsWith("[") && line.endsWith("]")) {
                currentSection = line.substring(1, line.length - 1).trim().lowercase()
                sections.putIfAbsent(currentSection!!, mutableMapOf())
                return@forEach
            }

            val section = currentSection ?: return@forEach
            val eqIndex = line.indexOf('=')
            if (eqIndex == -1) return@forEach
            val key = line.substring(0, eqIndex).trim().lowercase()
            val value = line.substring(eqIndex + 1).trim()
            sections[section]!![key] = value
        }
        return sections
    }
}
