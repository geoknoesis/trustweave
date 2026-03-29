package org.trustweave.trust.dsl.builders

/**
 * Revocation configuration builder.
 */
class RevocationConfigBuilder {
    var provider: String? = null

    fun provider(name: String) {
        provider = name
    }
}

/**
 * Schema configuration builder.
 */
class SchemaConfigBuilder {
    var provider: String? = null

    fun provider(name: String) {
        provider = name
    }
}

/**
 * Trust registry configuration builder.
 */
class TrustConfigBuilder {
    var provider: String? = null

    fun provider(name: String) {
        provider = name
    }
}
