package org.trustweave.credential.results

import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.model.vc.VerifiableCredential
import kotlinx.serialization.json.JsonElement

/**
 * Result of credential issuance operation.
 * 
 * Sealed hierarchy for exhaustive, type-safe error handling.
 * 
 * **Usage:**
 * ```kotlin
 * when (val result = service.issue(request)) {
 *     is IssuanceResult.Success -> {
 *         val envelope = result.envelope
 *         // Use credential
 *     }
 *     is IssuanceResult.Failure.UnsupportedFormat -> {
 *         // Handle unsupported format
 *     }
 *     // Compiler ensures all cases handled
 * }
 * ```
 */
sealed class IssuanceResult {
    /**
     * Issuance succeeded.
     */
    data class Success(
        val credential: VerifiableCredential
    ) : IssuanceResult()
    
    /**
     * Issuance failed.
     */
    sealed class Failure : IssuanceResult() {
        abstract val errors: List<String>
        abstract val warnings: List<String>
        
        /**
         * Format not supported (no adapter available).
         */
        data class UnsupportedFormat(
            val format: ProofSuiteId,
            val supportedFormats: List<ProofSuiteId> = emptyList(),
            override val errors: List<String> = emptyList(),
            override val warnings: List<String> = emptyList()
        ) : Failure() {
            constructor(
                format: ProofSuiteId,
                supportedFormats: List<ProofSuiteId> = emptyList()
            ) : this(
                format = format,
                supportedFormats = supportedFormats,
                errors = if (supportedFormats.isEmpty()) {
                    listOf("Proof suite '${format.value}' is not supported. No proof suites available.")
                } else {
                    listOf(
                        "Proof suite '${format.value}' is not supported. " +
                        "Supported proof suites: ${supportedFormats.joinToString(", ") { it.value }}"
                    )
                },
                warnings = emptyList()
            )
        }
        
        /**
         * Adapter not ready (not initialized).
         */
        data class AdapterNotReady(
            val format: ProofSuiteId,
            val reason: String? = null,
            override val errors: List<String> = emptyList(),
            override val warnings: List<String> = emptyList()
        ) : Failure() {
            constructor(
                format: ProofSuiteId,
                reason: String? = null
            ) : this(
                format = format,
                reason = reason,
                errors = listOf(
                    "Adapter for format '${format.value}' is not ready${reason?.let { ": $it" } ?: ""}"
                ),
                warnings = emptyList()
            )
        }
        
        /**
         * Request validation failed.
         */
        data class InvalidRequest(
            val field: String,
            val reason: String,
            override val errors: List<String> = emptyList(),
            override val warnings: List<String> = emptyList()
        ) : Failure() {
            constructor(
                field: String,
                reason: String
            ) : this(
                field = field,
                reason = reason,
                errors = listOf("Invalid request: field '$field' - $reason"),
                warnings = emptyList()
            )
        }
        
        /**
         * Issuance failed due to adapter error.
         */
        data class AdapterError(
            val format: ProofSuiteId,
            val reason: String,
            val cause: Throwable? = null,
            override val errors: List<String> = emptyList(),
            override val warnings: List<String> = emptyList(),
            val formatSpecificError: JsonElement? = null
        ) : Failure() {
            constructor(
                format: ProofSuiteId,
                reason: String,
                cause: Throwable? = null
            ) : this(
                format = format,
                reason = reason,
                cause = cause,
                errors = listOf("Issuance failed for format '${format.value}': $reason"),
                warnings = emptyList(),
                formatSpecificError = null
            )
        }
        
        /**
         * Multiple validation failures.
         */
        data class MultipleFailures(
            val failures: List<Failure>,
            override val errors: List<String> = emptyList(),
            override val warnings: List<String> = emptyList()
        ) : Failure() {
            init {
                require(failures.isNotEmpty()) { "MultipleFailures requires at least one failure" }
            }
            
            constructor(
                failures: List<Failure>
            ) : this(
                failures = failures,
                errors = failures.flatMap { it.errors },
                warnings = failures.flatMap { it.warnings }
            )
        }
    }
    
    /**
     * True if issuance succeeded.
     */
    val isSuccess: Boolean
        get() = this is Success
    
    /**
     * True if issuance failed.
     */
    val isFailure: Boolean
        get() = this is Failure
    
    /**
     * Get the credential if successful, otherwise null.
     */
    val credentialOrNull: VerifiableCredential?
        get() = (this as? Success)?.credential
    
    /**
     * Get all errors (flattened for nested failures).
     */
    val allErrors: List<String>
        get() = when (this) {
            is Success -> emptyList()
            is Failure.UnsupportedFormat -> errors
            is Failure.AdapterNotReady -> errors
            is Failure.InvalidRequest -> errors
            is Failure.AdapterError -> errors
            is Failure.MultipleFailures -> failures.flatMap { it.allErrors } + errors
        }
    
    /**
     * Get all warnings.
     */
    val allWarnings: List<String>
        get() = when (this) {
            is Success -> emptyList()  // Success doesn't have warnings
            is Failure -> warnings
        }
}

/**
 * Extension function for fluent result handling.
 */
inline fun <T> IssuanceResult.onSuccess(action: (VerifiableCredential) -> Unit): IssuanceResult {
    if (this is IssuanceResult.Success) action(credential)
    return this
}

inline fun <T> IssuanceResult.onFailure(action: (IssuanceResult.Failure) -> Unit): IssuanceResult {
    if (this is IssuanceResult.Failure) action(this)
    return this
}

/**
 * Get value or throw exception.
 */
fun IssuanceResult.getOrThrow(): VerifiableCredential {
    return when (this) {
        is IssuanceResult.Success -> credential
        is IssuanceResult.Failure -> throw IllegalArgumentException(
            "Credential issuance failed: ${allErrors.joinToString("; ")}"
        )
    }
}

/**
 * Get value or null.
 */
fun IssuanceResult.getOrNull(): VerifiableCredential? = credentialOrNull

/**
 * Map over success value.
 */
inline fun <R> IssuanceResult.map(transform: (VerifiableCredential) -> VerifiableCredential): IssuanceResult {
    return when (this) {
        is IssuanceResult.Success -> IssuanceResult.Success(transform(credential))
        is IssuanceResult.Failure -> this
    }
}

/**
 * Fold (pattern matching as expression).
 */
inline fun <R> IssuanceResult.fold(
    onFailure: (IssuanceResult.Failure) -> R,
    onSuccess: (VerifiableCredential) -> R
): R {
    return when (this) {
        is IssuanceResult.Success -> onSuccess(credential)
        is IssuanceResult.Failure -> onFailure(this)
    }
}

