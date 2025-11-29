package com.trustweave.wallet

import com.trustweave.credential.models.VerifiablePresentation
import com.trustweave.credential.PresentationOptions

/**
 * Credential presentation capabilities.
 *
 * Optional interface for wallets that can create presentations from stored credentials.
 *
 * **Example Usage**:
 * ```kotlin
 * val wallet: Wallet = createWallet()
 *
 * if (wallet is CredentialPresentation) {
 *     // Create presentation
 *     val presentation = wallet.createPresentation(
 *         credentialIds = listOf("cred1", "cred2"),
 *         holderDid = "did:key:holder",
 *         options = PresentationOptions(
 *             holderDid = "did:key:holder",
 *             proofType = "Ed25519Signature2020"
 *         )
 *     )
 *
 *     // Create selective disclosure
 *     val selective = wallet.createSelectiveDisclosure(
 *         credentialIds = listOf("cred1"),
 *         disclosedFields = listOf("name", "email"),
 *         holderDid = "did:key:holder",
 *         options = PresentationOptions(...)
 *     )
 * }
 * ```
 */
interface CredentialPresentation {
    /**
     * Create a verifiable presentation from stored credentials.
     *
     * @param credentialIds List of credential IDs to include
     * @param holderDid DID of the presentation holder
     * @param options Presentation options (proof type, challenge, domain, etc.)
     * @return Verifiable presentation
     * @throws IllegalArgumentException if any credential ID is not found
     */
    suspend fun createPresentation(
        credentialIds: List<String>,
        holderDid: String,
        options: PresentationOptions
    ): VerifiablePresentation

    /**
     * Create a selective disclosure presentation.
     *
     * Only discloses specified fields from the credentials.
     *
     * @param credentialIds List of credential IDs to include
     * @param disclosedFields List of field paths to disclose (e.g., "name", "credentialSubject.email")
     * @param holderDid DID of the presentation holder
     * @param options Presentation options
     * @return Verifiable presentation with selective disclosure
     * @throws IllegalArgumentException if any credential ID is not found
     */
    suspend fun createSelectiveDisclosure(
        credentialIds: List<String>,
        disclosedFields: List<String>,
        holderDid: String,
        options: PresentationOptions
    ): VerifiablePresentation
}

