package com.trustweave.trust.dsl.wallet

import com.trustweave.wallet.services.WalletFactory

/**
 * Provider interface for Wallet DSL operations.
 * 
 * This interface allows Wallet DSLs to work without directly depending on TrustWeaveContext,
 * enabling them to be in the trust module while being used by other modules.
 */
interface WalletDslProvider {
    /**
     * Get the wallet factory.
     */
    fun getWalletFactory(): WalletFactory?
}


