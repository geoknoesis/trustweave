package com.geoknoesis.vericore.credential.proof

import com.geoknoesis.vericore.credential.models.Proof
import com.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.time.Instant

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
        // Build proof document (credential without proof)
        val proofDocument = buildProofDocument(credential)
        
        // Sign the proof document
        val signature = signer(proofDocument.toByteArray(Charsets.UTF_8), keyId)
        
        // Encode signature as multibase
        val proofValue = encodeMultibase(signature)
        
        // Build verification method URL
        val publicKeyId = getPublicKeyId(keyId)
        val verificationMethod = options.verificationMethod 
            ?: (publicKeyId?.let { "did:key:$it#$keyId" } ?: "did:key:$keyId")
        
        // Create proof
        Proof(
            type = proofType,
            created = Instant.now().toString(),
            verificationMethod = verificationMethod,
            proofPurpose = options.proofPurpose,
            proofValue = proofValue,
            challenge = options.challenge,
            domain = options.domain
        )
    }
    
    /**
     * Build proof document (credential without proof field).
     * This is what gets signed.
     */
    private fun buildProofDocument(credential: VerifiableCredential): String {
        val json = Json {
            prettyPrint = false
            encodeDefaults = false
            ignoreUnknownKeys = true
        }
        
        // Build credential JSON without proof
        val credentialJson = buildJsonObject {
            credential.id?.let { put("id", it) }
            put("type", buildJsonArray { credential.type.forEach { add(it) } })
            put("issuer", credential.issuer)
            put("credentialSubject", credential.credentialSubject)
            put("issuanceDate", credential.issuanceDate)
            credential.expirationDate?.let { put("expirationDate", it) }
            credential.credentialStatus?.let { 
                put("credentialStatus", buildJsonObject {
                    put("id", it.id)
                    put("type", it.type)
                    it.statusPurpose?.let { put("statusPurpose", it) }
                    it.statusListIndex?.let { put("statusListIndex", it) }
                    it.statusListCredential?.let { put("statusListCredential", it) }
                })
            }
            credential.credentialSchema?.let {
                put("credentialSchema", buildJsonObject {
                    put("id", it.id)
                    put("type", it.type)
                })
            }
            credential.evidence?.let { 
                put("evidence", buildJsonArray { 
                    it.forEach { evidence ->
                        // Evidence is already JsonElement-compatible, add directly
                        add(evidence.evidenceDocument ?: buildJsonObject {
                            evidence.id?.let { put("id", it) }
                            put("type", buildJsonArray { evidence.type.forEach { add(it) } })
                            evidence.verifier?.let { put("verifier", it) }
                            evidence.evidenceDate?.let { put("evidenceDate", it) }
                        })
                    }
                }) 
            }
            credential.termsOfUse?.let { put("termsOfUse", it.termsOfUse) }
            credential.refreshService?.let {
                put("refreshService", buildJsonObject {
                    put("id", it.id)
                    put("type", it.type)
                    put("serviceEndpoint", it.serviceEndpoint)
                })
            }
        }
        
        // Canonicalize JSON (sort keys)
        return canonicalizeJson(json.encodeToJsonElement(credentialJson))
    }
    
    /**
     * Canonicalize JSON by sorting keys lexicographically.
     */
    private fun canonicalizeJson(element: JsonElement): String {
        val sorted = sortKeys(element)
        val json = Json {
            prettyPrint = false
            encodeDefaults = false
        }
        return json.encodeToString(JsonElement.serializer(), sorted)
    }
    
    /**
     * Recursively sort keys in JSON object.
     */
    private fun sortKeys(element: JsonElement): JsonElement {
        return when (element) {
            is JsonObject -> {
                val sortedEntries = element.entries.sortedBy { it.key }
                    .map { it.key to sortKeys(it.value) }
                buildJsonObject {
                    sortedEntries.forEach { (key, value) ->
                        put(key, value)
                    }
                }
            }
            is JsonArray -> {
                buildJsonArray {
                    element.forEach { add(sortKeys(it)) }
                }
            }
            else -> element
        }
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

