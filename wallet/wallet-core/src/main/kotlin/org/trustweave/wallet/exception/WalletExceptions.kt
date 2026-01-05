package org.trustweave.wallet.exception

import org.trustweave.core.exception.TrustWeaveException

/**
 * Wallet-related exception types.
 *
 * These exceptions provide structured error codes and context for wallet operations.
 */
sealed class WalletException(
    override val code: String,
    override val message: String,
    override val context: Map<String, Any?> = emptyMap(),
    override val cause: Throwable? = null
) : TrustWeaveException(code, message, context, cause) {

    data class WalletCreationFailed(
        val reason: String,
        val provider: String? = null,
        val walletId: String? = null
    ) : WalletException(
        code = "WALLET_CREATION_FAILED",
        message = "Wallet creation failed: $reason",
        context = mapOf(
            "reason" to reason,
            "provider" to provider,
            "walletId" to walletId
        ).filterValues { it != null }
    )

    /**
     * Wallet factory not configured.
     */
    data class WalletFactoryNotConfigured(
        val reason: String = "Wallet factory not configured. Ensure TrustWeave-testkit is on classpath or provide a wallet factory via TrustWeaveConfig."
    ) : WalletException(
        code = "WALLET_FACTORY_NOT_CONFIGURED",
        message = reason,
        context = mapOf("reason" to reason)
    )

    /**
     * Invalid holder DID provided.
     */
    data class InvalidHolderDid(
        val holderDid: String,
        val reason: String
    ) : WalletException(
        code = "INVALID_HOLDER_DID",
        message = "Invalid holder DID '$holderDid': $reason",
        context = mapOf(
            "holderDid" to holderDid,
            "reason" to reason
        )
    )
}

