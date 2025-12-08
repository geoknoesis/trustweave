package com.trustweave.credential.transform

import com.trustweave.credential.model.vc.VerifiableCredential
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.*
import kotlinx.datetime.Instant as KotlinInstant
import java.time.Instant as JavaInstant

/**
 * Credential transformer interface.
 *
 * Provides format conversion between different credential representations:
 * - JSON-LD (default W3C VC format)
 * - JWT (compact, widely supported)
 * - CBOR (binary, efficient)
 *
 * **Example Usage**:
 * ```kotlin
 * val transformer = CredentialTransformer()
 *
 * // Convert to JWT
 * val jwt = transformer.toJwt(credential)
 *
 * // Convert from JWT
 * val credential = transformer.fromJwt(jwt)
 * ```
 */
class CredentialTransformer {
    private val json = Json {
        prettyPrint = false
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    /**
     * Convert credential to JWT format.
     *
     * Creates a JWT with the credential in the 'vc' claim.
     * Note: This creates an unsigned JWT. For signed JWTs, use a ProofGenerator.
     *
     * @param credential Credential to convert
     * @return JWT string (unsigned)
     */
    suspend fun toJwt(credential: VerifiableCredential): String {
        try {
            // Use reflection to create JWT if nimbus-jose-jwt is available
            val jwtClaimsSetClass = Class.forName("com.nimbusds.jwt.JWTClaimsSet")
            val builderClass = Class.forName("com.nimbusds.jwt.JWTClaimsSet\$Builder")
            val builder = builderClass.getDeclaredConstructor().newInstance()

            // Set issuer
            val setIssuerMethod = builderClass.getMethod("issuer", String::class.java)
            setIssuerMethod.invoke(builder, credential.issuer)

            // Set issued at
            val issuedAt = credential.issuanceDate.let {
                KotlinInstant.parse(it).epochSeconds
            }
            val setIssuedAtMethod = builderClass.getMethod("issueTime", java.util.Date::class.java)
            setIssuedAtMethod.invoke(builder, java.util.Date.from(JavaInstant.ofEpochSecond(issuedAt)))

            // Set expiration if present
            credential.expirationDate?.let { expirationDate ->
                val expiration = KotlinInstant.parse(expirationDate).epochSeconds
                val setExpirationMethod = builderClass.getMethod("expirationTime", java.util.Date::class.java)
                setExpirationMethod.invoke(builder, java.util.Date.from(JavaInstant.ofEpochSecond(expiration)))
            }

            // Set jti (credential ID)
            credential.id?.let { id ->
                val setJtiMethod = builderClass.getMethod("jwtID", String::class.java)
                setJtiMethod.invoke(builder, id)
            }

            // Add vc claim with credential
            val credentialJson = json.encodeToJsonElement(VerifiableCredential.serializer(), credential)
            val credentialMap = jsonElementToMap(credentialJson)
            val setClaimMethod = builderClass.getMethod("claim", String::class.java, Any::class.java)
            setClaimMethod.invoke(builder, "vc", credentialMap)

            // Build claims set
            val buildMethod = builderClass.getMethod("build")
            val claimsSet = buildMethod.invoke(builder)

            // Create unsigned JWT
            val unsignedJwtClass = Class.forName("com.nimbusds.jwt.PlainJWT")
            val unsignedJwtConstructor = unsignedJwtClass.getConstructor(jwtClaimsSetClass)
            val unsignedJwt = unsignedJwtConstructor.newInstance(claimsSet)

            // Serialize to compact form
            val serializeMethod = unsignedJwtClass.getMethod("serialize")
            return serializeMethod.invoke(unsignedJwt) as String
        } catch (e: ClassNotFoundException) {
            // If nimbus-jose-jwt is not available, return JSON representation
            return json.encodeToString(VerifiableCredential.serializer(), credential)
        } catch (e: Exception) {
            // Fallback to JSON representation
            return json.encodeToString(VerifiableCredential.serializer(), credential)
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
            else -> null
        }
    }

    /**
     * Convert JWT to credential.
     *
     * Parses a JWT and extracts the credential from the 'vc' claim.
     * Note: This does not verify the signature. Use CredentialVerifier for verification.
     *
     * @param jwt JWT string
     * @return Verifiable credential
     */
    suspend fun fromJwt(jwt: String): VerifiableCredential {
        try {
            // Use reflection to parse JWT if nimbus-jose-jwt is available
            val signedJwtClass = Class.forName("com.nimbusds.jwt.SignedJWT")
            val plainJwtClass = Class.forName("com.nimbusds.jwt.PlainJWT")

            // Try parsing as SignedJWT first
            val jwtObject = try {
                val parseMethod = signedJwtClass.getMethod("parse", String::class.java)
                parseMethod.invoke(null, jwt)
            } catch (e: Exception) {
                // Try PlainJWT
                val parseMethod = plainJwtClass.getMethod("parse", String::class.java)
                parseMethod.invoke(null, jwt)
            }

            // Get claims set
            val getJwtClaimsSetMethod = jwtObject.javaClass.getMethod("getJWTClaimsSet")
            val claimsSet = getJwtClaimsSetMethod.invoke(jwtObject)

            // Get vc claim
            val getClaimMethod = claimsSet.javaClass.getMethod("getClaim", String::class.java)
            val vcClaim = getClaimMethod.invoke(claimsSet, "vc")

            if (vcClaim == null) {
                throw IllegalArgumentException("JWT does not contain 'vc' claim")
            }

            // Convert claim to JsonObject
            val vcMap = vcClaim as? Map<*, *> ?: throw IllegalArgumentException("'vc' claim is not a valid object")
            val vcJson = mapToJsonObject(vcMap as Map<String, Any?>)

            // Parse as VerifiableCredential
            return json.decodeFromJsonElement(VerifiableCredential.serializer(), vcJson)
        } catch (e: ClassNotFoundException) {
            // If nimbus-jose-jwt is not available, try parsing as JSON
            return json.decodeFromString(VerifiableCredential.serializer(), jwt)
        } catch (e: Exception) {
            // Fallback: try parsing as JSON
            return json.decodeFromString(VerifiableCredential.serializer(), jwt)
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
            is Map<*, *> -> mapToJsonObject(value as Map<String, Any?>)
            else -> kotlinx.serialization.json.JsonPrimitive(value.toString())
        }
    }

    /**
     * Convert credential to JSON-LD format.
     *
     * @param credential Credential to convert
     * @return JSON-LD object
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
     * @param json JSON-LD object
     * @return Verifiable credential
     */
    suspend fun fromJsonLd(json: JsonObject): VerifiableCredential {
        return this.json.decodeFromJsonElement(VerifiableCredential.serializer(), json)
    }

    /**
     * Convert credential to CBOR format.
     *
     * @param credential Credential to convert
     * @return CBOR bytes
     */
    suspend fun toCbor(credential: VerifiableCredential): ByteArray {
        // TODO: Implement CBOR conversion
        // Requires CBOR library (e.g., co.nstant.in:cbor)
        // 1. Convert credential to CBOR representation
        // 2. Return CBOR bytes

        // Placeholder: return JSON bytes
        val jsonString = json.encodeToString(VerifiableCredential.serializer(), credential)
        return jsonString.toByteArray(Charsets.UTF_8)
    }

    /**
     * Convert CBOR to credential.
     *
     * @param bytes CBOR bytes
     * @return Verifiable credential
     */
    suspend fun fromCbor(bytes: ByteArray): VerifiableCredential {
        // TODO: Implement CBOR parsing
        // 1. Parse CBOR bytes
        // 2. Convert to JSON
        // 3. Parse as VerifiableCredential

        // Placeholder: parse as JSON
        val jsonString = String(bytes, Charsets.UTF_8)
        return json.decodeFromString(VerifiableCredential.serializer(), jsonString)
    }
}

