package org.trustweave.ethrdid.spi

import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.spi.DidMethodProvider
import org.trustweave.ethrdid.EthrDidConfig
import org.trustweave.ethrdid.EthrDidMethod
import org.trustweave.kms.KeyManagementService
import java.util.ServiceLoader
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService

/**
 * SPI provider for did:ethr method.
 *
 * Automatically discovers did:ethr method when this module is on the classpath.
 */
class EthrDidMethodProvider : DidMethodProvider {

    override val name: String = "ethr"

    override val supportedMethods: List<String> = listOf("ethr")

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName.lowercase() != "ethr") {
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

        return EthrDidMethod(kms, anchorClient, config)
    }

    /**
     * Creates EthrDidConfig from options.
     */
    private fun createConfig(options: DidCreationOptions): EthrDidConfig {
        val configMap = options.additionalProperties

        require(configMap.containsKey("rpcUrl")) { "rpcUrl is required for did:ethr" }
        require(configMap.containsKey("chainId")) { "chainId is required for did:ethr" }

        return EthrDidConfig.fromMap(configMap)
    }

    /**
     * Gets or creates a blockchain anchor client for Ethereum.
     */
    private fun getOrCreateAnchorClient(
        options: DidCreationOptions,
        config: EthrDidConfig
    ): BlockchainAnchorClient {
        // Check if anchor client is provided in options
        val providedClient = options.additionalProperties["anchorClient"] as? BlockchainAnchorClient
        if (providedClient != null) {
            return providedClient
        }

        // Create anchor client options for Polygon/Ethereum-compatible client
        val anchorOptions = buildMap<String, Any?> {
            put("rpcUrl", config.rpcUrl)
            if (config.privateKey != null) {
                put("privateKey", config.privateKey)
            }
        }

        // Use PolygonBlockchainAnchorClient for Ethereum-compatible chains
        // Since Polygon client uses the same EVM infrastructure
        return try {
            val polygonClientClass = Class.forName("org.trustweave.polygon.PolygonBlockchainAnchorClient")
            val constructor = polygonClientClass.getConstructor(String::class.java, Map::class.java)
            @Suppress("UNCHECKED_CAST")
            constructor.newInstance(config.chainId, anchorOptions) as BlockchainAnchorClient
        } catch (e: Exception) {
            // If Polygon client is not available, require anchor client to be provided
            throw IllegalStateException(
                "BlockchainAnchorClient is required for did:ethr. " +
                "Provide 'anchorClient' in options or add TrustWeave-polygon dependency for EVM-compatible chains. " +
                "Error: ${e.message}"
            )
        }
    }
}

