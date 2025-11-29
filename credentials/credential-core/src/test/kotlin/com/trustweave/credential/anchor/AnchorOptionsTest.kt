package com.trustweave.credential.anchor

import kotlinx.serialization.json.*
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
        val credential = com.trustweave.credential.models.VerifiableCredential(
            type = listOf("VerifiableCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject { put("id", "did:key:subject") },
            issuanceDate = "2024-01-01T00:00:00Z"
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


