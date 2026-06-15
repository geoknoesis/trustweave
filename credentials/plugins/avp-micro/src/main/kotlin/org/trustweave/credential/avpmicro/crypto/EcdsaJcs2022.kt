package org.trustweave.credential.avpmicro.crypto

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import java.security.MessageDigest

object EcdsaJcs2022 {
    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)

    /** The 64-byte input the spec signs: sha256(JCS(proofConfig)) || sha256(JCS(unsecuredDoc)). */
    fun verifyData(document: JsonObject): ByteArray {
        val proof = document.getValue("proof").jsonObject
        val proofConfig = buildJsonObject {
            for ((k, v) in proof) if (k != "proofValue") put(k, v)
            document["@context"]?.let { put("@context", it) }
        }
        val cfgHash = sha256(Jcs.canonicalize(proofConfig))
        val unsecured = JsonObject(document.filterKeys { it != "proof" })
        val docHash = sha256(Jcs.canonicalize(unsecured))
        return cfgHash + docHash
    }
}
