package com.trustweave.credential

import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for CredentialIssuanceOptions, CredentialVerificationOptions, PresentationOptions.
 */
class CredentialOptionsTest {

    @Test
    fun `test CredentialIssuanceOptions with all fields`() {
        val options = CredentialIssuanceOptions(
            providerName = "waltid",
            proofType = "Ed25519Signature2020",
            keyId = "key-1",
            issuerDid = "did:key:issuer",
            challenge = "challenge-123",
            domain = "example.com",
            anchorToBlockchain = true,
            chainId = "algorand:testnet",
            additionalOptions = mapOf("custom" to "value")
        )
        
        assertEquals("waltid", options.providerName)
        assertEquals("Ed25519Signature2020", options.proofType)
        assertEquals("key-1", options.keyId)
        assertEquals("did:key:issuer", options.issuerDid)
        assertEquals("challenge-123", options.challenge)
        assertEquals("example.com", options.domain)
        assertTrue(options.anchorToBlockchain)
        assertEquals("algorand:testnet", options.chainId)
        assertEquals(1, options.additionalOptions.size)
    }

    @Test
    fun `test CredentialIssuanceOptions with defaults`() {
        val options = CredentialIssuanceOptions()
        
        assertNull(options.providerName)
        assertEquals("Ed25519Signature2020", options.proofType)
        assertNull(options.keyId)
        assertNull(options.issuerDid)
        assertNull(options.challenge)
        assertNull(options.domain)
        assertFalse(options.anchorToBlockchain)
        assertNull(options.chainId)
        assertTrue(options.additionalOptions.isEmpty())
    }

    @Test
    fun `test CredentialVerificationOptions with all fields`() {
        val options = CredentialVerificationOptions(
            providerName = "waltid",
            checkRevocation = true,
            checkExpiration = true,
            validateSchema = true,
            schemaId = "https://example.com/schemas/person",
            verifyBlockchainAnchor = true,
            chainId = "algorand:testnet",
            additionalOptions = mapOf("custom" to "value")
        )
        
        assertEquals("waltid", options.providerName)
        assertTrue(options.checkRevocation)
        assertTrue(options.checkExpiration)
        assertTrue(options.validateSchema)
        assertEquals("https://example.com/schemas/person", options.schemaId)
        assertTrue(options.verifyBlockchainAnchor)
        assertEquals("algorand:testnet", options.chainId)
    }

    @Test
    fun `test CredentialVerificationOptions with defaults`() {
        val options = CredentialVerificationOptions()
        
        assertNull(options.providerName)
        assertTrue(options.checkRevocation)
        assertTrue(options.checkExpiration)
        assertFalse(options.validateSchema)
        assertNull(options.schemaId)
        assertFalse(options.verifyBlockchainAnchor)
        assertNull(options.chainId)
    }

    @Test
    fun `test PresentationOptions with all fields`() {
        val options = PresentationOptions(
            holderDid = "did:key:holder",
            proofType = "Ed25519Signature2020",
            keyId = "key-1",
            challenge = "challenge-123",
            domain = "example.com",
            selectiveDisclosure = true,
            disclosedFields = listOf("name", "email"),
            additionalOptions = mapOf("custom" to "value")
        )
        
        assertEquals("did:key:holder", options.holderDid)
        assertEquals("Ed25519Signature2020", options.proofType)
        assertEquals("key-1", options.keyId)
        assertEquals("challenge-123", options.challenge)
        assertEquals("example.com", options.domain)
        assertTrue(options.selectiveDisclosure)
        assertEquals(2, options.disclosedFields.size)
    }

    @Test
    fun `test PresentationOptions with defaults`() {
        val options = PresentationOptions(holderDid = "did:key:holder")
        
        assertEquals("did:key:holder", options.holderDid)
        assertEquals("Ed25519Signature2020", options.proofType)
        assertNull(options.keyId)
        assertNull(options.challenge)
        assertNull(options.domain)
        assertFalse(options.selectiveDisclosure)
        assertTrue(options.disclosedFields.isEmpty())
    }

    @Test
    fun `test PresentationVerificationOptions with all fields`() {
        val options = PresentationVerificationOptions(
            providerName = "waltid",
            verifyChallenge = true,
            expectedChallenge = "challenge-123",
            verifyDomain = true,
            expectedDomain = "example.com",
            checkRevocation = true,
            additionalOptions = mapOf("custom" to "value")
        )
        
        assertEquals("waltid", options.providerName)
        assertTrue(options.verifyChallenge)
        assertEquals("challenge-123", options.expectedChallenge)
        assertTrue(options.verifyDomain)
        assertEquals("example.com", options.expectedDomain)
        assertTrue(options.checkRevocation)
    }

    @Test
    fun `test PresentationVerificationOptions with defaults`() {
        val options = PresentationVerificationOptions()
        
        assertNull(options.providerName)
        assertTrue(options.verifyChallenge)
        assertNull(options.expectedChallenge)
        assertFalse(options.verifyDomain)
        assertNull(options.expectedDomain)
        assertTrue(options.checkRevocation)
    }

    @Test
    fun `test CredentialVerificationResult`() {
        val result = CredentialVerificationResult(
            valid = true,
            errors = emptyList(),
            warnings = listOf("Warning"),
            proofValid = true,
            issuerValid = true,
            notExpired = true,
            notRevoked = true,
            schemaValid = true,
            blockchainAnchorValid = true
        )
        
        assertTrue(result.valid)
        assertTrue(result.proofValid)
        assertTrue(result.issuerValid)
        assertTrue(result.notExpired)
        assertTrue(result.notRevoked)
        assertTrue(result.schemaValid)
        assertTrue(result.blockchainAnchorValid)
        assertEquals(1, result.warnings.size)
    }

    @Test
    fun `test PresentationVerificationResult`() {
        val credentialResult = CredentialVerificationResult(valid = true)
        val result = PresentationVerificationResult(
            valid = true,
            errors = emptyList(),
            warnings = listOf("Warning"),
            presentationProofValid = true,
            challengeValid = true,
            domainValid = true,
            credentialResults = listOf(credentialResult)
        )
        
        assertTrue(result.valid)
        assertTrue(result.presentationProofValid)
        assertTrue(result.challengeValid)
        assertTrue(result.domainValid)
        assertEquals(1, result.credentialResults.size)
    }
}


