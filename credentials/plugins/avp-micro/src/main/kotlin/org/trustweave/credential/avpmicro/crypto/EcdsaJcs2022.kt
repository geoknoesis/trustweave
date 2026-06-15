package org.trustweave.credential.avpmicro.crypto

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.trustweave.core.util.decodeBase58
import org.trustweave.kms.util.EcdsaSignatureCodec
import java.math.BigInteger
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey

object EcdsaJcs2022 {
    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)

    // P-256 group order n, and n/2 for the canonical low-s check.
    private val P256_N = BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16)
    private val P256_HALF_N = P256_N.shiftRight(1)

    /** The 64-byte input the spec signs: sha256(JCS(proofConfig)) || sha256(JCS(unsecuredDoc)). */
    fun verifyData(document: JsonObject): ByteArray {
        val proof = document["proof"]?.jsonObject ?: error("document has no proof object")
        val proofConfig = buildJsonObject {
            for ((k, v) in proof) if (k != "proofValue") put(k, v)
            document["@context"]?.let { put("@context", it) }
        }
        val cfgHash = sha256(Jcs.canonicalize(proofConfig))
        val unsecured = JsonObject(document.filterKeys { it != "proof" })
        val docHash = sha256(Jcs.canonicalize(unsecured))
        return cfgHash + docHash
    }

    /** Verify with an explicitly supplied key. */
    fun verify(document: JsonObject, publicKey: ECPublicKey): Boolean {
        val proof = document["proof"]?.jsonObject ?: return false
        if (proof["cryptosuite"]?.jsonPrimitive?.content != "ecdsa-jcs-2022") return false
        val pv = proof["proofValue"]?.jsonPrimitive?.content ?: return false
        if (!pv.startsWith("z")) return false
        val raw = try { pv.substring(1).decodeBase58() } catch (e: Exception) { return false }
        if (raw.size != 64) return false
        val s = BigInteger(1, raw.copyOfRange(32, 64))
        if (s > P256_HALF_N) return false // spec mandates canonical low-s
        val der = EcdsaSignatureCodec.p1363ToDer(raw)
        return try {
            // SHA256withECDSA hashes verifyData again (i.e. signs SHA-256(hashData)),
            // matching the Python spec's ec.ECDSA(hashes.SHA256()). Do NOT switch to NONEwithECDSA.
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(publicKey)
                update(verifyData(document))
                verify(der)
            }
        } catch (e: Exception) { false }
    }

    /** Verify, resolving the public key from the proof's verificationMethod did:key. */
    fun verify(document: JsonObject): Boolean {
        val vm = document["proof"]?.jsonObject?.get("verificationMethod")?.jsonPrimitive?.content
            ?: return false
        return try { verify(document, P256DidKey.publicKeyFrom(vm)) } catch (e: Exception) { false }
    }
}
