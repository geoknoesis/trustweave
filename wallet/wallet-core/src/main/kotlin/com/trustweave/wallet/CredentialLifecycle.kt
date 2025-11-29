package com.trustweave.wallet

import com.trustweave.credential.models.VerifiableCredential

/**
 * Credential lifecycle management capabilities.
 *
 * Optional interface for wallets that support archiving and refreshing credentials.
 *
 * **Example Usage**:
 * ```kotlin
 * val wallet: Wallet = createWallet()
 *
 * if (wallet is CredentialLifecycle) {
 *     // Archive old credential
 *     wallet.archive(credentialId)
 *
 *     // Get archived credentials
 *     val archived = wallet.getArchived()
 *
 *     // Refresh credential
 *     val refreshed = wallet.refreshCredential(credentialId)
 * }
 * ```
 */
interface CredentialLifecycle {
    /**
     * Archive a credential.
     *
     * Archived credentials are hidden from normal queries but can be retrieved
     * via [getArchived].
     *
     * @param credentialId Credential ID
     * @return true if archived, false if credential not found
     */
    suspend fun archive(credentialId: String): Boolean

    /**
     * Unarchive a credential.
     *
     * @param credentialId Credential ID
     * @return true if unarchived, false if credential not found
     */
    suspend fun unarchive(credentialId: String): Boolean

    /**
     * Get all archived credentials.
     *
     * @return List of archived credentials
     */
    suspend fun getArchived(): List<VerifiableCredential>

    /**
     * Refresh a credential.
     *
     * Attempts to refresh the credential from its refresh service if available.
     *
     * @param credentialId Credential ID
     * @return Refreshed credential, or null if refresh failed or credential not found
     */
    suspend fun refreshCredential(credentialId: String): VerifiableCredential?
}

