package org.trustweave.jwkdid.spi

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.spi.DidMethodProvider
import org.trustweave.kms.KeyManagementService
import org.trustweave.jwkdid.JwkDidMethod
import java.util.ServiceLoader

/**
 * SPI provider for did:jwk method.
 *
 * Automatically discovers did:jwk method when this module is on the classpath.
 */
class JwkDidMethodProvider : DidMethodProvider {

    override val name: String = "jwk"

    override val supportedMethods: List<String> = listOf("jwk")

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName.lowercase() != "jwk") {
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

        return JwkDidMethod(kms)
    }
}

