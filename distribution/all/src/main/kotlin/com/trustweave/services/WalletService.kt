package com.trustweave.services

import com.trustweave.TrustWeaveContext
import com.trustweave.core.*
import com.trustweave.wallet.WalletType
import com.trustweave.wallet.exception.WalletException
import com.trustweave.wallet.Wallet
import com.trustweave.wallet.services.WalletFactory
import com.trustweave.wallet.services.WalletCreationOptions
import java.util.UUID

/**
 * Focused service for wallet operations.
 *
 * Provides wallet creation only. All other wallet operations should be performed
 * directly on the wallet instance (no unnecessary wrappers).
 *
 * **Example:**
 * ```kotlin
 * val TrustWeave = TrustWeave.create()
 * val wallet = trustweave.wallets.create(holderDid = "did:key:holder")
 *
 * // Use wallet directly - no wrapper methods
 * wallet.store(credential)
 * wallet.get(credentialId)
 * wallet.list()
 * ```
 */
class WalletService(
    private val context: TrustWeaveContext
) {
    /**
     * Creates a wallet with the specified configuration.
     *
     * **Example:**
     * ```kotlin
     * // Simple usage
     * val wallet = trustweave.wallets.create(holderDid = "did:key:holder")
     *
     * // With custom type and options
     * val wallet = trustweave.wallets.create(
     *     holderDid = "did:key:holder",
     *     type = WalletType.Database,
     *     options = WalletCreationOptions(
     *         label = "My Wallet",
     *         enableOrganization = true
     *     )
     * )
     * ```
     *
     * @param holderDid DID of the wallet holder (required)
     * @param walletId Optional wallet identifier (generated if not provided)
     * @param type Wallet type (default: InMemory)
     * @param options Provider-specific configuration
     * @return The created wallet instance
     * @throws WalletException.WalletCreationFailed if wallet creation fails
     */
    suspend fun create(
        holderDid: String,
        walletId: String = UUID.randomUUID().toString(),
        type: WalletType = WalletType.InMemory,
        options: WalletCreationOptions = WalletCreationOptions()
    ): Wallet {
        require(holderDid.isNotBlank()) { "Holder DID is required" }

        return try {
            val wallet = context.walletFactory.create(
                providerName = type.id,
                walletId = walletId,
                holderDid = holderDid,
                options = options
            )

            wallet as? Wallet ?: throw IllegalStateException(
                "WalletFactory returned unsupported instance: ${wallet?.let { w: Any -> w::class.qualifiedName }}"
            )
        } catch (e: Exception) {
            throw WalletException.WalletCreationFailed(
                reason = e.message ?: "Unknown error",
                provider = type.id,
                walletId = walletId
            )
        }
    }
}

