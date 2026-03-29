package org.trustweave.iondid.spi

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.base.AbstractDidMethodProvider
import org.trustweave.iondid.IonDidConfig
import org.trustweave.iondid.IonDidMethod

/**
 * SPI provider for did:ion method.
 *
 * Automatically discovers did:ion method when this module is on the classpath.
 */
class IonDidMethodProvider : AbstractDidMethodProvider() {

    override val name: String = "ion"

    override val supportedMethods: List<String> = listOf("ion")

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName.lowercase() != "ion") return null
        return IonDidMethod(resolveKms(options), createConfig(options))
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

