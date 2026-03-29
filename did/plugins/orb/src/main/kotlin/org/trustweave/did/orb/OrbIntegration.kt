package org.trustweave.did.orb

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.base.AbstractDidMethodProvider

/**
 * SPI provider for did:orb (Orb DID) method.
 *
 * Automatically discovered via Java ServiceLoader when the module is on the classpath.
 */
class OrbIntegration : AbstractDidMethodProvider() {

    override val name: String = "orb"

    override val supportedMethods: List<String> = listOf("orb")

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName != "orb") return null
        return OrbDidMethod(resolveKms(options))
    }
}

