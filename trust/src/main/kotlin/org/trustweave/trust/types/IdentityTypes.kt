package org.trustweave.trust.types

import org.trustweave.did.identifiers.Did

/**
 * Type-safe identity wrappers for trust operations.
 *
 * Value classes provide compile-time type safety without runtime overhead,
 * preventing accidental confusion between issuer, verifier, and holder DIDs.
 */

/**
 * Issuer identity - represents a credential issuer.
 */
@JvmInline
value class IssuerIdentity(val did: Did) {
    companion object {
        operator fun invoke(didString: String): IssuerIdentity = IssuerIdentity(Did(didString))
    }
}

/**
 * Verifier identity - represents a credential verifier.
 */
@JvmInline
value class VerifierIdentity(val did: Did) {
    companion object {
        operator fun invoke(didString: String): VerifierIdentity = VerifierIdentity(Did(didString))
    }
}

/**
 * Holder identity - represents a credential holder.
 */
@JvmInline
value class HolderIdentity(val did: Did) {
    companion object {
        operator fun invoke(didString: String): HolderIdentity = HolderIdentity(Did(didString))
    }
}
