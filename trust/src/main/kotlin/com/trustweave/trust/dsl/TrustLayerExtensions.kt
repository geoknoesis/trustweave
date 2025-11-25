package com.trustweave.trust.dsl

import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.wallet.Wallet
import com.trustweave.trust.dsl.wallet.OrganizationResult
import com.trustweave.trust.dsl.did.DidBuilder

// TODO: StoredCredential needs to be defined in credential-dsl module
// For now, using VerifiableCredential as a placeholder
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
 * Trust Layer Convenience Extensions.
 * 
 * Provides convenience methods and fluent chaining for common workflows.
 * 
 * **Example Usage**:
 * ```kotlin
 * // Complete workflow in one chain
 * val result = trustLayer
 *     .createDid { method("key") }
 *     .let { did -> 
 *         trustLayer.issue {
 *             credential { issuer(did); subject { id(did) } }
 *             by(issuerDid = did, keyId = "key-1")
 *         }
 *     }
 *     .storeIn(wallet)
 *     .verify(trustLayer)
 * ```
 */

/**
 * Extension function to create DID and issue credential in one workflow.
 * 
 * @param didBlock DID creation block
 * @param credentialBlock Credential issuance block that receives the created DID
 * @return Issued credential
 */
suspend fun TrustLayerContext.createDidAndIssue(
    didBlock: DidBuilder.() -> Unit,
    credentialBlock: suspend (String) -> VerifiableCredential
): VerifiableCredential {
    val did = createDid(didBlock)
    return credentialBlock(did)
}

/**
 * Extension function for direct usage on trust layer config.
 */
suspend fun TrustLayerConfig.createDidAndIssue(
    didBlock: DidBuilder.() -> Unit,
    credentialBlock: suspend (String) -> VerifiableCredential
): VerifiableCredential {
    return getDslContext().createDidAndIssue(didBlock, credentialBlock)
}

/**
 * Extension function to create DID, issue credential, and store in wallet.
 * 
 * @param didBlock DID creation block
 * @param credentialBlock Credential issuance block
 * @param wallet Wallet to store credential in
 * @return Stored credential result
 */
suspend fun TrustLayerContext.createDidIssueAndStore(
    didBlock: DidBuilder.() -> Unit,
    credentialBlock: suspend (String) -> VerifiableCredential,
    wallet: Wallet
): StoredCredential {
    val did = createDid(didBlock)
    val credential = credentialBlock(did)
    return credential.storeIn(wallet)
}

/**
 * Extension function for direct usage on trust layer config.
 */
suspend fun TrustLayerConfig.createDidIssueAndStore(
    didBlock: DidBuilder.() -> Unit,
    credentialBlock: suspend (String) -> VerifiableCredential,
    wallet: Wallet
): StoredCredential {
    return getDslContext().createDidIssueAndStore(didBlock, credentialBlock, wallet)
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
suspend fun TrustLayerContext.completeWorkflow(
    didBlock: DidBuilder.() -> Unit,
    credentialBlock: suspend (String) -> VerifiableCredential,
    wallet: Wallet,
    organizeBlock: (suspend (StoredCredential) -> OrganizationResult)? = null
): WorkflowResult {
    val did = createDid(didBlock)
    val credential = credentialBlock(did)
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
    val did: String,
    val credential: VerifiableCredential,
    val storedCredential: StoredCredential,
    val organizationResult: OrganizationResult? = null,
    val verificationResult: com.trustweave.credential.CredentialVerificationResult
)

/**
 * Extension function for direct usage on trust layer config.
 */
suspend fun TrustLayerConfig.completeWorkflow(
    didBlock: DidBuilder.() -> Unit,
    credentialBlock: suspend (String) -> VerifiableCredential,
    wallet: Wallet,
    organizeBlock: (suspend (StoredCredential) -> OrganizationResult)? = null
): WorkflowResult {
    return getDslContext().completeWorkflow(didBlock, credentialBlock, wallet, organizeBlock)
}


