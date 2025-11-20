package com.geoknoesis.vericore.proof.ld

import com.geoknoesis.vericore.credential.proof.ProofGenerator
import com.geoknoesis.vericore.credential.proof.ProofOptions
import com.geoknoesis.vericore.credential.models.Proof
import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.github.jsonldjava.core.JsonLdOptions
import com.github.jsonldjava.core.JsonLdProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.time.Instant

/**
 * LD-Proof (JSON-LD Signature) proof generator plugin implementation.
 * 
 * Generates Linked Data Proofs for verifiable credentials using JSON-LD signatures.
 * Supports multiple signature suites (Ed25519Signature2020, EcdsaSecp256k1Signature2019, etc.).
 * 
 * **Note**: This is a placeholder implementation. Full implementation requires
 * JSON-LD canonicalization and signature suite libraries.
 */
class LdProofGeneratorPlugin(
    private val signer: suspend (ByteArray, String) -> ByteArray,
    private val getPublicKeyId: suspend (String) -> String? = { null },
    private val signatureSuite: String = "Ed25519Signature2020"
) : ProofGenerator {
    override val proofType: String = signatureSuite
    
    override suspend fun generateProof(
        credential: VerifiableCredential,
        keyId: String,
        options: ProofOptions
    ): Proof = withContext(Dispatchers.IO) {
        val verificationMethod = options.verificationMethod 
            ?: (getPublicKeyId(keyId)?.let { "did:key:$it#$keyId" } ?: "did:key:$keyId")
        
        // Build proof document (credential + proof options without signature)
        val proofDocument = buildProofDocument(credential, verificationMethod, options)
        
        // Canonicalize proof document using JSON-LD
        val canonicalDocument = canonicalizeDocument(proofDocument)
        
        // Generate signature using signer function
        val signature = signer(canonicalDocument.toByteArray(Charsets.UTF_8), keyId)
        
        // Encode signature as multibase (base64url)
        val proofValue = encodeMultibase(signature)
        
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
     * Build proof document (credential + proof options without signature).
     */
    private fun buildProofDocument(
        credential: VerifiableCredential,
        verificationMethod: String,
        options: ProofOptions
    ): JsonObject {
        val json = Json {
            prettyPrint = false
            encodeDefaults = false
            ignoreUnknownKeys = true
        }
        
        // Serialize credential to JSON
        val credentialJson = json.encodeToJsonElement(
            com.geoknoesis.vericore.credential.models.VerifiableCredential.serializer(),
            credential
        ).jsonObject
        
        // Build proof options (without proofValue)
        val proofOptions = buildJsonObject {
            put("type", proofType)
            put("created", Instant.now().toString())
            put("verificationMethod", verificationMethod)
            put("proofPurpose", options.proofPurpose)
            options.challenge?.let { put("challenge", it) }
            options.domain?.let { put("domain", it) }
        }
        
        // Combine credential and proof options
        return buildJsonObject {
            credentialJson.entries.forEach { (key, value) ->
                put(key, value)
            }
            put("proof", proofOptions)
        }
    }
    
    /**
     * Canonicalize document using JSON-LD.
     */
    private fun canonicalizeDocument(document: JsonObject): String {
        val json = Json {
            prettyPrint = false
            encodeDefaults = false
            ignoreUnknownKeys = true
        }
        
        // Convert JsonObject to Map for jsonld-java
        val documentMap = jsonElementToMap(document)
        
        // Canonicalize using JSON-LD
        val options = JsonLdOptions()
        options.format = "application/n-quads"
        val canonical = JsonLdProcessor.normalize(documentMap, options)
        
        return canonical.toString()
    }
    
    /**
     * Convert JsonObject to Map for jsonld-java.
     */
    private fun jsonElementToMap(jsonObject: JsonObject): Map<String, Any> {
        return jsonObject.entries.associate { (key, value) ->
            key to when (value) {
                is JsonPrimitive -> {
                    when {
                        value.isString -> value.content
                        value.booleanOrNull != null -> value.boolean
                        value.longOrNull != null -> value.long
                        value.doubleOrNull != null -> value.double
                        else -> value.content
                    }
                }
                is JsonArray -> value.map { element ->
                    when (element) {
                        is JsonPrimitive -> element.content
                        is JsonObject -> jsonElementToMap(element)
                        else -> element.toString()
                    }
                }
                is JsonObject -> jsonElementToMap(value)
                else -> value.toString()
            }
        }
    }
    
    /**
     * Encode signature as multibase (base58btc encoding with 'z' prefix).
     */
    private fun encodeMultibase(data: ByteArray): String {
        val base58 = encodeBase58(data)
        return "z$base58" // 'z' prefix indicates base58btc encoding
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
        
        // Add leading zeros
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

