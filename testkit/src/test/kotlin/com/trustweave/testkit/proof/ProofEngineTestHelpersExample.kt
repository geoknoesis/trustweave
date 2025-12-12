package com.trustweave.testkit.proof

import com.trustweave.credential.format.ProofSuiteId
import com.trustweave.testkit.TrustWeaveTestFixture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Example usage of ProofEngineTestHelpers.
 * 
 * Demonstrates how to use the convenience methods for testing proof engines.
 */
class ProofEngineTestHelpersExample {

    @Test
    fun `example - create issuance request`() = runBlocking {
        val fixture = TrustWeaveTestFixture.minimal()
        val helpers = fixture.proofEngineHelpers()
        
        // Create a test issuance request
        val request = helpers.createIssuanceRequest(
            format = ProofSuiteId.VC_LD,
            credentialType = "PersonCredential",
            claims = mapOf(
                "name" to "John Doe",
                "email" to "john@example.com",
                "age" to 30
            )
        )
        
        assertNotNull(request)
        assertNotNull(request.issuer)
        assertNotNull(request.credentialSubject)
    }

    @Test
    fun `example - create test credential`() = runBlocking {
        val fixture = TrustWeaveTestFixture.minimal()
        val helpers = fixture.proofEngineHelpers()
        
        // Create a test credential
        val credential = helpers.createTestCredential(
            format = ProofSuiteId.SD_JWT_VC,
            credentialType = "PersonCredential",
            claims = mapOf(
                "name" to "Jane Doe",
                "age" to 25
            )
        )
        
        assertNotNull(credential)
        assertNotNull(credential.issuer)
        assertNotNull(credential.credentialSubject)
    }

    @Test
    fun `example - create expired credential`() = runBlocking {
        val fixture = TrustWeaveTestFixture.minimal()
        val helpers = fixture.proofEngineHelpers()
        
        // Create an expired credential for testing expiration scenarios
        val expiredCredential = helpers.createExpiredCredential(
            format = ProofSuiteId.VC_LD
        )
        
        assertNotNull(expiredCredential)
        assertNotNull(expiredCredential.expirationDate)
    }

    @Test
    fun `example - create credential without proof`() = runBlocking {
        val fixture = TrustWeaveTestFixture.minimal()
        val helpers = fixture.proofEngineHelpers()
        
        // Create a credential without proof for testing missing proof scenarios
        val credential = helpers.createCredentialWithoutProof(
            format = ProofSuiteId.VC_LD
        )
        
        assertNotNull(credential)
        assertNull(credential.proof)
    }

    @Test
    fun `example - create verification options`() = runBlocking {
        val fixture = TrustWeaveTestFixture.minimal()
        val helpers = fixture.proofEngineHelpers()
        
        // Create verification options
        val options = helpers.createVerificationOptions(
            checkRevocation = true,
            checkExpiration = true,
            validateSchema = false
        )
        
        assertNotNull(options)
        assertTrue(options.checkRevocation)
        assertTrue(options.checkExpiration)
    }

    @Test
    fun `example - create presentation request`() = runBlocking {
        val fixture = TrustWeaveTestFixture.minimal()
        val helpers = fixture.proofEngineHelpers()
        
        // Create a presentation request with selective disclosure
        val request = helpers.createPresentationRequest(
            disclosedClaims = setOf("name", "email"),
            proofOptions = helpers.createPresentationProofOptions(
                challenge = "test-challenge-123",
                domain = "example.com"
            )
        )
        
        assertNotNull(request)
        assertNotNull(request.disclosedClaims)
        assertEquals(2, request.disclosedClaims?.size)
    }

    @Test
    fun `example - using ProofEngineTestData without fixture`() {
        // Use ProofEngineTestData when you don't need DIDs or other fixture resources
        val request = ProofEngineTestData.createMinimalIssuanceRequest(
            format = ProofSuiteId.VC_LD,
            credentialType = "TestCredential",
            claims = mapOf("name" to "Test")
        )
        
        assertNotNull(request)
    }
}

