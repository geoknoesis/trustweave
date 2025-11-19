package com.geoknoesis.vericore.polygondid.spi

import com.geoknoesis.vericore.anchor.BlockchainAnchorClient
import com.geoknoesis.vericore.did.DidCreationOptions
import com.geoknoesis.vericore.did.DidMethod
import com.geoknoesis.vericore.did.spi.DidMethodProvider
import com.geoknoesis.vericore.polygondid.PolygonDidConfig
import com.geoknoesis.vericore.polygondid.PolygonDidMethod
import com.geoknoesis.vericore.kms.KeyManagementService
import java.util.ServiceLoader

/**
 * SPI provider for did:polygon method.
 * 
 * Automatically discovers did:polygon method when this module is on the classpath.
 */
class PolygonDidMethodProvider : DidMethodProvider {

    override val name: String = "polygon"

    override val supportedMethods: List<String> = listOf("polygon")

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName.lowercase() != "polygon") {
            return null
        }

        // Get KMS from options or discover via SPI
        val kms = (options.additionalProperties["kms"] as? KeyManagementService)
            ?: run {
                val kmsProviders = ServiceLoader.load(
                    com.geoknoesis.vericore.kms.spi.KeyManagementServiceProvider::class.java
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

        return PolygonDidMethod(kms, anchorClient, config)
    }

    /**
     * Creates PolygonDidConfig from options.
     */
    private fun createConfig(options: DidCreationOptions): PolygonDidConfig {
        val configMap = options.additionalProperties
        
        require(configMap.containsKey("rpcUrl")) { "rpcUrl is required for did:polygon" }
        require(configMap.containsKey("chainId")) { "chainId is required for did:polygon" }
        
        return PolygonDidConfig.fromMap(configMap)
    }

    /**
     * Gets or creates a blockchain anchor client for Polygon.
     */
    private fun getOrCreateAnchorClient(
        options: DidCreationOptions,
        config: PolygonDidConfig
    ): BlockchainAnchorClient {
        // Check if anchor client is provided in options
        val providedClient = options.additionalProperties["anchorClient"] as? BlockchainAnchorClient
        if (providedClient != null) {
            return providedClient
        }
        
        // Create anchor client options for Polygon
        val anchorOptions = buildMap<String, Any?> {
            put("rpcUrl", config.rpcUrl)
            if (config.privateKey != null) {
                put("privateKey", config.privateKey)
            }
        }
        
        // Use PolygonBlockchainAnchorClient
        return try {
            val polygonClientClass = Class.forName("com.geoknoesis.vericore.polygon.PolygonBlockchainAnchorClient")
            val constructor = polygonClientClass.getConstructor(String::class.java, Map::class.java)
            @Suppress("UNCHECKED_CAST")
            constructor.newInstance(config.chainId, anchorOptions) as BlockchainAnchorClient
        } catch (e: Exception) {
            throw IllegalStateException(
                "BlockchainAnchorClient is required for did:polygon. " +
                "Provide 'anchorClient' in options or add vericore-polygon dependency. " +
                "Error: ${e.message}"
            )
        }
    }
}

