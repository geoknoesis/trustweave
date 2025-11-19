package com.geoknoesis.vericore.jwkdid.spi

import com.geoknoesis.vericore.did.DidCreationOptions
import com.geoknoesis.vericore.did.DidMethod
import com.geoknoesis.vericore.did.spi.DidMethodProvider
import com.geoknoesis.vericore.kms.KeyManagementService
import com.geoknoesis.vericore.jwkdid.JwkDidMethod
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
                    com.geoknoesis.vericore.kms.spi.KeyManagementServiceProvider::class.java
                )
                kmsProviders.firstOrNull()?.create(options.additionalProperties)
                    ?: throw IllegalStateException(
                        "No KeyManagementService available. Provide 'kms' in options or ensure a KMS provider is registered."
                    )
            }

        return JwkDidMethod(kms)
    }
}

