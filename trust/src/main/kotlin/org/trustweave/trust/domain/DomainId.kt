package org.trustweave.trust.domain

/**
 * Identifier for a Trusted Domain. A short, opaque, human-readable string —
 * tenants, organisations, federations. Distinct from the domain DID so that
 * a domain can be renamed without re-anchoring its DID.
 */
@JvmInline
value class DomainId(val value: String) {
    init {
        require(value.isNotBlank()) { "DomainId must not be blank" }
    }

    override fun toString(): String = value
}
