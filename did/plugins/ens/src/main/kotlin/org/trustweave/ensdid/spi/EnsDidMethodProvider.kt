package org.trustweave.ensdid.spi

import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.spi.DidMethodProvider
import org.trustweave.ensdid.EnsDidConfig
import org.trustweave.ensdid.EnsDidMethod
import org.trustweave.kms.KeyManagementService
import java.util.ServiceLoader

/**
 * SPI provider for did:ens method.
 */
class EnsDidMethodProvider : DidMethodProvider {

    override val name: String = "ens"

    override val supportedMethods: List<String> = listOf("ens")

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName.lowercase() != "ens") {
            return null
        }

        val kms = (options.additionalProperties["kms"] as? KeyManagementService)
            ?: run {
                val kmsProviders = ServiceLoader.load(
                    org.trustweave.kms.spi.KeyManagementServiceProvider::class.java
                )
                kmsProviders.firstOrNull()?.create(options.additionalProperties)
                    ?: throw IllegalStateException("No KeyManagementService available")
            }

        val config = createConfig(options)
        val anchorClient = getOrCreateAnchorClient(options, config)

        return EnsDidMethod(kms, anchorClient, config)
    }

    private fun createConfig(options: DidCreationOptions): EnsDidConfig {
        val configMap = options.additionalProperties

        require(configMap.containsKey("ensRegistryAddress")) {
            "ensRegistryAddress is required for did:ens"
        }
        require(configMap.containsKey("rpcUrl")) { "rpcUrl is required for did:ens" }
        require(configMap.containsKey("chainId")) { "chainId is required for did:ens" }

        return EnsDidConfig.fromMap(configMap)
    }

    private fun getOrCreateAnchorClient(
        options: DidCreationOptions,
        config: EnsDidConfig
    ): BlockchainAnchorClient {
        val providedClient = options.additionalProperties["anchorClient"] as? BlockchainAnchorClient
        if (providedClient != null) {
            return providedClient
        }

        val anchorOptions = buildMap<String, Any?> {
            put("rpcUrl", config.rpcUrl)
            if (config.privateKey != null) {
                put("privateKey", config.privateKey)
            }
        }

        return try {
            val polygonClientClass = Class.forName("org.trustweave.polygon.PolygonBlockchainAnchorClient")
            val constructor = polygonClientClass.getConstructor(String::class.java, Map::class.java)
            @Suppress("UNCHECKED_CAST")
            constructor.newInstance(config.chainId, anchorOptions) as BlockchainAnchorClient
        } catch (e: Exception) {
            throw IllegalStateException(
                "BlockchainAnchorClient is required for did:ens. " +
                "Provide 'anchorClient' in options or add TrustWeave-polygon dependency. " +
                "Error: ${e.message}"
            )
        }
    }
}

