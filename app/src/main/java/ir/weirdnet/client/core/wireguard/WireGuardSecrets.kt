package ir.weirdnet.client.core.wireguard

import ir.weirdnet.client.data.db.SecretCipher
import org.json.JSONObject

/**
 * WireGuard profiles have up to two secret values (private key, optional
 * pre-shared key). [VpnProfile.secret] only has room for one encrypted string,
 * so both are packed into a small JSON object before encryption and unpacked
 * after decryption. The public key is NOT a secret and lives in
 * [ir.weirdnet.client.data.model.VpnProfile.extra] instead.
 */
data class WireGuardSecrets(
    val privateKey: String,
    val presharedKey: String? = null
) {
    fun toEncryptedString(): String {
        val json = JSONObject().apply {
            put("privateKey", privateKey)
            presharedKey?.let { put("presharedKey", it) }
        }
        return SecretCipher.encrypt(json.toString())
    }

    companion object {
        fun fromEncryptedString(encrypted: String): WireGuardSecrets {
            val json = JSONObject(SecretCipher.decrypt(encrypted))
            return WireGuardSecrets(
                privateKey = json.getString("privateKey"),
                presharedKey = if (json.has("presharedKey")) json.getString("presharedKey") else null
            )
        }
    }
}
