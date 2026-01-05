package org.trustweave.credential.transform

import org.trustweave.credential.model.vc.VerifiableCredential
import kotlinx.serialization.json.JsonObject

/**
 * Extension functions for VerifiableCredential format transformation.
 * 
 * Provides fluent, DSL-like API for credential format conversion.
 * These extensions make credential transformations more elegant and idiomatic.
 * 
 * **Example Usage:**
 * ```kotlin
 * // Convert to JWT
 * val jwt = credential.toJwt()
 * 
 * // Convert to JSON-LD
 * val jsonLd = credential.toJsonLd()
 * 
 * // Convert to CBOR
 * val cbor = credential.toCbor()
 * 
 * // Chain transformations
 * val roundTrip = credential
 *     .toJwt()
 *     .fromJwt()
 *     .toCbor()
 *     .fromCbor()
 * ```
 * 
 * **Round-trip Testing:**
 * ```kotlin
 * // Verify transformations preserve data
 * val recovered = credential.roundTripJwt()
 * assertEquals(credential.id, recovered.id)
 * ```
 */

// Shared transformer instance for efficiency
private val defaultTransformer = CredentialTransformer()

/**
 * Convert credential to JWT format.
 * 
 * Creates an unsigned JWT with the credential in the 'vc' claim.
 * For signed JWTs, use CredentialService.issue() with JWT format.
 * 
 * **Example:**
 * ```kotlin
 * val jwt = credential.toJwt()
 * println("JWT: $jwt")
 * ```
 * 
 * @return JWT string (unsigned)
 */
suspend fun VerifiableCredential.toJwt(): String {
    return defaultTransformer.toJwt(this)
}

/**
 * Convert credential to JSON-LD format.
 * 
 * Returns the credential as a JSON-LD object (JsonObject).
 * 
 * **Example:**
 * ```kotlin
 * val jsonLd = credential.toJsonLd()
 * println("JSON-LD: ${jsonLd.toString()}")
 * ```
 * 
 * @return JSON-LD object
 */
suspend fun VerifiableCredential.toJsonLd(): JsonObject {
    return defaultTransformer.toJsonLd(this)
}

/**
 * Convert credential to CBOR format.
 * 
 * Serializes the credential to compact binary CBOR format.
 * CBOR provides more efficient encoding than JSON.
 * 
 * **Example:**
 * ```kotlin
 * val cbor = credential.toCbor()
 * println("CBOR size: ${cbor.size} bytes")
 * ```
 * 
 * @return CBOR-encoded bytes
 */
suspend fun VerifiableCredential.toCbor(): ByteArray {
    return defaultTransformer.toCbor(this)
}

/**
 * Convert JWT string to credential.
 * 
 * Parses a JWT and extracts the credential from the 'vc' claim.
 * Note: This does not verify the signature. Use CredentialService.verify() for verification.
 * 
 * **Example:**
 * ```kotlin
 * val credential = jwtString.fromJwt()
 * println("Credential ID: ${credential.id}")
 * ```
 * 
 * @return Verifiable credential
 * @throws IllegalArgumentException if JWT is invalid or missing 'vc' claim
 */
suspend fun String.fromJwt(): VerifiableCredential {
    return defaultTransformer.fromJwt(this)
}

/**
 * Convert JSON-LD object to credential.
 * 
 * Parses a JSON-LD object and converts it to a VerifiableCredential.
 * 
 * **Example:**
 * ```kotlin
 * val credential = jsonLdObject.toCredential()
 * println("Issuer: ${credential.issuer}")
 * ```
 * 
 * @return Verifiable credential
 * @throws kotlinx.serialization.SerializationException if JSON-LD is invalid
 */
suspend fun JsonObject.toCredential(): VerifiableCredential {
    return defaultTransformer.fromJsonLd(this)
}

/**
 * Convert CBOR bytes to credential.
 * 
 * Deserializes CBOR-encoded bytes back to a VerifiableCredential.
 * 
 * **Example:**
 * ```kotlin
 * val credential = cborBytes.fromCbor()
 * println("Credential: ${credential.id}")
 * ```
 * 
 * @return Verifiable credential
 * @throws IllegalArgumentException if CBOR bytes are invalid
 */
suspend fun ByteArray.fromCbor(): VerifiableCredential {
    return defaultTransformer.fromCbor(this)
}

/**
 * Round-trip transformation: credential -> JWT -> credential.
 * 
 * Useful for testing that JWT transformation preserves all credential data.
 * 
 * **Example:**
 * ```kotlin
 * val recovered = credential.roundTripJwt()
 * assertEquals(credential.id, recovered.id)
 * assertEquals(credential.issuer, recovered.issuer)
 * ```
 * 
 * @return Credential recovered from JWT transformation
 */
suspend fun VerifiableCredential.roundTripJwt(): VerifiableCredential {
    return this.toJwt().fromJwt()
}

/**
 * Round-trip transformation: credential -> CBOR -> credential.
 * 
 * Useful for testing that CBOR transformation preserves all credential data.
 * 
 * **Example:**
 * ```kotlin
 * val recovered = credential.roundTripCbor()
 * assertEquals(credential.id, recovered.id)
 * ```
 * 
 * @return Credential recovered from CBOR transformation
 */
suspend fun VerifiableCredential.roundTripCbor(): VerifiableCredential {
    return this.toCbor().fromCbor()
}

/**
 * Round-trip transformation: credential -> JSON-LD -> credential.
 * 
 * Useful for testing that JSON-LD transformation preserves all credential data.
 * 
 * **Example:**
 * ```kotlin
 * val recovered = credential.roundTripJsonLd()
 * assertEquals(credential.id, recovered.id)
 * ```
 * 
 * @return Credential recovered from JSON-LD transformation
 */
suspend fun VerifiableCredential.roundTripJsonLd(): VerifiableCredential {
    return this.toJsonLd().toCredential()
}

