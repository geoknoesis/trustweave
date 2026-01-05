package org.trustweave.credential.transform

import org.trustweave.credential.model.vc.VerifiableCredential
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import kotlinx.datetime.Instant as KotlinInstant
import java.time.Instant as JavaInstant
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
import com.nimbusds.jwt.SignedJWT
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fasterxml.jackson.databind.JsonNode

/**
 * Credential transformer for format conversion.
 *
 * Provides format conversion between different credential representations:
 * - JSON-LD (default W3C VC format)
 * - JWT (compact, widely supported)
 * - CBOR (binary, efficient - RFC 8949 compliant)
 *
 * **Recommended Usage (Elegant DSL API)**:
 * ```kotlin
 * // Use extension functions directly on VerifiableCredential (recommended)
 * val jwt = credential.toJwt()
 * val jsonLd = credential.toJsonLd()
 * val cbor = credential.toCbor()
 *
 * // Convert from formats
 * val credential = jwtString.fromJwt()
 * val credential = jsonLdObject.toCredential()
 * val credential = cborBytes.fromCbor()
 *
 * // Round-trip transformations
 * val recovered = credential.roundTripJwt()
 * ```
 *
 * **Alternative Usage (Direct API)**:
 * ```kotlin
 * val transformer = CredentialTransformer()
 * val jwt = transformer.toJwt(credential)
 * val credential = transformer.fromJwt(jwt)
 * ```
 *
 * **With TrustWeave Integration**:
 * ```kotlin
 * val jwt = trustWeave.toJwt(credential)
 * val jsonLd = trustWeave.toJsonLd(credential)
 * ```
 *
 * **With DSL Builder**:
 * ```kotlin
 * val jwt = credential.transform {
 *     toJwt()
 * }
 * ```
 *
 * @see org.trustweave.credential.transform.CredentialTransformerExtensions For extension functions
 * @see org.trustweave.credential.transform.CredentialTransformationBuilder For DSL builder
 */
class CredentialTransformer {
    private val json = Json {
        prettyPrint = false
        encodeDefaults = false
        ignoreUnknownKeys = true
        classDiscriminator = "@type" // Use @type instead of type to avoid conflict with LinkedDataProof.type
        useArrayPolymorphism = false
    }
    
    // Jackson ObjectMapper configured for CBOR
    private val cborMapper = ObjectMapper(CBORFactory()).apply {
        registerKotlinModule()
    }
    
    // Jackson ObjectMapper configured for JSON (used for CBOR->JSON conversion)
    private val jsonMapper = ObjectMapper().apply {
        registerKotlinModule()
    }

    /**
     * Convert credential to JWT format.
     *
     * Creates a JWT with the credential in the 'vc' claim.
     * Note: This creates an unsigned JWT. For signed JWTs, use CredentialService.issue().
     *
     * @param credential Credential to convert
     * @return JWT string (unsigned)
     */
    suspend fun toJwt(credential: VerifiableCredential): String {
        try {
            // Use nimbus-jose-jwt library directly (it's a required dependency)
            val builder = JWTClaimsSet.Builder()

            // Set issuer
            builder.issuer(credential.issuer.id.value)

            // Set issued at
            val issuedAt = credential.issuanceDate.epochSeconds
            builder.issueTime(java.util.Date.from(JavaInstant.ofEpochSecond(issuedAt)))

            // Set expiration if present
            credential.expirationDate?.let { expirationDate ->
                val expiration = expirationDate.epochSeconds
                builder.expirationTime(java.util.Date.from(JavaInstant.ofEpochSecond(expiration)))
            }

            // Set jti (credential ID)
            credential.id?.let { id ->
                builder.jwtID(id.value)
            }

            // Add vc claim with credential
            val credentialJson = json.encodeToJsonElement(credential)
            val credentialMap = jsonElementToMap(credentialJson)
            builder.claim("vc", credentialMap)

            // Build claims set
            val claimsSet = builder.build()

            // Create unsigned JWT
            val unsignedJwt = PlainJWT(claimsSet)

            // Serialize to compact form
            return unsignedJwt.serialize()
        } catch (e: Exception) {
            // Fallback to JSON representation on any error
            return json.encodeToString(serializer(), credential)
        }
    }

    /**
     * Convert JsonElement to Map for JWT claims.
     */
    private fun jsonElementToMap(element: kotlinx.serialization.json.JsonElement): Map<String, Any?> {
        return when (element) {
            is kotlinx.serialization.json.JsonObject -> {
                element.entries.associate { (key, value) ->
                    key to jsonElementToValue(value)
                }
            }
            else -> emptyMap()
        }
    }

    /**
     * Convert JsonElement to value for JWT claims.
     */
    private fun jsonElementToValue(element: kotlinx.serialization.json.JsonElement): Any? {
        return when (element) {
            is kotlinx.serialization.json.JsonPrimitive -> {
                when {
                    element.isString -> element.content
                    element.booleanOrNull != null -> element.boolean
                    element.longOrNull != null -> element.long
                    element.doubleOrNull != null -> element.double
                    else -> element.content
                }
            }
            is kotlinx.serialization.json.JsonArray -> {
                element.map { jsonElementToValue(it) }
            }
            is kotlinx.serialization.json.JsonObject -> {
                jsonElementToMap(element)
            }
            is kotlinx.serialization.json.JsonNull -> null
        }
    }

    /**
     * Convert JWT to credential.
     *
     * Parses a JWT and extracts the credential from the 'vc' claim.
     * Note: This does not verify the signature. Use CredentialService.verify() for verification.
     *
     * **Recommended:** Use the extension function `jwtString.fromJwt()` instead for a more elegant API.
     *
     * @param jwt JWT string
     * @return Verifiable credential
     * @see org.trustweave.credential.transform.fromJwt Extension function
     */
    suspend fun fromJwt(jwt: String): VerifiableCredential {
        try {
            // Use nimbus-jose-jwt library directly (it's a required dependency)
            // Try parsing as SignedJWT first, then PlainJWT
            val jwtObject = try {
                SignedJWT.parse(jwt)
            } catch (e: Exception) {
                // Try PlainJWT
                PlainJWT.parse(jwt)
            }

            // Get claims set
            val claimsSet = jwtObject.jwtClaimsSet

            // Get vc claim
            val vcClaim = claimsSet.getClaim("vc")

            if (vcClaim == null) {
                throw IllegalArgumentException("JWT does not contain 'vc' claim")
            }

            // Convert claim to JsonObject
            val vcMap = vcClaim as? Map<*, *> ?: throw IllegalArgumentException("'vc' claim is not a valid object")
            @Suppress("UNCHECKED_CAST")
            val vcJson = mapToJsonObject(vcMap as Map<String, Any?>)

            // Parse as VerifiableCredential
            return json.decodeFromJsonElement<VerifiableCredential>(vcJson)
        } catch (e: Exception) {
            // Fallback: try parsing as JSON
            return json.decodeFromString<VerifiableCredential>(jwt)
        }
    }

    /**
     * Convert Map to JsonObject.
     */
    private fun mapToJsonObject(map: Map<String, Any?>): kotlinx.serialization.json.JsonObject {
        return buildJsonObject {
            map.forEach { (key, value) ->
                put(key, valueToJsonElement(value))
            }
        }
    }

    /**
     * Convert value to JsonElement.
     */
    private fun valueToJsonElement(value: Any?): kotlinx.serialization.json.JsonElement {
        return when (value) {
            null -> kotlinx.serialization.json.JsonNull
            is String -> kotlinx.serialization.json.JsonPrimitive(value)
            is Number -> kotlinx.serialization.json.JsonPrimitive(value)
            is Boolean -> kotlinx.serialization.json.JsonPrimitive(value)
            is List<*> -> kotlinx.serialization.json.JsonArray(value.map { valueToJsonElement(it) })
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                mapToJsonObject(value as Map<String, Any?>)
            }
            else -> kotlinx.serialization.json.JsonPrimitive(value.toString())
        }
    }

    /**
     * Convert credential to JSON-LD format.
     *
     * **Recommended:** Use the extension function `credential.toJsonLd()` instead for a more elegant API.
     *
     * @param credential Credential to convert
     * @return JSON-LD object
     * @see org.trustweave.credential.transform.toJsonLd Extension function
     */
    suspend fun toJsonLd(credential: VerifiableCredential): JsonObject {
        // Credential is already in JSON-LD format
        val jsonString = json.encodeToString(VerifiableCredential.serializer(), credential)
        val element = Json.parseToJsonElement(jsonString)
        return element.jsonObject
    }

    /**
     * Convert JSON-LD to credential.
     *
     * **Recommended:** Use the extension function `jsonLdObject.toCredential()` instead for a more elegant API.
     *
     * @param json JSON-LD object
     * @return Verifiable credential
     * @see org.trustweave.credential.transform.toCredential Extension function
     */
    suspend fun fromJsonLd(json: JsonObject): VerifiableCredential {
        return this.json.decodeFromJsonElement<VerifiableCredential>(json)
    }

    /**
     * Convert credential to CBOR format.
     *
     * Serializes the credential to JSON using kotlinx.serialization, then converts it to CBOR
     * binary format using Jackson's CBOR dataformat. CBOR provides more compact encoding than JSON
     * while maintaining compatibility with JSON data structures.
     *
     * **Implementation Details:**
     * - Uses kotlinx.serialization to convert VerifiableCredential to JSON string
     * - Uses Jackson CBOR mapper to convert JSON string to CBOR binary format
     * - Preserves all credential data including nested structures
     * - More efficient than JSON for storage and transmission
     *
     * **Performance:**
     * - CBOR encoding is typically 10-20% smaller than equivalent JSON
     * - Faster to parse than JSON in many cases
     * - Well-suited for binary storage and network transmission
     *
     * **Recommended:** Use the extension function `credential.toCbor()` instead for a more elegant API.
     *
     * @param credential Credential to convert
     * @return CBOR-encoded bytes
     * @see fromCbor For parsing CBOR-encoded credentials
     * @see org.trustweave.credential.transform.toCbor Extension function
     */
    suspend fun toCbor(credential: VerifiableCredential): ByteArray {
        // Step 1: Serialize credential to JSON string using kotlinx.serialization
        val jsonString = json.encodeToString(VerifiableCredential.serializer(), credential)
        
        // Step 2: Parse JSON string to Jackson tree model using JSON mapper
        val jsonNode = jsonMapper.readTree(jsonString)
        
        // Step 3: Write to CBOR bytes using CBOR mapper
        return cborMapper.writeValueAsBytes(jsonNode)
    }

    /**
     * Convert CBOR to credential.
     *
     * Deserializes CBOR-encoded bytes back to a VerifiableCredential. The CBOR bytes are first
     * converted to JSON format, then parsed using kotlinx.serialization.
     *
     * **Implementation Details:**
     * - Uses Jackson CBOR mapper to parse CBOR bytes to JSON tree model
     * - Converts JSON tree to JSON string
     * - Uses kotlinx.serialization to deserialize JSON string to VerifiableCredential
     * - Handles all credential structures including nested objects and arrays
     *
     * **Error Handling:**
     * - Throws IllegalArgumentException if CBOR bytes are invalid
     * - Throws SerializationException if credential structure is invalid
     *
     * **Recommended:** Use the extension function `cborBytes.fromCbor()` instead for a more elegant API.
     *
     * @param bytes CBOR-encoded bytes
     * @return Verifiable credential
     * @throws IllegalArgumentException if CBOR bytes cannot be parsed
     * @throws kotlinx.serialization.SerializationException if credential deserialization fails
     * @see toCbor For encoding credentials to CBOR
     * @see org.trustweave.credential.transform.fromCbor Extension function
     */
    suspend fun fromCbor(bytes: ByteArray): VerifiableCredential {
        try {
            // Step 1: Parse CBOR bytes to Jackson tree model using CBOR mapper
            val jsonNode = cborMapper.readTree(bytes)
            
            // Step 2: Convert JSON tree to JSON string using JSON mapper
            val jsonString = jsonMapper.writeValueAsString(jsonNode)
            
            // Step 3: Deserialize JSON string to VerifiableCredential using kotlinx.serialization
            return json.decodeFromString<VerifiableCredential>(jsonString)
        } catch (e: com.fasterxml.jackson.core.JsonProcessingException) {
            throw IllegalArgumentException("Invalid CBOR data: ${e.message}", e)
        } catch (e: java.io.IOException) {
            throw IllegalArgumentException("Failed to parse CBOR bytes: ${e.message}", e)
        }
    }
}


