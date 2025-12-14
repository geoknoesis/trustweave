package com.trustweave.credential.anchor

import com.trustweave.anchor.BlockchainAnchorClient
import com.trustweave.anchor.AnchorRef
import com.trustweave.credential.CredentialService
import com.trustweave.credential.model.vc.VerifiableCredential

/**
 * Extension methods for CredentialService to support blockchain anchoring.
 * 
 * These extensions provide convenient access to credential anchoring
 * without requiring direct use of CredentialAnchorService.
 * 
 * **Note:** These extensions require the `credentials:plugins:anchor` module
 * and a configured BlockchainAnchorClient.
 */

/**
 * Anchor a credential to blockchain.
 * 
 * **Example:**
 * ```kotlin
 * val result = credentialService.anchorCredential(
 *     credential = credential,
 *     anchorClient = anchorClient,
 *     chainId = "algorand:testnet"
 * )
 * ```
 * 
 * @param credential Credential to anchor
 * @param anchorClient Blockchain anchor client
 * @param chainId Chain identifier
 * @param options Anchor options
 * @return Anchor result
 */
suspend fun CredentialService.anchorCredential(
    credential: VerifiableCredential,
    anchorClient: BlockchainAnchorClient,
    chainId: String,
    options: AnchorOptions = AnchorOptions()
): CredentialAnchorResult {
    val anchorService = CredentialAnchorService(anchorClient)
    return anchorService.anchorCredential(credential, chainId, options)
}

/**
 * Verify that a credential is anchored on blockchain.
 * 
 * **Example:**
 * ```kotlin
 * val isAnchored = credentialService.verifyAnchoredCredential(
 *     credential = credential,
 *     anchorClient = anchorClient,
 *     chainId = "algorand:testnet"
 * )
 * ```
 * 
 * @param credential Credential to verify
 * @param anchorClient Blockchain anchor client
 * @param chainId Chain identifier
 * @return true if anchored and verified
 */
suspend fun CredentialService.verifyAnchoredCredential(
    credential: VerifiableCredential,
    anchorClient: BlockchainAnchorClient,
    chainId: String
): Boolean {
    val anchorService = CredentialAnchorService(anchorClient)
    return anchorService.verifyAnchoredCredential(credential, chainId)
}

/**
 * Get anchor reference for a credential.
 * 
 * **Example:**
 * ```kotlin
 * val anchorRef = credentialService.getAnchorReference(
 *     credential = credential,
 *     anchorClient = anchorClient,
 *     chainId = "algorand:testnet"
 * )
 * ```
 * 
 * @param credential Credential to get anchor reference for
 * @param anchorClient Blockchain anchor client
 * @param chainId Chain identifier
 * @return Anchor reference, or null if not found
 */
suspend fun CredentialService.getAnchorReference(
    credential: VerifiableCredential,
    anchorClient: BlockchainAnchorClient,
    chainId: String
): AnchorRef? {
    val anchorService = CredentialAnchorService(anchorClient)
    return anchorService.getAnchorReference(credential, chainId)
}

