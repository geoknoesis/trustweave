package org.trustweave.trust.services

import org.trustweave.trust.context.WalletDslContext
import org.trustweave.trust.dsl.wallet.WalletBuilder
import org.trustweave.trust.types.WalletCreationResult
import org.trustweave.wallet.exception.WalletException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Domain service for wallet management operations.
 *
 * Extracted from [TrustWeave] to separate the wallet management responsibility into a
 * focused service class.
 */
class WalletManagementService(
    private val walletContext: WalletDslContext,
    private val ioDispatcher: CoroutineDispatcher
) {
    /**
     * Create a wallet using the configured TrustWeave instance.
     *
     * @param block DSL block for configuring the wallet
     * @return Sealed result type with success or detailed failure information
     */
    suspend fun wallet(block: WalletBuilder.() -> Unit): WalletCreationResult =
        withContext(ioDispatcher) {
            try {
                val builder = WalletBuilder(walletContext)
                builder.block()
                val wallet = builder.build()
                WalletCreationResult.Success(wallet)
            } catch (e: WalletException.WalletFactoryNotConfigured) {
                WalletCreationResult.Failure.FactoryNotConfigured(e.reason)
            } catch (e: WalletException.InvalidHolderDid) {
                WalletCreationResult.Failure.InvalidHolderDid(
                    holderDid = e.holderDid,
                    reason = e.reason
                )
            } catch (e: WalletException.WalletCreationFailed) {
                WalletCreationResult.Failure.Other(reason = e.reason, cause = e)
            } catch (e: Throwable) {
                WalletCreationResult.Failure.Other(
                    reason = e.message ?: "Wallet creation failed",
                    cause = e
                )
            }
        }
}
