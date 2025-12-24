package org.trustweave.keydid.spi

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.spi.DidMethodProvider
import org.trustweave.kms.KeyManagementService
import org.trustweave.keydid.KeyDidMethod
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
                    org.trustweave.kms.spi.KeyManagementServiceProvider::class.java
                )
                kmsProviders.firstOrNull()?.create(options.additionalProperties)
                    ?: throw IllegalStateException(
                        "No KeyManagementService available. Provide 'kms' in options or ensure a KMS provider is registered."
                    )
            }

        return KeyDidMethod(kms)
    }
}

