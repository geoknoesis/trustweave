package com.geoknoesis.vericore.services

import com.geoknoesis.vericore.core.*
import com.geoknoesis.vericore.core.types.WalletType
import com.geoknoesis.vericore.credential.wallet.Wallet
import com.geoknoesis.vericore.spi.services.WalletFactory
import com.geoknoesis.vericore.spi.services.WalletCreationOptions
import java.util.UUID

/**
 * Focused service for wallet operations.
 * 
 * Provides wallet creation only. All other wallet operations should be performed
 * directly on the wallet instance (no unnecessary wrappers).
 * 
 * **Example:**
 * ```kotlin
 * val vericore = VeriCore.create()
 * val wallet = vericore.wallets.create(holderDid = "did:key:holder")
 * 
 * // Use wallet directly - no wrapper methods
 * wallet.store(credential)
 * wallet.get(credentialId)
 * wallet.list()
 * ```
 */
class WalletService(
    private val context: VeriCoreContext
) {
    /**
     * Creates a wallet with the specified configuration.
     * 
     * **Example:**
     * ```kotlin
     * // Simple usage
     * val wallet = vericore.wallets.create(holderDid = "did:key:holder")
     * 
     * // With custom type and options
     * val wallet = vericore.wallets.create(
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
     * @throws VeriCoreError.WalletCreationFailed if wallet creation fails
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
                "WalletFactory returned unsupported instance: ${wallet?.let { it::class.qualifiedName }}"
            )
        } catch (e: Exception) {
            throw VeriCoreError.WalletCreationFailed(
                reason = e.message ?: "Unknown error",
                provider = type.id,
                walletId = walletId
            )
        }
    }
}

