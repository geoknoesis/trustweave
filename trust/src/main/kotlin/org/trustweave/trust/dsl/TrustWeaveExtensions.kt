package org.trustweave.trust.dsl

import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.wallet.Wallet
import org.trustweave.trust.dsl.wallet.OrganizationResult
import org.trustweave.trust.dsl.did.DidBuilder
import org.trustweave.did.identifiers.Did
import org.trustweave.trust.types.VerificationResult
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.did.model.DidDocument
import org.trustweave.credential.schema.SchemaRegistrationResult
import org.trustweave.trust.dsl.credential.*
import org.trustweave.trust.dsl.did.DidDocumentBuilder
import org.trustweave.trust.TrustWeave
import kotlinx.serialization.json.JsonObject

/**
 * Stored Credential type alias.
 *
 * Represents a credential that has been stored in a wallet.
 * Currently aliased to VerifiableCredential, but may be extended in the future
 * to include wallet-specific metadata (e.g., storage location, organization tags).
 */
typealias StoredCredential = VerifiableCredential

/**
 * Extension function to store a credential in a wallet.
 * Returns the credential itself as StoredCredential.
 */
suspend fun VerifiableCredential.storeIn(wallet: Wallet): StoredCredential {
    wallet.store(this)
    return this
}

/**
 * TrustWeave Convenience Extensions.
 *
 * Provides convenience methods and fluent chaining for common workflows.
 *
 * **Example Usage**:
 * ```kotlin
 * // Complete workflow in one chain
 * val result = trustWeave
 *     .createDid { method("key") }
 *     .let { did ->
 *         trustWeave.issue {
 *             credential { issuer(did); subject { id(did) } }
 *             signedBy(issuerDid = did, keyId = "key-1")
 *         }
 *     }
 *     .storeIn(wallet)
 *     .verify(trustWeave)
 * ```
 */

/**
 * Extract error message from DidCreationResult.Failure.
 */
private fun DidCreationResult.Failure.getErrorMessage(): String = when (this) {
    is DidCreationResult.Failure.MethodNotRegistered -> 
        "DID method '$method' not registered. Available: ${availableMethods.joinToString()}"
    is DidCreationResult.Failure.KeyGenerationFailed -> reason
    is DidCreationResult.Failure.DocumentCreationFailed -> reason
    is DidCreationResult.Failure.InvalidConfiguration -> reason
    is DidCreationResult.Failure.Other -> reason
}

/**
 * Extract error message from IssuanceResult.Failure.
 */
private fun IssuanceResult.Failure.getErrorMessage(): String = when (this) {
    is IssuanceResult.Failure.UnsupportedFormat -> 
        "Unsupported format '${format.value}'. Supported: ${supportedFormats.joinToString { it.value }}"
    is IssuanceResult.Failure.AdapterNotReady -> 
        "Adapter not ready: ${reason ?: "Unknown reason"}"
    is IssuanceResult.Failure.InvalidRequest -> 
        "Invalid request: field '$field' - $reason"
    is IssuanceResult.Failure.AdapterError -> 
        "Adapter error: $reason"
    is IssuanceResult.Failure.MultipleFailures -> 
        "Multiple failures: ${errors.joinToString("; ")}"
}

/**
 * Extension function to create DID and issue credential in one workflow.
 *
 * @param didBlock DID creation block
 * @param credentialBlock Credential issuance block that receives the created DID
 * @return Issued credential result
 */
suspend fun TrustWeave.createDidAndIssue(
    didBlock: DidBuilder.() -> Unit,
    credentialBlock: suspend (String) -> IssuanceResult
): IssuanceResult {
    val didResult = createDid(block = didBlock)
    val did = when (didResult) {
        is DidCreationResult.Success -> didResult.did
        is DidCreationResult.Failure -> {
            return IssuanceResult.Failure.InvalidRequest(
                field = "issuer",
                reason = "DID creation failed: ${didResult.getErrorMessage()}"
            )
        }
    }
    return credentialBlock(did.value)
}


/**
 * Extension function to create DID, issue credential, and store in wallet.
 *
 * @param didBlock DID creation block
 * @param credentialBlock Credential issuance block
 * @param wallet Wallet to store credential in
 * @return Result containing stored credential or failure
 */
suspend fun TrustWeave.createDidIssueAndStore(
    didBlock: DidBuilder.() -> Unit,
    credentialBlock: suspend (String) -> IssuanceResult,
    wallet: Wallet
): Result<StoredCredential> {
    val didResult = createDid(block = didBlock)
    val did = when (didResult) {
        is DidCreationResult.Success -> didResult.did
        is DidCreationResult.Failure -> {
            return Result.failure(IllegalStateException("DID creation failed: ${didResult.getErrorMessage()}"))
        }
    }
    val issuanceResult = credentialBlock(did.value)
    val credential = when (issuanceResult) {
        is IssuanceResult.Success -> issuanceResult.credential
        is IssuanceResult.Failure -> {
            return Result.failure(IllegalStateException("Credential issuance failed: ${issuanceResult.getErrorMessage()}"))
        }
    }
    return Result.success(credential.storeIn(wallet))
}


/**
 * Extension function for complete workflow: create DID, issue, store, organize, verify.
 *
 * @param didBlock DID creation block
 * @param credentialBlock Credential issuance block
 * @param wallet Wallet to store credential in
 * @param organizeBlock Optional organization block
 * @return Result containing complete workflow result or failure
 */
suspend fun TrustWeave.completeWorkflow(
    didBlock: DidBuilder.() -> Unit,
    credentialBlock: suspend (String) -> IssuanceResult,
    wallet: Wallet,
    organizeBlock: (suspend (StoredCredential) -> OrganizationResult)? = null
): Result<WorkflowResult> {
    val didResult = createDid(block = didBlock)
    val did = when (didResult) {
        is DidCreationResult.Success -> didResult.did
        is DidCreationResult.Failure -> {
            return Result.failure(IllegalStateException("DID creation failed: ${didResult.getErrorMessage()}"))
        }
    }
    
    val issuanceResult = credentialBlock(did.value)
    val credential = when (issuanceResult) {
        is IssuanceResult.Success -> issuanceResult.credential
        is IssuanceResult.Failure -> {
            return Result.failure(IllegalStateException("Credential issuance failed: ${issuanceResult.getErrorMessage()}"))
        }
    }
    
    val stored = credential.storeIn(wallet)

    val organizationResult = organizeBlock?.invoke(stored)

    val verificationResult = this.verify {
        credential(credential)
    }

    return Result.success(WorkflowResult(
        did = did,
        credential = credential,
        storedCredential = stored,
        organizationResult = organizationResult,
        verificationResult = verificationResult
    ))
}

/**
 * Workflow result containing all steps.
 */
data class WorkflowResult(
    val did: Did,
    val credential: VerifiableCredential,
    val storedCredential: StoredCredential,
    val organizationResult: OrganizationResult? = null,
    val verificationResult: VerificationResult
)

// Note: These extension functions have been removed.
// Use TrustWeave methods directly instead of TrustWeaveConfig extensions.
// For example: trustWeave.createDid { ... } instead of config.createDid { ... }

// ========== Credential Format Transformation ==========
// 
// Use extension functions directly on VerifiableCredential:
// - credential.toJwt()
// - credential.toJsonLd()
// - credential.toCbor()
// 
// These are provided by credential-api's CredentialTransformerExtensions.

