package com.trustweave.credential

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.transform.CredentialTransformer
import kotlinx.serialization.json.JsonObject

/**
 * Extension methods for CredentialService to support format transformations.
 * 
 * These extensions provide convenient access to credential format conversion
 * without requiring direct use of CredentialTransformer.
 */

/**
 * Convert credential to JWT format.
 * 
 * Creates an unsigned JWT with the credential in the 'vc' claim.
 * For signed JWTs, use [CredentialService.issue] with JWT format.
 * 
 * **Example:**
 * ```kotlin
 * val jwt = credentialService.toJwt(credential)
 * ```
 */
suspend fun CredentialService.toJwt(credential: VerifiableCredential): String {
    return CredentialTransformer().toJwt(credential)
}

/**
 * Convert JWT to credential.
 * 
 * Parses a JWT and extracts the credential from the 'vc' claim.
 * Note: This does not verify the signature. Use [CredentialService.verify] for verification.
 * 
 * **Example:**
 * ```kotlin
 * val credential = credentialService.fromJwt(jwt)
 * ```
 */
suspend fun CredentialService.fromJwt(jwt: String): VerifiableCredential {
    return CredentialTransformer().fromJwt(jwt)
}

/**
 * Convert credential to JSON-LD format.
 * 
 * **Example:**
 * ```kotlin
 * val jsonLd = credentialService.toJsonLd(credential)
 * ```
 */
suspend fun CredentialService.toJsonLd(credential: VerifiableCredential): JsonObject {
    return CredentialTransformer().toJsonLd(credential)
}

/**
 * Convert JSON-LD to credential.
 * 
 * **Example:**
 * ```kotlin
 * val credential = credentialService.fromJsonLd(jsonLd)
 * ```
 */
suspend fun CredentialService.fromJsonLd(json: JsonObject): VerifiableCredential {
    return CredentialTransformer().fromJsonLd(json)
}

/**
 * Convert credential to CBOR format.
 * 
 * **Note:** This is a placeholder implementation. Full CBOR support requires a CBOR library.
 * 
 * **Example:**
 * ```kotlin
 * val cbor = credentialService.toCbor(credential)
 * ```
 */
suspend fun CredentialService.toCbor(credential: VerifiableCredential): ByteArray {
    return CredentialTransformer().toCbor(credential)
}

/**
 * Convert CBOR to credential.
 * 
 * **Note:** This is a placeholder implementation. Full CBOR support requires a CBOR library.
 * 
 * **Example:**
 * ```kotlin
 * val credential = credentialService.fromCbor(cborBytes)
 * ```
 */
suspend fun CredentialService.fromCbor(bytes: ByteArray): VerifiableCredential {
    return CredentialTransformer().fromCbor(bytes)
}
