package org.trustweave.did.threebox

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.base.AbstractDidMethodProvider

/**
 * SPI provider for did:3 (3Box/Identity) method.
 *
 * Automatically discovered via Java ServiceLoader when the module is on the classpath.
 */
class ThreeBoxIntegration : AbstractDidMethodProvider() {

    override val name: String = "threebox"

    override val supportedMethods: List<String> = listOf("3")

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName != "3") return null
        return ThreeBoxDidMethod(resolveKms(options))
    }
}

