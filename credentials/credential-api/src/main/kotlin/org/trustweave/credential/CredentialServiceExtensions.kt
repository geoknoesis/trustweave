package org.trustweave.credential

import kotlinx.serialization.json.JsonObject
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.transform.CredentialTransformer

/**
 * Extension methods for CredentialService to support format transformations.
 *
 * These extensions provide convenient access to credential format conversion
 * without requiring direct use of CredentialTransformer.
 *
 * **Recommended Usage:**
 * For the most elegant API, use extension functions directly on VerifiableCredential:
 * ```kotlin
 * // Recommended (most elegant)
 * val jwt = credential.toJwt()
 * val jsonLd = credential.toJsonLd()
 * val cbor = credential.toCbor()
 *
 * // Alternative (CredentialService extensions)
 * val jwt = credentialService.toJwt(credential)
 * val jsonLd = credentialService.toJsonLd(credential)
 * ```
 *
 * **See Also:**
 * - [org.trustweave.credential.transform.CredentialTransformerExtensions] for extension functions on VerifiableCredential
 */

// Shared transformer instance for efficiency
private val sharedTransformer = CredentialTransformer()

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
suspend fun CredentialService.toJwt(credential: VerifiableCredential): String = sharedTransformer.toJwt(credential)

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
suspend fun CredentialService.fromJwt(jwt: String): VerifiableCredential = sharedTransformer.fromJwt(jwt)

/**
 * Convert credential to JSON-LD format.
 *
 * **Example:**
 * ```kotlin
 * val jsonLd = credentialService.toJsonLd(credential)
 * ```
 */
suspend fun CredentialService.toJsonLd(credential: VerifiableCredential): JsonObject = sharedTransformer.toJsonLd(credential)

/**
 * Convert JSON-LD to credential.
 *
 * **Example:**
 * ```kotlin
 * val credential = credentialService.fromJsonLd(jsonLd)
 * ```
 */
suspend fun CredentialService.fromJsonLd(json: JsonObject): VerifiableCredential = sharedTransformer.fromJsonLd(json)

/**
 * Convert credential to CBOR format.
 *
 * Backed by Jackson's CBOR mapper via the shared transformer.
 *
 * **Example:**
 * ```kotlin
 * val cbor = credentialService.toCbor(credential)
 * ```
 */
suspend fun CredentialService.toCbor(credential: VerifiableCredential): ByteArray = sharedTransformer.toCbor(credential)

/**
 * Convert CBOR to credential.
 *
 * Backed by Jackson's CBOR mapper via the shared transformer.
 *
 * **Example:**
 * ```kotlin
 * val credential = credentialService.fromCbor(cborBytes)
 * ```
 */
suspend fun CredentialService.fromCbor(bytes: ByteArray): VerifiableCredential = sharedTransformer.fromCbor(bytes)
