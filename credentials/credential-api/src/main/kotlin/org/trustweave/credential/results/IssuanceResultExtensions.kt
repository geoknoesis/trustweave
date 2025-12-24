package org.trustweave.credential.results

import org.trustweave.credential.model.vc.VerifiableCredential

/**
 * Additional convenience extensions for IssuanceResult.
 */

/**
 * Get credential or return early with failure handling.
 * 
 * Useful for early return pattern in functions.
 * 
 * **Example:**
 * ```kotlin
 * val credential = result.getOrReturn {
 *     log.error("Failed: ${it.allErrors}")
 *     return@function
 * } ?: return
 * ```
 */
inline fun <R> IssuanceResult.getOrReturn(
    onFailure: (IssuanceResult.Failure) -> R
): VerifiableCredential? = when (this) {
    is IssuanceResult.Success -> credential
    is IssuanceResult.Failure -> {
        onFailure(this)
        null
    }
}

/**
 * Require success, throwing with detailed error message on failure.
 * 
 * Useful for tests or when failure should be exceptional.
 * 
 * **Example:**
 * ```kotlin
 * val credential = result.requireSuccess()  // Throws if failed
 * ```
 */
fun IssuanceResult.requireSuccess(): VerifiableCredential {
    return when (this) {
        is IssuanceResult.Success -> credential
        is IssuanceResult.Failure -> throw IllegalStateException(
            "Credential issuance failed: ${allErrors.joinToString("; ")}"
        )
    }
}

/**
 * Get credential or use default value.
 * 
 * **Example:**
 * ```kotlin
 * val credential = result.getOrElse { createDefaultCredential() }
 * ```
 */
inline fun IssuanceResult.getOrElse(defaultValue: () -> VerifiableCredential): VerifiableCredential {
    return when (this) {
        is IssuanceResult.Success -> credential
        is IssuanceResult.Failure -> defaultValue()
    }
}

/**
 * Alias for credentialOrNull for more natural property access.
 */
val IssuanceResult.credential: VerifiableCredential?
    get() = credentialOrNull

