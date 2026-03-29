package org.trustweave.cheqddid.spi

import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.base.AbstractDidMethodProvider
import org.trustweave.cheqddid.CheqdDidConfig
import org.trustweave.cheqddid.CheqdDidMethod

/**
 * SPI provider for did:cheqd method.
 *
 * Automatically discovers did:cheqd method when this module is on the classpath.
 */
class CheqdDidMethodProvider : AbstractDidMethodProvider() {

    override val name: String = "cheqd"

    override val supportedMethods: List<String> = listOf("cheqd")

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName.lowercase() != "cheqd") return null
        val config = createConfig(options)
        return CheqdDidMethod(resolveKms(options), getOrCreateAnchorClient(options, config), config)
    }

    /**
     * Creates CheqdDidConfig from options.
     */
    private fun createConfig(options: DidCreationOptions): CheqdDidConfig {
        val configMap = options.additionalProperties

        // Use defaults if not specified
        return CheqdDidConfig.fromMap(configMap)
    }

    /**
     * Gets or creates a blockchain anchor client for Cheqd.
     */
    private fun getOrCreateAnchorClient(
        options: DidCreationOptions,
        config: CheqdDidConfig
    ): BlockchainAnchorClient {
        // Check if anchor client is provided in options
        val providedClient = options.additionalProperties["anchorClient"] as? BlockchainAnchorClient
        if (providedClient != null) {
            return providedClient
        }

        // In a full implementation, we'd create a CheqdBlockchainAnchorClient
        // For now, throw an error - anchor client must be provided
        throw IllegalStateException(
            "BlockchainAnchorClient must be provided in options for did:cheqd. " +
            "Set 'anchorClient' in additionalProperties."
        )
    }
}

