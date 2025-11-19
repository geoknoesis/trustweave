package com.geoknoesis.vericore.peerdid.spi

import com.geoknoesis.vericore.did.DidCreationOptions
import com.geoknoesis.vericore.did.DidMethod
import com.geoknoesis.vericore.did.spi.DidMethodProvider
import com.geoknoesis.vericore.peerdid.PeerDidConfig
import com.geoknoesis.vericore.peerdid.PeerDidMethod
import com.geoknoesis.vericore.kms.KeyManagementService
import java.util.ServiceLoader

/**
 * SPI provider for did:peer method.
 * 
 * Automatically discovers did:peer method when this module is on the classpath.
 */
class PeerDidMethodProvider : DidMethodProvider {

    override val name: String = "peer"

    override val supportedMethods: List<String> = listOf("peer")

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName.lowercase() != "peer") {
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

        return PeerDidMethod(kms, config)
    }

    /**
     * Creates PeerDidConfig from options.
     */
    private fun createConfig(options: DidCreationOptions): PeerDidConfig {
        val configMap = options.additionalProperties
        
        // Use defaults if not specified
        return PeerDidConfig.fromMap(configMap)
    }
}

