package org.trustweave.anchor.cardano

import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.anchor.BlockchainAnchorRegistry
import org.trustweave.anchor.spi.BlockchainAnchorClientProvider
import org.trustweave.anchor.spi.BlockchainIntegrationHelper

/**
 * SPI provider for Cardano blockchain anchor clients.
 *
 * Registered via `META-INF/services/org.trustweave.anchor.spi.BlockchainAnchorClientProvider`.
 * Recognises CAIP-2 chain ids `cardano:mainnet`, `cardano:preview`, and `cardano:preprod`.
 *
 * Required options when used through the generic [BlockchainAnchorClientProvider.create] API:
 * - [CardanoAnchorConfig.KEY_PROJECT_ID] (Blockfrost project id, required)
 * - [CardanoAnchorConfig.KEY_MNEMONIC] or [CardanoAnchorConfig.KEY_SECRET_KEY] (write path only)
 *
 * The provider itself does *not* fail when those options are missing: it returns a client
 * that can still be used in read-only mode, with [BlockchainAnchorClient.writePayload]
 * surfacing a structured [org.trustweave.anchor.exceptions.BlockchainException].
 */
class CardanoBlockchainAnchorClientProvider : BlockchainAnchorClientProvider {

    override val name: String = "cardano"

    override val supportedChains: List<String> = listOf(
        CardanoBlockchainAnchorClient.MAINNET,
        CardanoBlockchainAnchorClient.PREVIEW,
        CardanoBlockchainAnchorClient.PREPROD,
    )

    override val requiredEnvironmentVariables: List<String> = listOf(
        "?CARDANO_BLOCKFROST_PROJECT_ID",
        "?CARDANO_SUBMITTER_MNEMONIC",
    )

    override fun create(chainId: String, options: Map<String, Any?>): BlockchainAnchorClient? {
        if (!chainId.startsWith("cardano:")) return null
        if (CardanoNetwork.fromChainId(chainId) == null) return null

        val mergedOptions = if (options.containsKey(CardanoAnchorConfig.KEY_PROJECT_ID)) {
            options
        } else {
            options + buildMap<String, Any?> {
                System.getenv("CARDANO_BLOCKFROST_PROJECT_ID")?.let {
                    put(CardanoAnchorConfig.KEY_PROJECT_ID, it)
                }
                System.getenv("CARDANO_SUBMITTER_MNEMONIC")?.let {
                    put(CardanoAnchorConfig.KEY_MNEMONIC, it)
                }
                System.getenv("CARDANO_SUBMITTER_SECRET_KEY")?.let {
                    put(CardanoAnchorConfig.KEY_SECRET_KEY, it)
                }
            }
        }

        if (mergedOptions[CardanoAnchorConfig.KEY_PROJECT_ID] == null) return null
        return CardanoBlockchainAnchorClient(chainId, mergedOptions)
    }
}

/**
 * Result of registering Cardano clients with a [BlockchainAnchorRegistry].
 */
data class CardanoIntegrationResult(
    val registry: BlockchainAnchorRegistry,
    val registeredChains: List<String>,
)

/**
 * Discovery helpers for [CardanoBlockchainAnchorClientProvider].
 */
object CardanoIntegration {

    /**
     * Discovers via SPI and registers every supported chain on [registry].
     * Chains without sufficient configuration (e.g. no Blockfrost project id) are skipped.
     */
    fun discoverAndRegister(
        options: Map<String, Any?> = emptyMap(),
        registry: BlockchainAnchorRegistry = BlockchainAnchorRegistry(),
    ): CardanoIntegrationResult {
        val chains = BlockchainIntegrationHelper.discoverAndRegister(
            providerName = "cardano",
            registry = registry,
            options = options,
        )
        return CardanoIntegrationResult(registry, chains)
    }

    /**
     * Manually register the specified [chainIds] (defaults to Preview testnet).
     */
    fun setup(
        chainIds: List<String> = listOf(CardanoBlockchainAnchorClient.PREVIEW),
        options: Map<String, Any?> = emptyMap(),
        registry: BlockchainAnchorRegistry = BlockchainAnchorRegistry(),
    ): CardanoIntegrationResult {
        val chains = BlockchainIntegrationHelper.setup(
            providerName = "cardano",
            registry = registry,
            chainIds = chainIds,
            defaultChainIds = listOf(CardanoBlockchainAnchorClient.PREVIEW),
            options = options,
        )
        return CardanoIntegrationResult(registry, chains)
    }
}
