package com.trustweave.did.threebox

import com.trustweave.did.DidCreationOptions
import com.trustweave.did.DidMethod
import com.trustweave.did.spi.DidMethodProvider
import com.trustweave.kms.KeyManagementService

/**
 * SPI provider for did:3 (3Box/Identity) method.
 *
 * Automatically discovered via Java ServiceLoader when the module is on the classpath.
 *
 * Note: This provider requires a KeyManagementService to be provided via options.
 */
class ThreeBoxIntegration : DidMethodProvider {

    override val name: String = "threebox"

    override val supportedMethods: List<String> = listOf("3")

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName != "3") {
            return null
        }

        // Get KMS from options
        val kms = options.additionalProperties["kms"] as? KeyManagementService
            ?: throw IllegalArgumentException("KeyManagementService must be provided in options for did:3")

        return ThreeBoxDidMethod(kms)
    }
}

