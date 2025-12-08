package com.trustweave.trust.dsl

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.wallet.Wallet
import com.trustweave.trust.dsl.wallet.OrganizationResult
import com.trustweave.trust.dsl.did.DidBuilder
import com.trustweave.did.identifiers.Did
import com.trustweave.trust.types.VerificationResult
import com.trustweave.trust.types.DidCreationResult
import com.trustweave.credential.results.IssuanceResult
import com.trustweave.did.model.DidDocument
import com.trustweave.trust.dsl.credential.registerSchema
import com.trustweave.trust.dsl.credential.schema

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
 * Extension function to create DID and issue credential in one workflow.
 *
 * @param didBlock DID creation block
 * @param credentialBlock Credential issuance block that receives the created DID
 * @return Issued credential result
 */
suspend fun TrustWeaveContext.createDidAndIssue(
    didBlock: DidBuilder.() -> Unit,
    credentialBlock: suspend (String) -> IssuanceResult
): IssuanceResult {
    val didResult = createDid(didBlock)
    val did = when (didResult) {
        is DidCreationResult.Success -> didResult.did
        is DidCreationResult.Failure -> {
            val reason = when (didResult) {
                is DidCreationResult.Failure.MethodNotRegistered -> 
                    "DID method '${didResult.method}' not registered. Available: ${didResult.availableMethods.joinToString()}"
                is DidCreationResult.Failure.KeyGenerationFailed -> didResult.reason
                is DidCreationResult.Failure.DocumentCreationFailed -> didResult.reason
                is DidCreationResult.Failure.InvalidConfiguration -> didResult.reason
                is DidCreationResult.Failure.Other -> didResult.reason
            }
            return IssuanceResult.Failure.InvalidRequest(
                field = "issuer",
                reason = "DID creation failed: $reason"
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
suspend fun TrustWeaveContext.createDidIssueAndStore(
    didBlock: DidBuilder.() -> Unit,
    credentialBlock: suspend (String) -> IssuanceResult,
    wallet: Wallet
): Result<StoredCredential> {
    val didResult = createDid(didBlock)
    val did = when (didResult) {
        is DidCreationResult.Success -> didResult.did
        is DidCreationResult.Failure -> {
            val reason = when (didResult) {
                is DidCreationResult.Failure.MethodNotRegistered -> 
                    "DID method '${didResult.method}' not registered. Available: ${didResult.availableMethods.joinToString()}"
                is DidCreationResult.Failure.KeyGenerationFailed -> didResult.reason
                is DidCreationResult.Failure.DocumentCreationFailed -> didResult.reason
                is DidCreationResult.Failure.InvalidConfiguration -> didResult.reason
                is DidCreationResult.Failure.Other -> didResult.reason
            }
            return Result.failure(IllegalStateException("DID creation failed: $reason"))
        }
    }
    val issuanceResult = credentialBlock(did.value)
    val credential = when (issuanceResult) {
        is IssuanceResult.Success -> issuanceResult.credential
        is IssuanceResult.Failure -> {
            val reason = when (issuanceResult) {
                is IssuanceResult.Failure.UnsupportedFormat -> 
                    "Unsupported format '${issuanceResult.format.value}'. Supported: ${issuanceResult.supportedFormats.joinToString { it.value }}"
                is IssuanceResult.Failure.AdapterNotReady -> 
                    "Adapter not ready: ${issuanceResult.reason ?: "Unknown reason"}"
                is IssuanceResult.Failure.InvalidRequest -> 
                    "Invalid request: field '${issuanceResult.field}' - ${issuanceResult.reason}"
                is IssuanceResult.Failure.AdapterError -> 
                    "Adapter error: ${issuanceResult.reason}"
                is IssuanceResult.Failure.MultipleFailures -> 
                    "Multiple failures: ${issuanceResult.errors.joinToString("; ")}"
            }
            return Result.failure(IllegalStateException("Credential issuance failed: $reason"))
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
suspend fun TrustWeaveContext.completeWorkflow(
    didBlock: DidBuilder.() -> Unit,
    credentialBlock: suspend (String) -> IssuanceResult,
    wallet: Wallet,
    organizeBlock: (suspend (StoredCredential) -> OrganizationResult)? = null
): Result<WorkflowResult> {
    val didResult = createDid(didBlock)
    val did = when (didResult) {
        is DidCreationResult.Success -> didResult.did
        is DidCreationResult.Failure -> {
            val reason = when (didResult) {
                is DidCreationResult.Failure.MethodNotRegistered -> 
                    "DID method '${didResult.method}' not registered. Available: ${didResult.availableMethods.joinToString()}"
                is DidCreationResult.Failure.KeyGenerationFailed -> didResult.reason
                is DidCreationResult.Failure.DocumentCreationFailed -> didResult.reason
                is DidCreationResult.Failure.InvalidConfiguration -> didResult.reason
                is DidCreationResult.Failure.Other -> didResult.reason
            }
            return Result.failure(IllegalStateException("DID creation failed: $reason"))
        }
    }
    
    val issuanceResult = credentialBlock(did.value)
    val credential = when (issuanceResult) {
        is IssuanceResult.Success -> issuanceResult.credential
        is IssuanceResult.Failure -> {
            val reason = when (issuanceResult) {
                is IssuanceResult.Failure.UnsupportedFormat -> 
                    "Unsupported format '${issuanceResult.format.value}'. Supported: ${issuanceResult.supportedFormats.joinToString { it.value }}"
                is IssuanceResult.Failure.AdapterNotReady -> 
                    "Adapter not ready: ${issuanceResult.reason ?: "Unknown reason"}"
                is IssuanceResult.Failure.InvalidRequest -> 
                    "Invalid request: field '${issuanceResult.field}' - ${issuanceResult.reason}"
                is IssuanceResult.Failure.AdapterError -> 
                    "Adapter error: ${issuanceResult.reason}"
                is IssuanceResult.Failure.MultipleFailures -> 
                    "Multiple failures: ${issuanceResult.errors.joinToString("; ")}"
            }
            return Result.failure(IllegalStateException("Credential issuance failed: $reason"))
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

/**
 * Extension functions for TrustWeaveConfig to delegate to TrustWeaveContext.
 * These allow calling createDid, updateDid, and rotateKey directly on TrustWeaveConfig.
 */
suspend fun TrustWeaveConfig.createDid(block: DidBuilder.() -> Unit): DidCreationResult {
    return getDslContext().createDid(block)
}

suspend fun TrustWeaveConfig.updateDid(block: com.trustweave.trust.dsl.did.DidDocumentBuilder.() -> Unit): com.trustweave.did.model.DidDocument {
    return getDslContext().updateDid(block)
}

suspend fun TrustWeaveConfig.rotateKey(block: com.trustweave.trust.dsl.KeyRotationBuilder.() -> Unit): com.trustweave.did.model.DidDocument {
    return getDslContext().rotateKey(block)
}

suspend fun TrustWeaveConfig.verify(block: com.trustweave.trust.dsl.credential.VerificationBuilder.() -> Unit): com.trustweave.trust.types.VerificationResult {
    return getDslContext().verify(block)
}

suspend fun TrustWeaveConfig.issue(block: com.trustweave.trust.dsl.credential.IssuanceBuilder.() -> Unit): IssuanceResult {
    return getDslContext().issue(block)
}

suspend fun TrustWeaveConfig.registerSchema(block: com.trustweave.trust.dsl.credential.SchemaBuilder.() -> Unit): com.trustweave.credential.schema.SchemaRegistrationResult {
    val context = getDslContext()
    return with(context) { registerSchema(block) }
}

fun TrustWeaveConfig.schema(schemaId: String? = null, block: com.trustweave.trust.dsl.credential.SchemaBuilder.() -> Unit = {}): com.trustweave.trust.dsl.credential.SchemaBuilder {
    val context = getDslContext()
    return with(context) { schema(schemaId, block) }
}

fun TrustWeaveConfig.revocation(block: com.trustweave.trust.dsl.credential.RevocationBuilder.() -> Unit): com.trustweave.trust.dsl.credential.RevocationBuilder {
    val context = getDslContext()
    // TrustWeaveContext implements CredentialDslProvider, so we can use it directly
    // Create RevocationBuilder directly to avoid recursion with extension function
    val provider = context as com.trustweave.trust.dsl.credential.CredentialDslProvider
    return com.trustweave.trust.dsl.credential.RevocationBuilder(provider.getRevocationManager()).apply(block)
}

suspend fun TrustWeaveConfig.revoke(block: com.trustweave.trust.dsl.credential.RevocationBuilder.() -> Unit): Boolean {
    val context = getDslContext()
    // TrustWeaveContext implements CredentialDslProvider, so we can use it directly
    // Create RevocationBuilder directly to avoid recursion with extension function
    val provider = context as com.trustweave.trust.dsl.credential.CredentialDslProvider
    val builder = com.trustweave.trust.dsl.credential.RevocationBuilder(provider.getRevocationManager())
    builder.block()
    return builder.revoke()
}

/**
 * Extension functions for TrustWeaveConfig to delegate workflow operations to TrustWeaveContext.
 */
suspend fun TrustWeaveConfig.createDidAndIssue(
    didBlock: DidBuilder.() -> Unit,
    credentialBlock: suspend (String) -> IssuanceResult
): IssuanceResult {
    return getDslContext().createDidAndIssue(didBlock, credentialBlock)
}

suspend fun TrustWeaveConfig.createDidIssueAndStore(
    didBlock: DidBuilder.() -> Unit,
    credentialBlock: suspend (String) -> IssuanceResult,
    wallet: Wallet
): Result<StoredCredential> {
    return getDslContext().createDidIssueAndStore(didBlock, credentialBlock, wallet)
}

suspend fun TrustWeaveConfig.completeWorkflow(
    didBlock: DidBuilder.() -> Unit,
    credentialBlock: suspend (String) -> IssuanceResult,
    wallet: Wallet,
    organizeBlock: (suspend (StoredCredential) -> OrganizationResult)? = null
): Result<WorkflowResult> {
    return getDslContext().completeWorkflow(didBlock, credentialBlock, wallet, organizeBlock)
}

