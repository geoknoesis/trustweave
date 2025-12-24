package org.trustweave.credential.results

import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.core.identifiers.Iri
import org.trustweave.did.identifiers.Did
import kotlinx.serialization.json.JsonElement
import kotlinx.datetime.Instant

/**
 * Result of credential or presentation verification.
 * 
 * Sealed hierarchy for exhaustive, type-safe error handling.
 * 
 * **Usage:**
 * ```kotlin
 * when (val result = service.verify(credential)) {
 *     is VerificationResult.Valid -> {
 *         trustGraph.add(result.credential, result)
 *     }
 *     is VerificationResult.Invalid.Expired -> {
 *         refreshCredential(result.credential)
 *     }
 *     // Compiler ensures all cases handled
 * }
 * ```
 */
sealed class VerificationResult {
    /**
     * Get the credential from any result type.
     */
    abstract val credential: VerifiableCredential
    
    /**
     * Verification succeeded.
     */
    data class Valid(
        override val credential: VerifiableCredential,
        val issuerIri: Iri,  // Issuer IRI (typically DID, but can be URI/URN)
        val subjectIri: Iri?,  // Subject IRI (typically DID, but can be URI/URN)
        val issuedAt: Instant,
        val expiresAt: Instant?,
        val warnings: List<String> = emptyList(),
        val formatMetadata: Map<String, JsonElement> = emptyMap()
    ) : VerificationResult() {
        /**
         * Convenience: Get issuer as DID if it is a DID.
         */
        val issuerDid: Did?
            get() = if (issuerIri.isDid) try { Did(issuerIri.value) } catch (e: IllegalArgumentException) { null } else null
        
        /**
         * Convenience: Get subject as DID if it is a DID.
         */
        val subjectDid: Did?
            get() = subjectIri?.let { if (it.isDid) try { Did(it.value) } catch (e: IllegalArgumentException) { null } else null }
    }
    
    /**
     * Verification failed.
     */
    sealed class Invalid : VerificationResult() {
        abstract override val credential: VerifiableCredential
        abstract val errors: List<String>
        abstract val warnings: List<String>
        
        /**
         * Cryptographic proof verification failed.
         */
        data class InvalidProof(
            override val credential: VerifiableCredential,
            val reason: String,
            override val errors: List<String> = emptyList(),
            override val warnings: List<String> = emptyList(),
            val formatSpecificError: JsonElement? = null
        ) : Invalid()
        
        /**
         * Credential has expired.
         */
        data class Expired(
            override val credential: VerifiableCredential,
            val expiredAt: Instant,
            override val errors: List<String> = emptyList(),
            override val warnings: List<String> = emptyList()
        ) : Invalid()
        
        /**
         * Credential not yet valid (validFrom in future).
         */
        data class NotYetValid(
            override val credential: VerifiableCredential,
            val validFrom: Instant,
            override val errors: List<String> = emptyList(),
            override val warnings: List<String> = emptyList()
        ) : Invalid()
        
        /**
         * Credential has been revoked.
         */
        data class Revoked(
            override val credential: VerifiableCredential,
            val revokedAt: Instant?,
            override val errors: List<String> = emptyList(),
            override val warnings: List<String> = emptyList(),
            val revocationReason: String? = null
        ) : Invalid()
        
        /**
         * Issuer DID could not be resolved or is invalid.
         */
        data class InvalidIssuer(
            override val credential: VerifiableCredential,
            val issuerIri: Iri,
            val reason: String,
            override val errors: List<String> = emptyList(),
            override val warnings: List<String> = emptyList()
        ) : Invalid()
        
        /**
         * Issuer is not trusted according to the trust policy.
         */
        data class UntrustedIssuer(
            override val credential: VerifiableCredential,
            val issuerDid: Did,
            override val errors: List<String> = emptyList(),
            override val warnings: List<String> = emptyList()
        ) : Invalid() {
            constructor(
                credential: VerifiableCredential,
                issuerDid: Did
            ) : this(
                credential = credential,
                issuerDid = issuerDid,
                errors = listOf("Issuer ${issuerDid.value} is not trusted according to trust policy"),
                warnings = emptyList()
            )
        }
        
        /**
         * Format not supported (no adapter available).
         */
        data class UnsupportedFormat(
            override val credential: VerifiableCredential,
            val format: ProofSuiteId,
            override val errors: List<String> = emptyList(),
            override val warnings: List<String> = emptyList()
        ) : Invalid()
        
        /**
         * Schema validation failed.
         */
        data class SchemaValidationFailed(
            override val credential: VerifiableCredential,
            val schemaId: String?,
            val validationErrors: List<String>,
            override val errors: List<String> = emptyList(),
            override val warnings: List<String> = emptyList()
        ) : Invalid()
        
        /**
         * Multiple validation failures.
         */
        data class MultipleFailures(
            override val credential: VerifiableCredential,
            val failures: List<Invalid>,
            override val errors: List<String> = emptyList(),
            override val warnings: List<String> = emptyList()
        ) : Invalid() {
            init {
                require(failures.isNotEmpty()) { "MultipleFailures requires at least one failure" }
            }
        }
    }
    
    /**
     * True if verification succeeded.
     */
    val isValid: Boolean
        get() = this is Valid
    
    /**
     * Get all errors (flattened for nested failures).
     */
    val allErrors: List<String>
        get() = when (this) {
            is Valid -> emptyList()
            is Invalid.InvalidProof -> errors
            is Invalid.Expired -> errors
            is Invalid.NotYetValid -> errors
            is Invalid.Revoked -> errors
            is Invalid.InvalidIssuer -> errors
            is Invalid.UntrustedIssuer -> errors
            is Invalid.UnsupportedFormat -> errors
            is Invalid.SchemaValidationFailed -> errors + validationErrors
            is Invalid.MultipleFailures -> failures.flatMap { it.allErrors } + errors
        }
    
    /**
     * Get all warnings.
     */
    val allWarnings: List<String>
        get() = when (this) {
            is Valid -> warnings
            is Invalid -> warnings
        }
    
}

/**
 * Extension function for fluent result handling.
 */
inline fun <T> VerificationResult.ifValid(block: (VerificationResult.Valid) -> T): T? =
    (this as? VerificationResult.Valid)?.let(block)

inline fun <T> VerificationResult.ifInvalid(block: (VerificationResult.Invalid) -> T): T? =
    (this as? VerificationResult.Invalid)?.let(block)

/**
 * Map over valid result (returns new Valid result with transformed credential).
 * 
 * Note: This is a simplified version. For more complex transformations,
 * use fold() or ifValid().
 */
inline fun VerificationResult.mapCredential(transform: (VerifiableCredential) -> VerifiableCredential): VerificationResult {
    return when (this) {
        is VerificationResult.Valid -> copy(credential = transform(credential))
        is VerificationResult.Invalid -> this
    }
}

/**
 * Fold (pattern matching as expression).
 */
inline fun <R> VerificationResult.fold(
    onInvalid: (VerificationResult.Invalid) -> R,
    onValid: (VerificationResult.Valid) -> R
): R {
    return when (this) {
        is VerificationResult.Valid -> onValid(this)
        is VerificationResult.Invalid -> onInvalid(this)
    }
}

/**
 * Recover from specific invalid results.
 */
inline fun VerificationResult.recover(
    predicate: (VerificationResult.Invalid) -> Boolean,
    transform: (VerificationResult.Invalid) -> VerificationResult.Valid
): VerificationResult {
    return when (this) {
        is VerificationResult.Valid -> this
        is VerificationResult.Invalid -> if (predicate(this)) {
            transform(this)
        } else {
            this
        }
    }
}

/**
 * Check if result is expired.
 */
val VerificationResult.isExpired: Boolean
    get() = this is VerificationResult.Invalid.Expired

/**
 * Check if result is revoked.
 */
val VerificationResult.isRevoked: Boolean
    get() = this is VerificationResult.Invalid.Revoked

/**
 * Check if result is invalid proof.
 */
val VerificationResult.isInvalidProof: Boolean
    get() = this is VerificationResult.Invalid.InvalidProof

/**
 * Get Valid credential or null.
 */
val VerificationResult.validCredential: VerifiableCredential?
    get() = (this as? VerificationResult.Valid)?.credential

/**
 * Credential status information.
 */
data class CredentialStatusInfo(
    val valid: Boolean,
    val revoked: Boolean = false,
    val expired: Boolean = false,
    val notYetValid: Boolean = false,
    val statusDetails: Map<String, JsonElement> = emptyMap()
)

