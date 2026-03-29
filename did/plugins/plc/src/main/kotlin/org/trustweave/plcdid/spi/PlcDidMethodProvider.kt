package org.trustweave.plcdid.spi

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.base.AbstractDidMethodProvider
import org.trustweave.plcdid.PlcDidConfig
import org.trustweave.plcdid.PlcDidMethod

/**
 * SPI provider for did:plc method.
 *
 * Automatically discovers did:plc method when this module is on the classpath.
 */
class PlcDidMethodProvider : AbstractDidMethodProvider() {

    override val name: String = "plc"

    override val supportedMethods: List<String> = listOf("plc")

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName.lowercase() != "plc") return null
        return PlcDidMethod(resolveKms(options), createConfig(options))
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

