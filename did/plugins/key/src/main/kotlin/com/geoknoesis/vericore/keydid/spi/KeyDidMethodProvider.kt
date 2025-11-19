package com.geoknoesis.vericore.keydid.spi

import com.geoknoesis.vericore.did.DidCreationOptions
import com.geoknoesis.vericore.did.DidMethod
import com.geoknoesis.vericore.did.spi.DidMethodProvider
import com.geoknoesis.vericore.kms.KeyManagementService
import com.geoknoesis.vericore.keydid.KeyDidMethod
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
                    com.geoknoesis.vericore.kms.spi.KeyManagementServiceProvider::class.java
                )
                kmsProviders.firstOrNull()?.create(options.additionalProperties)
                    ?: throw IllegalStateException(
                        "No KeyManagementService available. Provide 'kms' in options or ensure a KMS provider is registered."
                    )
            }

        return KeyDidMethod(kms)
    }
}

