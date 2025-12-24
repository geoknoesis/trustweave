package org.trustweave.soldid.spi

import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.spi.DidMethodProvider
import org.trustweave.soldid.SolDidConfig
import org.trustweave.soldid.SolDidMethod
import org.trustweave.kms.KeyManagementService
import java.util.ServiceLoader

/**
 * SPI provider for did:sol method.
 *
 * Automatically discovers did:sol method when this module is on the classpath.
 */
class SolDidMethodProvider : DidMethodProvider {

    override val name: String = "sol"

    override val supportedMethods: List<String> = listOf("sol")

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName.lowercase() != "sol") {
            return null
        }

        // Get KMS from options or discover via SPI
        val kms = (options.additionalProperties["kms"] as? KeyManagementService)
            ?: run {
                val kmsProviders = ServiceLoader.load(
                    org.trustweave.kms.spi.KeyManagementServiceProvider::class.java
                )
                kmsProviders.firstOrNull()?.create(options.additionalProperties)
                    ?: throw IllegalStateException(
                        "No KeyManagementService available. Provide 'kms' in options or ensure a KMS provider is registered."
                    )
            }

        // Create configuration from options
        val config = createConfig(options)

        // Get or create blockchain anchor client
        val anchorClient = getOrCreateAnchorClient(options, config)

        return SolDidMethod(kms, anchorClient, config)
    }

    /**
     * Creates SolDidConfig from options.
     */
    private fun createConfig(options: DidCreationOptions): SolDidConfig {
        val configMap = options.additionalProperties

        require(configMap.containsKey("rpcUrl")) { "rpcUrl is required for did:sol" }

        return SolDidConfig.fromMap(configMap)
    }

    /**
     * Gets or creates a blockchain anchor client for Solana.
     */
    private fun getOrCreateAnchorClient(
        options: DidCreationOptions,
        config: SolDidConfig
    ): BlockchainAnchorClient {
        // Check if anchor client is provided in options
        val providedClient = options.additionalProperties["anchorClient"] as? BlockchainAnchorClient
        if (providedClient != null) {
            return providedClient
        }

        // In a full implementation, we'd create a SolanaBlockchainAnchorClient
        // For now, throw an error - anchor client must be provided
        throw IllegalStateException(
            "BlockchainAnchorClient must be provided in options for did:sol. " +
            "Set 'anchorClient' in additionalProperties."
        )
    }
}

