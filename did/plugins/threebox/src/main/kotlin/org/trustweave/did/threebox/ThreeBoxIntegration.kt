package org.trustweave.did.threebox

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.base.AbstractDidMethodProvider

/**
 * SPI provider for did:3 (3Box/Identity) method.
 *
 * **STUB — NOT IMPLEMENTED.** [ThreeBoxDidMethod] is a non-functional skeleton, so this
 * provider is intentionally NOT registered in `META-INF/services` and is never picked up
 * by ServiceLoader discovery. It exists only for explicit, opt-in construction.
 */
class ThreeBoxIntegration : AbstractDidMethodProvider() {

    override val name: String = "threebox"

    override val supportedMethods: List<String> = listOf("3")

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName != "3") return null
        return ThreeBoxDidMethod(resolveKms(options))
    }
}

