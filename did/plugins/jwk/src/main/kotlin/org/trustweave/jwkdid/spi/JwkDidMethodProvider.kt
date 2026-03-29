package org.trustweave.jwkdid.spi

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.base.AbstractDidMethodProvider
import org.trustweave.jwkdid.JwkDidMethod

/**
 * SPI provider for did:jwk method.
 *
 * Automatically discovers did:jwk method when this module is on the classpath.
 */
class JwkDidMethodProvider : AbstractDidMethodProvider() {

    override val name: String = "jwk"

    override val supportedMethods: List<String> = listOf("jwk")

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName.lowercase() != "jwk") return null
        return JwkDidMethod(resolveKms(options))
    }
}

