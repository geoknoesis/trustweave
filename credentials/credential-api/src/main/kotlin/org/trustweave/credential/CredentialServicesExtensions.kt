package org.trustweave.credential

import org.trustweave.credential.model.vc.Issuer
import org.trustweave.did.identifiers.Did
import org.trustweave.core.identifiers.Iri

/**
 * Convenience extensions for CredentialService.
 */

/**
 * Convenience extensions for creating VC types from DIDs and IRIs.
 */
fun Did.asIssuer(): Issuer = Issuer.fromDid(this)
fun Iri.asIssuer(): Issuer = Issuer.from(this)
