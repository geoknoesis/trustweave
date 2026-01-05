package org.trustweave.credential.transform

import org.trustweave.credential.model.vc.VerifiableCredential
import kotlinx.serialization.json.JsonObject

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
 * // With type casting
 * val jwt = credential.transform { toJwt() } as String
 * 
 * // JSON-LD transformation
 * val jsonLd = credential.transform { toJsonLd() } as JsonObject
 * 
 * // CBOR transformation
 * val cbor = credential.transform { toCbor() } as ByteArray
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
    private var result: Any? = null
    
    /**
     * Transform credential to JWT format.
     * 
     * @return JWT string
     */
    suspend fun toJwt(): String {
        result = transformer.toJwt(credential)
        return result as String
    }
    
    /**
     * Transform credential to JSON-LD format.
     * 
     * @return JSON-LD object
     */
    suspend fun toJsonLd(): JsonObject {
        result = transformer.toJsonLd(credential)
        return result as JsonObject
    }
    
    /**
     * Transform credential to CBOR format.
     * 
     * @return CBOR-encoded bytes
     */
    suspend fun toCbor(): ByteArray {
        result = transformer.toCbor(credential)
        return result as ByteArray
    }
    
    /**
     * Get the transformation result.
     * 
     * @return The result of the transformation (type depends on which method was called)
     */
    fun getResult(): Any? = result
}

/**
 * Transform credential using DSL builder.
 * 
 * **Example:**
 * ```kotlin
 * val jwt = credential.transform {
 *     toJwt()
 * }
 * ```
 * 
 * @param block DSL block for specifying transformation
 * @return The result of the transformation
 */
suspend fun VerifiableCredential.transform(
    block: suspend CredentialTransformationBuilder.() -> Any
): Any {
    return CredentialTransformationBuilder(this).block()
}

