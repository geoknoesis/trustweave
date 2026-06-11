package org.trustweave.did.btcr

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.base.AbstractDidMethodProvider

/**
 * SPI provider for did:btcr (Bitcoin Reference) method.
 *
 * **STUB — NOT IMPLEMENTED.** [BtcrDidMethod] is a non-functional skeleton, so this
 * provider is intentionally NOT registered in `META-INF/services` and is never picked up
 * by ServiceLoader discovery. It exists only for explicit, opt-in construction.
 */
class BtcrIntegration : AbstractDidMethodProvider() {

    override val name: String = "btcr"

    override val supportedMethods: List<String> = listOf("btcr")

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName != "btcr") return null
        return BtcrDidMethod(resolveKms(options))
    }
}

