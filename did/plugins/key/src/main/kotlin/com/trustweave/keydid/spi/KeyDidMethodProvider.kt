package com.trustweave.keydid.spi

import com.trustweave.did.DidCreationOptions
import com.trustweave.did.DidMethod
import com.trustweave.did.spi.DidMethodProvider
import com.trustweave.kms.KeyManagementService
import com.trustweave.keydid.KeyDidMethod
import java.util.ServiceLoader

/**
 * SPI provider for did:key method.
 * 
 * Automatically discovers did:key method when this module is on the classpath.
 */
class KeyDidMethodProvider : DidMethodProvider {

    override val name: String = "key"

    override val supportedMethods: List<String> = listOf("key")

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName.lowercase() != "key") {
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

        return KeyDidMethod(kms)
    }
}

