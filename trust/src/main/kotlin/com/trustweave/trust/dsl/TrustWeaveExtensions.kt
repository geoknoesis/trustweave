package com.trustweave.trust.dsl

import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.wallet.Wallet
import com.trustweave.trust.dsl.wallet.OrganizationResult
import com.trustweave.trust.dsl.did.DidBuilder
import com.trustweave.trust.types.Did
import com.trustweave.trust.types.VerificationResult
import com.trustweave.did.DidDocument

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
 * @return Issued credential
 */
suspend fun TrustWeaveContext.createDidAndIssue(
    didBlock: DidBuilder.() -> Unit,
    credentialBlock: suspend (String) -> VerifiableCredential
): VerifiableCredential {
    val did = createDid(didBlock)
    return credentialBlock(did.value)
}


/**
 * Extension function to create DID, issue credential, and store in wallet.
 *
 * @param didBlock DID creation block
 * @param credentialBlock Credential issuance block
 * @param wallet Wallet to store credential in
 * @return Stored credential result
 */
suspend fun TrustWeaveContext.createDidIssueAndStore(
    didBlock: DidBuilder.() -> Unit,
    credentialBlock: suspend (String) -> VerifiableCredential,
    wallet: Wallet
): StoredCredential {
    val did = createDid(didBlock)
    val credential = credentialBlock(did.value)
    return credential.storeIn(wallet)
}


/**
 * Extension function for complete workflow: create DID, issue, store, organize, verify.
 *
 * @param didBlock DID creation block
 * @param credentialBlock Credential issuance block
 * @param wallet Wallet to store credential in
 * @param organizeBlock Optional organization block
 * @return Complete workflow result
 */
suspend fun TrustWeaveContext.completeWorkflow(
    didBlock: DidBuilder.() -> Unit,
    credentialBlock: suspend (String) -> VerifiableCredential,
    wallet: Wallet,
    organizeBlock: (suspend (StoredCredential) -> OrganizationResult)? = null
): WorkflowResult {
    val did = createDid(didBlock)
    val credential = credentialBlock(did.value)
    val stored = credential.storeIn(wallet)

    val organizationResult = organizeBlock?.invoke(stored)

    val verificationResult = this.verify {
        credential(credential)
    }

    return WorkflowResult(
        did = did,
        credential = credential,
        storedCredential = stored,
        organizationResult = organizationResult,
        verificationResult = verificationResult
    )
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
suspend fun TrustWeaveConfig.createDid(block: DidBuilder.() -> Unit): Did {
    return getDslContext().createDid(block)
}

suspend fun TrustWeaveConfig.updateDid(block: com.trustweave.trust.dsl.did.DidDocumentBuilder.() -> Unit): com.trustweave.did.DidDocument {
    return getDslContext().updateDid(block)
}

suspend fun TrustWeaveConfig.rotateKey(block: com.trustweave.trust.dsl.KeyRotationBuilder.() -> Unit): com.trustweave.did.DidDocument {
    return getDslContext().rotateKey(block)
}

suspend fun TrustWeaveConfig.verify(block: com.trustweave.trust.dsl.credential.VerificationBuilder.() -> Unit): com.trustweave.trust.types.VerificationResult {
    return getDslContext().verify(block)
}

suspend fun TrustWeaveConfig.issue(block: com.trustweave.trust.dsl.credential.IssuanceBuilder.() -> Unit): com.trustweave.credential.models.VerifiableCredential {
    return getDslContext().issue(block)
}

suspend fun TrustWeaveConfig.registerSchema(block: com.trustweave.trust.dsl.credential.SchemaBuilder.() -> Unit): com.trustweave.credential.schema.SchemaRegistrationResult {
    return com.trustweave.trust.dsl.credential.registerSchema(block)
}

fun TrustWeaveConfig.schema(schemaId: String? = null, block: com.trustweave.trust.dsl.credential.SchemaBuilder.() -> Unit = {}): com.trustweave.trust.dsl.credential.SchemaBuilder {
    return com.trustweave.trust.dsl.credential.schema(schemaId, block)
}

fun TrustWeaveConfig.revocation(block: com.trustweave.trust.dsl.credential.RevocationBuilder.() -> Unit): com.trustweave.trust.dsl.credential.RevocationBuilder {
    val context = getDslContext()
    // TrustWeaveContext implements CredentialDslProvider, so we can use it directly
    // Create RevocationBuilder directly to avoid recursion with extension function
    val provider = context as com.trustweave.trust.dsl.credential.CredentialDslProvider
    return com.trustweave.trust.dsl.credential.RevocationBuilder(provider.getStatusListManager()).apply(block)
}

suspend fun TrustWeaveConfig.revoke(block: com.trustweave.trust.dsl.credential.RevocationBuilder.() -> Unit): Boolean {
    val context = getDslContext()
    // TrustWeaveContext implements CredentialDslProvider, so we can use it directly
    // Create RevocationBuilder directly to avoid recursion with extension function
    val provider = context as com.trustweave.trust.dsl.credential.CredentialDslProvider
    val builder = com.trustweave.trust.dsl.credential.RevocationBuilder(provider.getStatusListManager())
    builder.block()
    return builder.revoke()
}

/**
 * Extension functions for TrustWeaveConfig to delegate workflow operations to TrustWeaveContext.
 */
suspend fun TrustWeaveConfig.createDidAndIssue(
    didBlock: DidBuilder.() -> Unit,
    credentialBlock: suspend (String) -> VerifiableCredential
): VerifiableCredential {
    return getDslContext().createDidAndIssue(didBlock, credentialBlock)
}

suspend fun TrustWeaveConfig.createDidIssueAndStore(
    didBlock: DidBuilder.() -> Unit,
    credentialBlock: suspend (String) -> VerifiableCredential,
    wallet: Wallet
): StoredCredential {
    return getDslContext().createDidIssueAndStore(didBlock, credentialBlock, wallet)
}

suspend fun TrustWeaveConfig.completeWorkflow(
    didBlock: DidBuilder.() -> Unit,
    credentialBlock: suspend (String) -> VerifiableCredential,
    wallet: Wallet,
    organizeBlock: (suspend (StoredCredential) -> OrganizationResult)? = null
): WorkflowResult {
    return getDslContext().completeWorkflow(didBlock, credentialBlock, wallet, organizeBlock)
}

