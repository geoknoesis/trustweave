package org.trustweave.trust.internal

import kotlinx.datetime.Clock
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.did.identifiers.Did

/**
 * Placeholder VC when verification cannot run because [org.trustweave.credential.CredentialService]
 * is not configured. The credential is not verifiable; it only carries [VerificationResult.Invalid.AdapterNotReady].
 */
internal fun placeholderCredentialForUnconfiguredVerification(): VerifiableCredential =
    VerifiableCredential(
        type = listOf(CredentialType.VerifiableCredential),
        issuer = Issuer.IriIssuer(Iri("did:key:trustweave-configuration-placeholder")),
        issuanceDate = Clock.System.now(),
        credentialSubject = CredentialSubject.fromDid(Did("did:key:trustweave-configuration-placeholder-subject")),
    )
