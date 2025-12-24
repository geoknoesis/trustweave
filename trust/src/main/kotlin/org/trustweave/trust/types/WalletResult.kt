package org.trustweave.trust.types

import org.trustweave.wallet.Wallet

/**
 * Result type for wallet creation operations.
 */
sealed class WalletCreationResult {
    /**
     * Wallet creation succeeded.
     */
    data class Success(
        val wallet: Wallet
    ) : WalletCreationResult()

    /**
     * Wallet creation failed.
     */
    sealed class Failure : WalletCreationResult() {
        /**
         * Invalid holder DID.
         */
        data class InvalidHolderDid(
            val holderDid: String,
            val reason: String
        ) : Failure()

        /**
         * Wallet factory not configured.
         */
        data class FactoryNotConfigured(
            val reason: String
        ) : Failure()

        /**
         * Storage failed.
         */
        data class StorageFailed(
            val reason: String,
            val cause: Throwable? = null
        ) : Failure()

        /**
         * Other failure.
         */
        data class Other(
            val reason: String,
            val cause: Throwable? = null
        ) : Failure()
    }
}

