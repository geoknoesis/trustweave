package org.trustweave.keydid.spi

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.base.AbstractDidMethodProvider
import org.trustweave.keydid.KeyDidMethod

/**
 * SPI provider for did:key method.
 *
 * Automatically discovers did:key method when this module is on the classpath.
 */
class KeyDidMethodProvider : AbstractDidMethodProvider() {

    override val name: String = "key"

    override val supportedMethods: List<String> = listOf("key")

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName.lowercase() != "key") return null
        return KeyDidMethod(resolveKms(options))
    }
}

