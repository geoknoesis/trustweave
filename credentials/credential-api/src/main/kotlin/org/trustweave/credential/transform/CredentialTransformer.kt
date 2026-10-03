package org.trustweave.credential.transform

import kotlinx.serialization.json.JsonObject
import org.trustweave.credential.model.vc.VerifiableCredential

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
 * This public class is a thin facade over
 * [org.trustweave.credential.internal.transform.CredentialTransformer], so both entry points
 * share one implementation (and one set of security rules).
 *
 * @see org.trustweave.credential.transform.CredentialTransformerExtensions For extension functions
 * @see org.trustweave.credential.transform.CredentialTransformationBuilder For DSL builder
 */
class CredentialTransformer {
    private val delegate =
        org.trustweave.credential.internal.transform
            .CredentialTransformer()

    /**
     * Convert credential to an *unsecured* JWT (`alg: none`) with the credential in the `vc`
     * claim. For signed JWTs, use `CredentialService.issue()`.
     *
     * @throws IllegalStateException if the credential cannot be encoded; there is no fallback
     *         to another representation.
     */
    suspend fun toJwt(credential: VerifiableCredential): String = delegate.toJwt(credential)

    /**
     * Convert a JWS-secured JWT to a credential (signature **not** verified). Unsecured
     * (`alg: none`) JWTs are rejected; see the overload with `allowUnsecured`.
     *
     * @throws IllegalArgumentException if the input is not a secured JWT with a valid `vc` claim.
     * @see org.trustweave.credential.transform.fromJwt Extension function
     */
    suspend fun fromJwt(jwt: String): VerifiableCredential = delegate.fromJwt(jwt)

    /**
     * Convert a JWT to a credential, accepting unsecured (`alg: none`) JWTs only when
     * [allowUnsecured] is `true` — e.g. to read back the output of [toJwt].
     */
    suspend fun fromJwt(
        jwt: String,
        allowUnsecured: Boolean,
    ): VerifiableCredential = delegate.fromJwt(jwt, allowUnsecured)

    /** Convert credential to JSON-LD format. */
    suspend fun toJsonLd(credential: VerifiableCredential): JsonObject = delegate.toJsonLd(credential)

    /** Convert JSON-LD to credential. */
    suspend fun fromJsonLd(json: JsonObject): VerifiableCredential = delegate.fromJsonLd(json)

    /** Convert credential to CBOR (RFC 8949) bytes. */
    suspend fun toCbor(credential: VerifiableCredential): ByteArray = delegate.toCbor(credential)

    /**
     * Convert CBOR bytes to credential.
     *
     * @throws IllegalArgumentException if CBOR bytes cannot be parsed
     */
    suspend fun fromCbor(bytes: ByteArray): VerifiableCredential = delegate.fromCbor(bytes)
}
