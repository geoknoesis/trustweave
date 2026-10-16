package org.trustweave.trust.dsl.wallet

import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.did.identifiers.Did
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

internal fun walletCredential(
    id: String,
    issuer: String = "did:key:issuer-a",
    subject: String = "did:key:subject-a",
    types: List<String> = listOf("VerifiableCredential"),
    expiresAt: Instant? = null,
    signed: Boolean = true,
): VerifiableCredential =
    VerifiableCredential(
        id = CredentialId(id),
        type = types.map { CredentialType.fromString(it) },
        issuer = Issuer.fromDid(Did(issuer)),
        issuanceDate = Clock.System.now() - 2.days,
        credentialSubject = CredentialSubject.fromIri(Iri(subject)),
        expirationDate = expiresAt,
        proof = if (signed) CredentialProof.JwtProof("eyJhbGciOiJFZERTQSJ9.stub.stub") else null,
    )
