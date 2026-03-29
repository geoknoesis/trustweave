package org.trustweave.peerdid.spi

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.base.AbstractDidMethodProvider
import org.trustweave.peerdid.PeerDidConfig
import org.trustweave.peerdid.PeerDidMethod

/**
 * SPI provider for did:peer method.
 *
 * Automatically discovers did:peer method when this module is on the classpath.
 */
class PeerDidMethodProvider : AbstractDidMethodProvider() {

    override val name: String = "peer"

    override val supportedMethods: List<String> = listOf("peer")

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName.lowercase() != "peer") return null
        return PeerDidMethod(resolveKms(options), createConfig(options))
    }

    /**
     * Creates PeerDidConfig from options.
     */
    private fun createConfig(options: DidCreationOptions): PeerDidConfig {
        val configMap = options.additionalProperties

        // Use defaults if not specified
        return PeerDidConfig.fromMap(configMap)
    }
}

