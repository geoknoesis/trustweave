package com.trustweave.credential.models

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.VerifiablePresentation
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.vc.CredentialProof
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.did.identifiers.Did
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for VerifiablePresentation model.
 */
class VerifiablePresentationTest {

    @Test
    fun `test create presentation with all fields`() {
        val credential = VerifiableCredential(
            type = listOf(CredentialType.VerifiableCredential),
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            credentialSubject = CredentialSubject.fromDid(
                Did("did:key:subject"),
                claims = emptyMap()
            ),
            issuanceDate = Instant.parse("2024-01-01T00:00:00Z")
        )

        val proof = CredentialProof.LinkedDataProof(
            type = "Ed25519Signature2020",
            created = Instant.parse("2024-01-01T00:00:00Z"),
            verificationMethod = "did:key:holder#key-1",
            proofPurpose = "authentication",
            proofValue = "test-proof",
            additionalProperties = mapOf(
                "challenge" to JsonPrimitive("challenge-123"),
                "domain" to JsonPrimitive("example.com")
            )
        )

        val presentation = VerifiablePresentation(
            id = CredentialId("https://example.com/presentations/1"),
            type = listOf(CredentialType.fromString("VerifiablePresentation")),
            verifiableCredential = listOf(credential),
            holder = Did("did:key:holder"),
            proof = proof,
            challenge = "challenge-123",
            domain = "example.com"
        )

        assertEquals("https://example.com/presentations/1", presentation.id?.value)
        assertEquals(1, presentation.verifiableCredential.size)
        assertEquals("did:key:holder", presentation.holder.value)
        assertNotNull(presentation.proof)
        assertEquals("challenge-123", presentation.challenge)
        assertEquals("example.com", presentation.domain)
    }

    @Test
    fun `test create presentation with multiple credentials`() {
        val cred1 = VerifiableCredential(
            type = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("PersonCredential")),
            issuer = Issuer.fromDid(Did("did:key:issuer1")),
            credentialSubject = CredentialSubject.fromDid(
                Did("did:key:subject"),
                claims = emptyMap()
            ),
            issuanceDate = Instant.parse("2024-01-01T00:00:00Z")
        )

        val cred2 = VerifiableCredential(
            type = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("EducationCredential")),
            issuer = Issuer.fromDid(Did("did:key:issuer2")),
            credentialSubject = CredentialSubject.fromDid(
                Did("did:key:subject"),
                claims = emptyMap()
            ),
            issuanceDate = Instant.parse("2024-01-01T00:00:00Z")
        )

        val presentation = VerifiablePresentation(
            type = listOf(CredentialType.fromString("VerifiablePresentation")),
            verifiableCredential = listOf(cred1, cred2),
            holder = Did("did:key:holder")
        )

        assertEquals(2, presentation.verifiableCredential.size)
    }

    @Test
    fun `test create presentation with minimal fields`() {
        val presentation = VerifiablePresentation(
            type = listOf(CredentialType.fromString("VerifiablePresentation")),
            verifiableCredential = emptyList(),
            holder = Did("did:key:holder")
        )

        assertNull(presentation.id)
        assertNull(presentation.proof)
        assertNull(presentation.challenge)
        assertNull(presentation.domain)
        assertTrue(presentation.verifiableCredential.isEmpty())
    }

    @Test
    fun `test presentation with challenge only`() {
        val presentation = VerifiablePresentation(
            type = listOf(CredentialType.fromString("VerifiablePresentation")),
            verifiableCredential = emptyList(),
            holder = Did("did:key:holder"),
            challenge = "challenge-123"
        )

        assertEquals("challenge-123", presentation.challenge)
        assertNull(presentation.domain)
    }

    @Test
    fun `test presentation with domain only`() {
        val presentation = VerifiablePresentation(
            type = listOf(CredentialType.fromString("VerifiablePresentation")),
            verifiableCredential = emptyList(),
            holder = Did("did:key:holder"),
            domain = "example.com"
        )

        assertEquals("example.com", presentation.domain)
        assertNull(presentation.challenge)
    }
}


