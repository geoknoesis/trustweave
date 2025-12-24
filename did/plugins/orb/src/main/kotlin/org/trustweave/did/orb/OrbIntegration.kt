package org.trustweave.did.orb

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.spi.DidMethodProvider
import org.trustweave.kms.KeyManagementService

/**
 * SPI provider for did:orb (Orb DID) method.
 *
 * Automatically discovered via Java ServiceLoader when the module is on the classpath.
 */
class OrbIntegration : DidMethodProvider {

    override val name: String = "orb"

    override val supportedMethods: List<String> = listOf("orb")

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName != "orb") {
            return null
        }

        val kms = options.additionalProperties["kms"] as? KeyManagementService
            ?: throw IllegalArgumentException("KeyManagementService must be provided in options for did:orb")

        return OrbDidMethod(kms)
    }
}

