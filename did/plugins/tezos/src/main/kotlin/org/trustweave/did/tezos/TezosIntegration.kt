package org.trustweave.did.tezos

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.base.AbstractDidMethodProvider

/**
 * SPI provider for did:tz (Tezos) method.
 *
 * **STUB — NOT IMPLEMENTED.** [TezosDidMethod] is a non-functional skeleton, so this
 * provider is intentionally NOT registered in `META-INF/services` and is never picked up
 * by ServiceLoader discovery. It exists only for explicit, opt-in construction.
 */
class TezosIntegration : AbstractDidMethodProvider() {

    override val name: String = "tezos"

    override val supportedMethods: List<String> = listOf("tz")

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName != "tz") return null
        return TezosDidMethod(resolveKms(options))
    }
}

