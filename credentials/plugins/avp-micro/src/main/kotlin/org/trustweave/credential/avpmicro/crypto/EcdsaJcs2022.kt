package org.trustweave.credential.avpmicro.crypto

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.crypto.signers.HMacDSAKCalculator
import org.bouncycastle.jce.ECNamedCurveTable
import org.trustweave.core.util.decodeBase58
import org.trustweave.core.util.encodeBase58
import org.trustweave.kms.util.EcdsaSignatureCodec
import java.math.BigInteger
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey

object EcdsaJcs2022 {
    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)

    // P-256 group order n, and n/2 for the canonical low-s check.
    private val P256_N = BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16)
    private val P256_HALF_N = P256_N.shiftRight(1)
    private val secp256r1 = ECNamedCurveTable.getParameterSpec("secp256r1")

    /** The 64-byte input the spec signs: sha256(JCS(proofConfig)) || sha256(JCS(unsecuredDoc)). */
    fun verifyData(document: JsonObject): ByteArray {
        val proof = document["proof"]?.jsonObject ?: error("document has no proof object")
        val proofConfig = buildJsonObject {
            for ((k, v) in proof) if (k != "proofValue") put(k, v)
            document["@context"]?.let { put("@context", it) }
        }
        return hashData(JsonObject(document.filterKeys { it != "proof" }), proofConfig)
    }

    private fun hashData(unsecured: JsonObject, proofConfig: JsonObject): ByteArray =
        sha256(Jcs.canonicalize(proofConfig)) + sha256(Jcs.canonicalize(unsecured))

    /**
     * Produce an `ecdsa-jcs-2022` Data Integrity proof over [document] (any existing `proof`
     * is replaced). Deterministic RFC 6979 + canonical low-s; raw R‖S; multibase base58btc.
     */
    fun sign(
        document: JsonObject,
        privateKey: ECPrivateKey,
        verificationMethod: String,
        created: String,
        proofPurpose: String = "assertionMethod",
    ): JsonObject {
        val unsecured = JsonObject(document.filterKeys { it != "proof" })
        val base = buildJsonObject {
            put("type", "DataIntegrityProof")
            put("cryptosuite", "ecdsa-jcs-2022")
            put("created", created)
            put("verificationMethod", verificationMethod)
            put("proofPurpose", proofPurpose)
        }
        val proofConfig = JsonObject(base.toMutableMap().apply {
            unsecured["@context"]?.let { put("@context", it) }
        })
        val raw = signRaw(privateKey, hashData(unsecured, proofConfig))
        val proof = JsonObject(base.toMutableMap().apply {
            put("proofValue", JsonPrimitive("z" + raw.encodeBase58()))
        })
        return JsonObject(unsecured.toMutableMap().apply { put("proof", proof) })
    }

    private fun signRaw(privateKey: ECPrivateKey, hashData: ByteArray): ByteArray {
        val domain = ECDomainParameters(secp256r1.curve, secp256r1.g, secp256r1.n, secp256r1.h)
        val signer = ECDSASigner(HMacDSAKCalculator(SHA256Digest()))
        signer.init(true, ECPrivateKeyParameters(privateKey.s, domain))
        val e = sha256(hashData) // ECDSA(SHA-256) signs SHA-256 of the message, matching the spec
        val sig = signer.generateSignature(e)
        val r = sig[0]
        var s = sig[1]
        if (s > P256_HALF_N) s = P256_N.subtract(s) // canonical low-s, using the validated constants
        return toFixed32(r) + toFixed32(s)
    }

    private fun toFixed32(v: BigInteger): ByteArray {
        val b = v.toByteArray()
        return when {
            b.size == 32 -> b
            b.size == 33 && b[0].toInt() == 0 -> b.copyOfRange(1, 33)
            b.size < 32 -> ByteArray(32 - b.size) + b
            else -> error("unreachable: a P-256 scalar serializes to at most 33 bytes, got ${b.size}")
        }
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
