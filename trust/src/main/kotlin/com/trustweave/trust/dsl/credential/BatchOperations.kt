package com.trustweave.trust.dsl.credential

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.results.IssuanceResult
import com.trustweave.credential.trust.TrustPolicy
import com.trustweave.credential.requests.VerificationOptions
import com.trustweave.trust.types.VerificationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore

/**
 * Flow-based batch operations for credentials.
 * 
 * Provides reactive, backpressure-aware batch processing using Kotlin Flow.
 * Ideal for processing large numbers of credentials efficiently.
 * 
 * **Example Usage:**
 * ```kotlin
 * // Batch issuance with Flow
 * trustWeave.issueBatch {
 *     requests = listOf(
 *         { credential { type("Degree"); issuer(did1); subject { ... } }; signedBy(did1, "key-1") },
 *         { credential { type("Cert"); issuer(did2); subject { ... } }; signedBy(did2, "key-2") }
 *     )
 *     maxConcurrency = 5
 * }.collect { result ->
 *     when (result) {
 *         is IssuanceResult.Success -> println("Issued: ${result.credential.id}")
 *         is IssuanceResult.Failure -> println("Failed: ${result.allErrors}")
 *     }
 * }
 * 
 * // Batch verification with Flow
 * trustWeave.verifyBatch {
 *     credentials = listOf(cred1, cred2, cred3)
 *     requireTrust(trustRegistry)
 * }.collect { result ->
 *     when (result) {
 *         is VerificationResult.Valid -> println("Valid!")
 *         is VerificationResult.Invalid -> println("Invalid: ${result.errors}")
 *     }
 * }
 * ```
 */

/**
 * Batch issuance builder for Flow-based operations.
 */
class BatchIssuanceBuilder {
    /**
     * List of issuance requests to process.
     * Each request is a DSL block that will be passed to the issue function.
     */
    var requests: List<IssuanceBuilder.() -> Unit> = emptyList()
    
    /**
     * Maximum concurrency for parallel issuance (default: 10).
     * Set to 1 for sequential processing.
     */
    var maxConcurrency: Int = 10
    
    init {
        require(maxConcurrency > 0) { "maxConcurrency must be positive" }
    }
}

/**
 * Batch verification builder for Flow-based operations.
 */
class BatchVerificationBuilder {
    /**
     * List of credentials to verify.
     */
    var credentials: List<VerifiableCredential> = emptyList()
    
    /**
     * Optional trust policy to apply to all verifications.
     */
    var trustPolicy: TrustPolicy? = null
    
    /**
     * Verification options to apply to all verifications.
     */
    var options: VerificationOptions = VerificationOptions()
    
    /**
     * Maximum concurrency for parallel verification (default: 10).
     * Set to 1 for sequential processing.
     */
    var maxConcurrency: Int = 10
    
    init {
        require(maxConcurrency > 0) { "maxConcurrency must be positive" }
    }
}

/**
 * Issue multiple credentials using Flow for reactive processing.
 * 
 * **Example:**
 * ```kotlin
 * trustWeave.issueBatch {
 *     requests = listOf(
 *         { credential { type("DegreeCredential"); issuer(did1); subject { ... } }; signedBy(did1, "key-1") },
 *         { credential { type("CertCredential"); issuer(did2); subject { ... } }; signedBy(did2, "key-2") }
 *     )
 *     maxConcurrency = 5
 * }.collect { result ->
 *     when (result) {
 *         is IssuanceResult.Success -> handleSuccess(result.credential)
 *         is IssuanceResult.Failure -> handleFailure(result)
 *     }
 * }
 * ```
 * 
 * @param block Configuration block for batch issuance
 * @return Flow of IssuanceResult for each request
 */
suspend fun CredentialDslProvider.issueBatch(
    block: BatchIssuanceBuilder.() -> Unit
): Flow<IssuanceResult> = flow {
    val builder = BatchIssuanceBuilder()
    builder.block()
    
    require(builder.requests.isNotEmpty()) { 
        "Batch issuance requires at least one request. Set requests = listOf(...)" 
    }
    
    // Process requests with controlled concurrency
    val semaphore = Semaphore(builder.maxConcurrency)
    
    builder.requests.forEach { request ->
        semaphore.acquire()
        try {
            val result = issue(request)
            emit(result)
        } finally {
            semaphore.release()
        }
    }
}

/**
 * Verify multiple credentials using Flow for reactive processing.
 * 
 * **Example:**
 * ```kotlin
 * trustWeave.verifyBatch {
 *     credentials = listOf(cred1, cred2, cred3)
 *     requireTrust(trustRegistry)
 *     maxConcurrency = 5
 * }.collect { result ->
 *     when (result) {
 *         is VerificationResult.Valid -> println("Valid: ${result.credential.id}")
 *         is VerificationResult.Invalid -> println("Invalid: ${result.errors}")
 *     }
 * }
 * ```
 * 
 * @param block Configuration block for batch verification
 * @return Flow of VerificationResult for each credential
 */
suspend fun CredentialDslProvider.verifyBatch(
    block: BatchVerificationBuilder.() -> Unit
): Flow<VerificationResult> = flow {
    val builder = BatchVerificationBuilder()
    builder.block()
    
    require(builder.credentials.isNotEmpty()) { 
        "Batch verification requires at least one credential. Set credentials = listOf(...)" 
    }
    
    // Process verifications with controlled concurrency
    val semaphore = Semaphore(builder.maxConcurrency)
    
    builder.credentials.forEach { credential ->
        semaphore.acquire()
        try {
            val result = verify {
                this.credential(credential)
                builder.trustPolicy?.let { withTrustPolicy(it) }
                // Apply options
                if (!builder.options.checkRevocation) skipRevocation()
                if (!builder.options.checkExpiration) skipExpiration()
                builder.options.schemaId?.let { validateSchema(it.value) }
            }
            emit(result)
        } finally {
            semaphore.release()
        }
    }
}
