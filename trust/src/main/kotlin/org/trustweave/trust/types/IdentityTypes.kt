package org.trustweave.trust.types

import org.trustweave.did.identifiers.Did

/**
 * Type-safe identity wrappers for trust operations.
 * 
 * These are simple type aliases for Did to provide semantic meaning
 * in trust-related operations.
 */

/**
 * Issuer identity - represents a credential issuer.
 */
typealias IssuerIdentity = Did

/**
 * Verifier identity - represents a credential verifier.
 */
typealias VerifierIdentity = Did

/**
 * Holder identity - represents a credential holder.
 */
typealias HolderIdentity = Did

