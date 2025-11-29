package com.trustweave.cheqddid.spi

import com.trustweave.anchor.BlockchainAnchorClient
import com.trustweave.did.DidCreationOptions
import com.trustweave.did.DidMethod
import com.trustweave.did.spi.DidMethodProvider
import com.trustweave.cheqddid.CheqdDidConfig
import com.trustweave.cheqddid.CheqdDidMethod
import com.trustweave.kms.KeyManagementService
import java.util.ServiceLoader

/**
 * SPI provider for did:cheqd method.
 *
 * Automatically discovers did:cheqd method when this module is on the classpath.
 */
class CheqdDidMethodProvider : DidMethodProvider {

    override val name: String = "cheqd"

    override val supportedMethods: List<String> = listOf("cheqd")

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName.lowercase() != "cheqd") {
            return null
        }

        // Get KMS from options or discover via SPI
        val kms = (options.additionalProperties["kms"] as? KeyManagementService)
            ?: run {
                val kmsProviders = ServiceLoader.load(
                    com.trustweave.kms.spi.KeyManagementServiceProvider::class.java
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

        return CheqdDidMethod(kms, anchorClient, config)
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

