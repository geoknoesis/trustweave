package org.trustweave.hashicorpkms

import com.bettercloud.vault.response.LogicalResponse

/** The driver's string map flattens nested objects; use its structured data instead. */
internal object VaultPublicKeyResponse {
    fun extract(response: LogicalResponse): String? {
        val data = response.dataObject ?: return null
        val version = data.get("latest_version") ?: return null
        if (!version.isNumber) return null
        val number = version.toString().toIntOrNull()?.takeIf { it > 0 } ?: return null
        val keys = data.get("keys")?.takeIf { it.isObject }?.asObject() ?: return null
        val entry = keys.get(number.toString())?.takeIf { it.isObject }?.asObject() ?: return null
        return entry.get("public_key")?.takeIf { it.isString }?.asString()?.takeIf { it.isNotBlank() }
    }
}
