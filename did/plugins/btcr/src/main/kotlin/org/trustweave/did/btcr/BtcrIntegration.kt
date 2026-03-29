package org.trustweave.did.btcr

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.base.AbstractDidMethodProvider

/**
 * SPI provider for did:btcr (Bitcoin Reference) method.
 *
 * Automatically discovered via Java ServiceLoader when the module is on the classpath.
 */
class BtcrIntegration : AbstractDidMethodProvider() {

    override val name: String = "btcr"

    override val supportedMethods: List<String> = listOf("btcr")

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName != "btcr") return null
        return BtcrDidMethod(resolveKms(options))
    }
}

