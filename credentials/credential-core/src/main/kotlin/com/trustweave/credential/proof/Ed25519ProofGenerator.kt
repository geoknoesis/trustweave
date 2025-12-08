package com.trustweave.credential.proof

import com.trustweave.credential.models.Proof
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.core.util.DigestUtils
import com.trustweave.did.identifiers.VerificationMethodId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

/**
 * Ed25519 proof generator implementation.
 *
 * Generates Ed25519Signature2020 proofs for verifiable credentials.
 * Uses Ed25519 signatures for credential signing.
 *
 * **Example Usage**:
 * ```kotlin
 * val generator = Ed25519ProofGenerator { data, keyId ->
 *     kms.sign(keyId, data)
 * }
 * ProofGeneratorRegistry.register(generator)
 *
 * val proof = generator.generateProof(
 *     credential = credential,
 *     keyId = "key-1",
 *     options = ProofOptions(proofPurpose = "assertionMethod")
 * )
 * ```
 */
class Ed25519ProofGenerator(
    private val signer: suspend (ByteArray, String) -> ByteArray,
    private val getPublicKeyId: suspend (String) -> String? = { null }
) : ProofGenerator {
    override val proofType = "Ed25519Signature2020"

    override suspend fun generateProof(
        credential: VerifiableCredential,
        keyId: String,
        options: ProofOptions
    ): Proof = withContext(Dispatchers.IO) {
        // Build verification method URL first (needed for proof document)
        val publicKeyId = getPublicKeyId(keyId)
        val verificationMethod = options.verificationMethod
            ?: (publicKeyId?.let { "did:key:$it#$keyId" } ?: "did:key:$keyId")

        val created = Clock.System.now().toString()

        // Build proof document (credential + proof metadata without proofValue)
        // This matches what the verifier expects per Ed25519Signature2020 spec
        val proofDocument = buildProofDocument(credential, proofType, created, verificationMethod, options)
        println("[DEBUG Ed25519ProofGenerator] Proof document (first 200 chars): ${proofDocument.take(200)}")

        // Sign the proof document
        val documentBytes = proofDocument.toByteArray(Charsets.UTF_8)
        println("[DEBUG Ed25519ProofGenerator] Document bytes length: ${documentBytes.size}")
        val signature = signer(documentBytes, keyId)
        println("[DEBUG Ed25519ProofGenerator] Signature bytes length: ${signature.size}")

        // Encode signature as multibase
        val proofValue = encodeMultibase(signature)
        println("[DEBUG Ed25519ProofGenerator] Proof value (first 50 chars): ${proofValue.take(50)}")

        // Create proof
        Proof(
            type = ProofTypes.fromString(proofType),
            created = created,
            verificationMethod = VerificationMethodId.parse(verificationMethod),
            proofPurpose = options.proofPurpose,
            proofValue = proofValue,
            challenge = options.challenge,
            domain = options.domain
        )
    }

    /**
     * Build proof document (credential + proof metadata without proofValue).
     * This is what gets signed per Ed25519Signature2020 spec.
     * The verifier expects the same format.
     */
    private fun buildProofDocument(
        credential: VerifiableCredential,
        proofType: String,
        created: String,
        verificationMethod: String,
        options: ProofOptions
    ): String {
        val json = Json {
            prettyPrint = false
            encodeDefaults = false
            ignoreUnknownKeys = true
        }

        // Serialize credential without proof - use same approach as verifier
        // This ensures identical JSON structure between issuance and verification
        val credentialJson = json.encodeToJsonElement(credential.copy(proof = null))

        // Add proof options (without proofValue) - matches verifier exactly
        val proofOptions = buildJsonObject {
            put("type", proofType)
            put("created", created)
            put("verificationMethod", verificationMethod)
            put("proofPurpose", options.proofPurpose)
            options.challenge?.let { put("challenge", it) }
            options.domain?.let { put("domain", it) }
        }

        // Build proof document - matches verifier structure exactly
        val proofDocument = buildJsonObject {
            // Add all credential fields from serialized credential
            credentialJson.jsonObject.entries.forEach { (key, value) ->
                put(key, value as JsonElement)
            }
            // Add proof metadata (without proofValue)
            put("proof", proofOptions)
        }

        // Encode to JSON string (same as verifier)
        val jsonString = json.encodeToString(JsonObject.serializer(), proofDocument)

        // Canonicalize using DigestUtils (same as verifier)
        return DigestUtils.canonicalizeJson(jsonString)
    }

    /**
     * Encode bytes as multibase (base58btc).
     */
    private fun encodeMultibase(bytes: ByteArray): String {
        // Simple base58 encoding (for production, use a proper multibase library)
        val base58 = encodeBase58(bytes)
        return "z$base58" // 'z' prefix indicates base58btc
    }

    /**
     * Encode bytes as base58.
     */
    private fun encodeBase58(bytes: ByteArray): String {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var num = java.math.BigInteger(1, bytes)
        val sb = StringBuilder()

        while (num > java.math.BigInteger.ZERO) {
            val remainder = num.mod(java.math.BigInteger.valueOf(58))
            sb.append(alphabet[remainder.toInt()])
            num = num.divide(java.math.BigInteger.valueOf(58))
        }

        for (byte in bytes) {
            if (byte.toInt() == 0) {
                sb.append('1')
            } else {
                break
            }
        }

        return sb.reverse().toString()
    }
}

