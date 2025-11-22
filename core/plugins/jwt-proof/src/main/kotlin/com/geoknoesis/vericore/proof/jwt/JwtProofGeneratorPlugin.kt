package com.geoknoesis.vericore.proof.jwt

import com.geoknoesis.vericore.credential.proof.ProofGenerator
import com.geoknoesis.vericore.credential.proof.ProofOptions
import com.geoknoesis.vericore.credential.models.Proof
import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.util.Base64URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.time.Instant
import java.util.*

/**
 * JWT proof generator plugin implementation.
 * 
 * Generates JsonWebSignature2020 proofs in JWT format using nimbus-jose-jwt.
 * 
 * Supports Ed25519, ECDSA (secp256k1, P-256, P-384, P-521), and RSA algorithms.
 * 
 * **Example Usage:**
 * ```kotlin
 * val generator = JwtProofGeneratorPlugin(
 *     signer = { data, keyId -> kms.sign(keyId, data) },
 *     getPublicKeyId = { keyId -> kms.getPublicKey(keyId).id }
 * )
 * 
 * val proof = generator.generateProof(credential, keyId, options)
 * ```
 */
class JwtProofGeneratorPlugin(
    private val signer: suspend (ByteArray, String) -> ByteArray,
    private val getPublicKeyId: suspend (String) -> String? = { null },
    private val getPublicKeyJwk: suspend (String) -> Map<String, Any?>? = { null }
) : ProofGenerator {
    override val proofType = "JsonWebSignature2020"
    
    override suspend fun generateProof(
        credential: VerifiableCredential,
        keyId: String,
        options: ProofOptions
    ): Proof = withContext(Dispatchers.IO) {
        val verificationMethod = options.verificationMethod 
            ?: (getPublicKeyId(keyId)?.let { "did:key:$it#$keyId" } ?: "did:key:$keyId")
        
        // Extract subject ID from credentialSubject
        val subjectId = try {
            val subjectJson = credential.credentialSubject.jsonObject
            subjectJson["id"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
        
        // Determine algorithm from public key JWK
        val publicKeyJwk = getPublicKeyJwk(keyId)
        val algorithm = determineAlgorithm(publicKeyJwk)
        
        // Build JWT header
        val headerJson = buildJsonObject {
            put("alg", algorithm.name)
            put("typ", "JWT")
            put("kid", keyId)
        }
        
        // Build JWT payload (claims)
        val expirationTimestamp = credential.expirationDate?.let {
            try {
                Instant.parse(it).epochSecond
            } catch (e: Exception) {
                Instant.now().plusSeconds(365 * 24 * 60 * 60).epochSecond // Default: 1 year
            }
        } ?: Instant.now().plusSeconds(365 * 24 * 60 * 60).epochSecond // Default: 1 year
        
        val nbfTimestamp = try {
            Instant.parse(credential.issuanceDate).epochSecond
        } catch (e: Exception) {
            Instant.now().epochSecond
        }
        
        // Encode header and payload as Base64URL
        val json = Json { prettyPrint = false; encodeDefaults = false }
        
        // Serialize credential to JSON for vc claim
        val vcJson = json.encodeToJsonElement(
            com.geoknoesis.vericore.credential.models.VerifiableCredential.serializer(),
            credential
        ).jsonObject
        
        val payloadJson = buildJsonObject {
            put("vc", vcJson)
            put("iss", verificationMethod)
            put("sub", subjectId ?: verificationMethod)
            if (options.domain != null) {
                put("aud", options.domain)
            }
            put("exp", expirationTimestamp)
            put("nbf", nbfTimestamp)
            put("jti", UUID.randomUUID().toString())
        }
        val headerBase64 = Base64URL.encode(json.encodeToString(JsonObject.serializer(), headerJson).toByteArray(Charsets.UTF_8)).toString()
        val payloadBase64 = Base64URL.encode(json.encodeToString(JsonObject.serializer(), payloadJson).toByteArray(Charsets.UTF_8)).toString()
        
        // Signing input is the base64-encoded header and payload separated by a dot
        val signingInput = "$headerBase64.$payloadBase64".toByteArray(Charsets.UTF_8)
        
        // Sign the JWT using custom signer
        val signature = signer(signingInput, keyId)
        val signatureBase64 = Base64URL.encode(signature).toString()
        
        // Create compact JWT string
        val jwtString = "$headerBase64.$payloadBase64.$signatureBase64"
        
        Proof(
            type = proofType,
            created = Instant.now().toString(),
            verificationMethod = verificationMethod,
            proofPurpose = options.proofPurpose,
            jws = jwtString,
            challenge = options.challenge,
            domain = options.domain
        )
    }
    
    /**
     * Converts VerifiableCredential to JSON object for JWT claim.
     */
    private fun credentialToJsonObject(credential: VerifiableCredential): Map<String, Any?> {
        val json = kotlinx.serialization.json.Json {
            prettyPrint = false
            encodeDefaults = false
            ignoreUnknownKeys = true
        }
        
        // Serialize credential to JSON and parse as JsonElement
        val credentialJson = json.encodeToJsonElement(
            com.geoknoesis.vericore.credential.models.VerifiableCredential.serializer(),
            credential
        )
        
        // Convert JsonElement to Map for JWT claims
        return jsonElementToMap(credentialJson.jsonObject)
    }
    
    /**
     * Converts JsonObject to Map<String, Any?> for JWT claims.
     */
    private fun jsonElementToMap(jsonObject: kotlinx.serialization.json.JsonObject): Map<String, Any?> {
        return jsonObject.entries.associate { (key, value) ->
            key to when (value) {
                is kotlinx.serialization.json.JsonPrimitive -> {
                    when {
                        value.isString -> value.content
                        value.booleanOrNull != null -> value.boolean
                        value.longOrNull != null -> value.long
                        value.doubleOrNull != null -> value.double
                        else -> value.content
                    }
                }
                is kotlinx.serialization.json.JsonArray -> {
                    value.map { element ->
                        when (element) {
                            is kotlinx.serialization.json.JsonPrimitive -> element.content
                            is kotlinx.serialization.json.JsonObject -> jsonElementToMap(element)
                            else -> element.toString()
                        }
                    }
                }
                is kotlinx.serialization.json.JsonObject -> jsonElementToMap(value)
                else -> value.toString()
            }
        }
    }
    
    /**
     * Determines JWS algorithm from public key JWK.
     */
    private fun determineAlgorithm(publicKeyJwk: Map<String, Any?>?): JWSAlgorithm {
        if (publicKeyJwk == null) {
            return JWSAlgorithm.EdDSA // Default to Ed25519
        }
        
        val kty = publicKeyJwk["kty"] as? String ?: return JWSAlgorithm.EdDSA
        val crv = publicKeyJwk["crv"] as? String
        
        return when {
            kty == "OKP" && crv == "Ed25519" -> JWSAlgorithm.EdDSA
            kty == "EC" && crv == "secp256k1" -> JWSAlgorithm.ES256K
            kty == "EC" && crv == "P-256" -> JWSAlgorithm.ES256
            kty == "EC" && crv == "P-384" -> JWSAlgorithm.ES384
            kty == "EC" && crv == "P-521" -> JWSAlgorithm.ES512
            kty == "RSA" -> {
                val n = publicKeyJwk["n"] as? String
                val keySize = n?.let { Base64.getUrlDecoder().decode(it).size * 8 } ?: 2048
                when {
                    keySize <= 2048 -> JWSAlgorithm.RS256
                    keySize <= 3072 -> JWSAlgorithm.RS384
                    else -> JWSAlgorithm.RS512
                }
            }
            else -> JWSAlgorithm.EdDSA // Default fallback
        }
    }
}

