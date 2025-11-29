package com.trustweave.iondid.spi

import com.trustweave.did.DidCreationOptions
import com.trustweave.did.DidMethod
import com.trustweave.did.spi.DidMethodProvider
import com.trustweave.iondid.IonDidConfig
import com.trustweave.iondid.IonDidMethod
import com.trustweave.kms.KeyManagementService
import java.util.ServiceLoader

/**
 * SPI provider for did:ion method.
 *
 * Automatically discovers did:ion method when this module is on the classpath.
 */
class IonDidMethodProvider : DidMethodProvider {

    override val name: String = "ion"

    override val supportedMethods: List<String> = listOf("ion")

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName.lowercase() != "ion") {
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

        return IonDidMethod(kms, config)
    }

    /**
     * Creates IonDidConfig from options.
     */
    private fun createConfig(options: DidCreationOptions): IonDidConfig {
        val configMap = options.additionalProperties

        require(configMap.containsKey("ionNodeUrl")) {
            "ionNodeUrl is required for did:ion"
        }

        return IonDidConfig.fromMap(configMap)
    }
}

