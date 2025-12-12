package com.trustweave.credential.anchor

import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.CredentialType
import com.trustweave.did.identifiers.Did
import kotlinx.serialization.json.*
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for AnchorOptions and CredentialAnchorResult.
 */
class AnchorOptionsTest {

    @Test
    fun `test AnchorOptions with all fields`() {
        val options = AnchorOptions(
            includeProof = true,
            addEvidenceToCredential = false
        )

        assertTrue(options.includeProof)
        assertFalse(options.addEvidenceToCredential)
    }

    @Test
    fun `test AnchorOptions with defaults`() {
        val options = AnchorOptions()

        assertFalse(options.includeProof)
        assertTrue(options.addEvidenceToCredential)
    }

    @Test
    fun `test CredentialAnchorResult`() {
        val anchorRef = Any() // Placeholder AnchorRef
        val credential = com.trustweave.credential.model.vc.VerifiableCredential(
            type = listOf(CredentialType.VerifiableCredential),
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            credentialSubject = CredentialSubject.fromDid(
                Did("did:key:subject"),
                claims = emptyMap()
            ),
            issuanceDate = Instant.parse("2024-01-01T00:00:00Z")
        )

        val result = CredentialAnchorResult(
            anchorRef = anchorRef,
            credential = credential,
            digest = "digest-123"
        )

        assertEquals(anchorRef, result.anchorRef)
        assertEquals(credential, result.credential)
        assertEquals("digest-123", result.digest)
    }
}


