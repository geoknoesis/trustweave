package org.trustweave.did.tezos

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.base.AbstractDidMethodProvider

/**
 * SPI provider for did:tz (Tezos) method.
 *
 * Automatically discovered via Java ServiceLoader when the module is on the classpath.
 */
class TezosIntegration : AbstractDidMethodProvider() {

    override val name: String = "tezos"

    override val supportedMethods: List<String> = listOf("tz")

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName != "tz") return null
        return TezosDidMethod(resolveKms(options))
    }
}

