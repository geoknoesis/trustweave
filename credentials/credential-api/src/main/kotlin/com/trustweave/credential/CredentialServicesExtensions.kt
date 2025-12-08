package com.trustweave.credential

import com.trustweave.credential.model.vc.Issuer
import com.trustweave.did.identifiers.Did
import com.trustweave.core.identifiers.Iri

/**
 * Convenience extensions for CredentialService.
 */

/**
 * Convenience extensions for creating VC types from DIDs and IRIs.
 */
fun Did.asIssuer(): Issuer = Issuer.fromDid(this)
fun Iri.asIssuer(): Issuer = Issuer.from(this)
