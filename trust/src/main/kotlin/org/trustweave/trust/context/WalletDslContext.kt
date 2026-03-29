package org.trustweave.trust.context

import org.trustweave.wallet.services.WalletFactory

/**
 * Narrow context for wallet DSL ([org.trustweave.trust.dsl.wallet.WalletBuilder])
 * and [org.trustweave.trust.services.WalletManagementService].
 */
interface WalletDslContext {
    fun getWalletFactory(): WalletFactory?
}
