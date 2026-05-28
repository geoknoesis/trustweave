package org.trustweave.trust.dsl.builders

import org.trustweave.trust.dsl.TrustWeaveDsl

/**
 * Revocation configuration builder.
 */
@TrustWeaveDsl
class RevocationConfigBuilder {
    var provider: String? = null

    fun provider(name: String) {
        provider = name
    }
}

/**
 * Schema configuration builder.
 */
@TrustWeaveDsl
class SchemaConfigBuilder {
    var provider: String? = null

    fun provider(name: String) {
        provider = name
    }
}

/**
 * Trust registry configuration builder.
 */
@TrustWeaveDsl
class TrustConfigBuilder {
    var provider: String? = null

    fun provider(name: String) {
        provider = name
    }
}
