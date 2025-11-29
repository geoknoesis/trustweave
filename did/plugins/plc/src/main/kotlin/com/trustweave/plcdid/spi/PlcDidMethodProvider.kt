package com.trustweave.plcdid.spi

import com.trustweave.did.DidCreationOptions
import com.trustweave.did.DidMethod
import com.trustweave.did.spi.DidMethodProvider
import com.trustweave.plcdid.PlcDidConfig
import com.trustweave.plcdid.PlcDidMethod
import com.trustweave.kms.KeyManagementService
import java.util.ServiceLoader

/**
 * SPI provider for did:plc method.
 *
 * Automatically discovers did:plc method when this module is on the classpath.
 */
class PlcDidMethodProvider : DidMethodProvider {

    override val name: String = "plc"

    override val supportedMethods: List<String> = listOf("plc")

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName.lowercase() != "plc") {
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

        return PlcDidMethod(kms, config)
    }

    /**
     * Creates PlcDidConfig from options.
     */
    private fun createConfig(options: DidCreationOptions): PlcDidConfig {
        val configMap = options.additionalProperties

        // Use defaults if not specified
        return PlcDidConfig.fromMap(configMap)
    }
}

