package com.trustweave.trust.dsl

import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.wallet.Wallet
import com.trustweave.trust.dsl.wallet.OrganizationResult
import com.trustweave.trust.dsl.did.DidBuilder
import com.trustweave.trust.types.Did
import com.trustweave.trust.types.VerificationResult

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
 *             by(issuerDid = did, keyId = "key-1")
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



