package org.trustweave.credential.internal.transform

import org.trustweave.credential.model.vc.VerifiableCredential
import kotlinx.serialization.json.JsonObject

/**
 * Result of a credential transformation.
 *
 * Models the concrete output shapes produced by [CredentialTransformationBuilder] as a
 * type-safe sealed hierarchy, so callers can exhaustively pattern-match on the produced
 * format instead of relying on unchecked casts (`as String` / `as JsonObject` / `as ByteArray`).
 */
sealed interface TransformResult {
    /** Result of a transformation to compact JWT form. */
    data class Jwt(val value: String) : TransformResult

    /** Result of a transformation to W3C JSON-LD form. */
    data class JsonLd(val value: JsonObject) : TransformResult

    /** Result of a transformation to CBOR binary form. */
    data class Cbor(val value: ByteArray) : TransformResult {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Cbor) return false
            return value.contentEquals(other.value)
        }

        override fun hashCode(): Int = value.contentHashCode()
    }
}

/**
 * DSL builder for credential transformations.
 *
 * Provides a fluent, builder-style API for credential format conversions.
 * This is useful for complex transformation scenarios or when you want
 * to use a builder pattern for consistency with other TrustWeave DSLs.
 *
 * **Example Usage:**
 * ```kotlin
 * // Simple transformation
 * val jwt = credential.transform {
 *     toJwt()
 * }
 *
 * // Pattern-match on the typed result
 * when (val result = credential.transform { toJwt() }) {
 *     is TransformResult.Jwt -> println(result.value)
 *     is TransformResult.JsonLd -> println(result.value)
 *     is TransformResult.Cbor -> println(result.value.size)
 * }
 *
 * // JSON-LD transformation
 * val jsonLd = (credential.transform { toJsonLd() } as TransformResult.JsonLd).value
 *
 * // CBOR transformation
 * val cbor = (credential.transform { toCbor() } as TransformResult.Cbor).value
 * ```
 *
 * **Note:** For most use cases, the direct extension functions are more elegant:
 * - `credential.toJwt()` instead of `credential.transform { toJwt() }`
 * - `credential.toJsonLd()` instead of `credential.transform { toJsonLd() }`
 *
 * Use this builder when you need conditional transformations or want to
 * integrate with other builder-based DSLs.
 */
class CredentialTransformationBuilder(
    private val credential: VerifiableCredential,
    private val transformer: CredentialTransformer = CredentialTransformer()
) {
    private var result: TransformResult? = null

    /**
     * Transform credential to JWT format.
     *
     * @return JWT string
     */
    suspend fun toJwt(): String {
        val jwt = transformer.toJwt(credential)
        result = TransformResult.Jwt(jwt)
        return jwt
    }

    /**
     * Transform credential to JSON-LD format.
     *
     * @return JSON-LD object
     */
    suspend fun toJsonLd(): JsonObject {
        val jsonLd = transformer.toJsonLd(credential)
        result = TransformResult.JsonLd(jsonLd)
        return jsonLd
    }

    /**
     * Transform credential to CBOR format.
     *
     * @return CBOR-encoded bytes
     */
    suspend fun toCbor(): ByteArray {
        val cbor = transformer.toCbor(credential)
        result = TransformResult.Cbor(cbor)
        return cbor
    }

    /**
     * Get the transformation result.
     *
     * @return The typed result of the last transformation, or `null` if no transformation
     *   has been performed yet.
     */
    fun getResult(): TransformResult? = result
}

/**
 * Transform credential using DSL builder.
 *
 * **Example:**
 * ```kotlin
 * val result = credential.transform {
 *     toJwt()
 * }
 * val jwt = (result as TransformResult.Jwt).value
 * ```
 *
 * @param block DSL block for specifying transformation; the value it produces determines
 *   the returned [TransformResult] variant.
 * @return The typed result of the transformation.
 * @throws IllegalStateException if the block does not perform a transformation.
 */
suspend fun VerifiableCredential.transform(
    block: suspend CredentialTransformationBuilder.() -> Unit
): TransformResult {
    val builder = CredentialTransformationBuilder(this)
    builder.block()
    return builder.getResult()
        ?: error("transform { } block did not perform a transformation (call toJwt(), toJsonLd(), or toCbor())")
}
