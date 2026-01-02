package org.trustweave.credential.internal

import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.spi.proof.ProofEngine
import org.trustweave.credential.spi.proof.ProofEngineCapabilities
import org.trustweave.core.identifiers.Iri
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertEquals

/**
 * Comprehensive tests for ErrorHandling utility.
 */
class ErrorHandlingTest {
    
    @Test
    fun `test handleIssuanceErrors with successful operation`() = runBlocking {
        val credential = createTestCredential()
        
        val result = ErrorHandling.handleIssuanceErrors(ProofSuiteId.VC_LD) {
            credential
        }
        
        assertTrue(result is IssuanceResult.Success, "Should return success for successful operation")
        assertEquals(credential, (result as IssuanceResult.Success).credential)
    }
    
    @Test
    fun `test handleIssuanceErrors with IllegalArgumentException`() = runBlocking {
        val result = ErrorHandling.handleIssuanceErrors(ProofSuiteId.VC_LD) {
            throw IllegalArgumentException("Invalid request")
        }
        
        assertTrue(result is IssuanceResult.Failure.InvalidRequest, "Should return InvalidRequest for IllegalArgumentException")
        val failure = result as IssuanceResult.Failure.InvalidRequest
        assertEquals("request", failure.field)
        assertTrue(failure.reason?.contains("Invalid request") == true)
    }
    
    @Test
    fun `test handleIssuanceErrors with IllegalStateException`() = runBlocking {
        val result = ErrorHandling.handleIssuanceErrors(ProofSuiteId.VC_LD) {
            throw IllegalStateException("Engine not ready")
        }
        
        assertTrue(result is IssuanceResult.Failure.AdapterNotReady, "Should return AdapterNotReady for IllegalStateException")
        val failure = result as IssuanceResult.Failure.AdapterNotReady
        assertEquals(ProofSuiteId.VC_LD, failure.format)
        assertEquals("Engine not ready", failure.reason)
    }
    
    @Test
    fun `test handleIssuanceErrors with TimeoutException`() = runBlocking {
        val result = ErrorHandling.handleIssuanceErrors(ProofSuiteId.VC_LD) {
            throw java.util.concurrent.TimeoutException("Operation timed out")
        }
        
        assertTrue(result is IssuanceResult.Failure.AdapterError, "Should return AdapterError for TimeoutException")
        val failure = result as IssuanceResult.Failure.AdapterError
        assertEquals(ProofSuiteId.VC_LD, failure.format)
        assertTrue(failure.reason?.contains("timed out") == true)
    }
    
    @Test
    fun `test handleIssuanceErrors with IOException`() = runBlocking {
        val result = ErrorHandling.handleIssuanceErrors(ProofSuiteId.VC_LD) {
            throw java.io.IOException("I/O error occurred")
        }
        
        assertTrue(result is IssuanceResult.Failure.AdapterError, "Should return AdapterError for IOException")
        val failure = result as IssuanceResult.Failure.AdapterError
        assertEquals(ProofSuiteId.VC_LD, failure.format)
        assertTrue(failure.reason?.contains("I/O error") == true)
    }
    
    @Test
    fun `test handleIssuanceErrors with RuntimeException`() = runBlocking {
        val result = ErrorHandling.handleIssuanceErrors(ProofSuiteId.VC_LD) {
            throw RuntimeException("Runtime error")
        }
        
        assertTrue(result is IssuanceResult.Failure.AdapterError, "Should return AdapterError for RuntimeException")
        val failure = result as IssuanceResult.Failure.AdapterError
        assertEquals(ProofSuiteId.VC_LD, failure.format)
        assertTrue(failure.reason?.contains("Runtime error") == true)
    }
    
    @Test
    fun `test handleIssuanceErrors with generic Exception`() = runBlocking {
        val result = ErrorHandling.handleIssuanceErrors(ProofSuiteId.VC_LD) {
            throw Exception("Unexpected error")
        }
        
        assertTrue(result is IssuanceResult.Failure.AdapterError, "Should return AdapterError for generic Exception")
        val failure = result as IssuanceResult.Failure.AdapterError
        assertEquals(ProofSuiteId.VC_LD, failure.format)
        assertTrue(failure.reason?.contains("Unexpected error") == true)
    }
    
    @Test
    fun `test handleIssuanceErrors with Exception without message`() = runBlocking {
        val result = ErrorHandling.handleIssuanceErrors(ProofSuiteId.VC_LD) {
            throw Exception()
        }
        
        assertTrue(result is IssuanceResult.Failure.AdapterError, "Should return AdapterError even without message")
        val failure = result as IssuanceResult.Failure.AdapterError
        assertNotNull(failure.reason)
    }
    
    @Test
    fun `test validateEngineAvailability with available engine`() {
        val mockEngine = object : ProofEngine {
            override val format = ProofSuiteId.VC_LD
            override val formatName = "Test"
            override val formatVersion = "1.0"
            override val capabilities = ProofEngineCapabilities()
            
            override suspend fun issue(request: org.trustweave.credential.requests.IssuanceRequest): VerifiableCredential {
                throw NotImplementedError()
            }
            
            override suspend fun verify(credential: VerifiableCredential, options: org.trustweave.credential.requests.VerificationOptions): org.trustweave.credential.results.VerificationResult {
                throw NotImplementedError()
            }
            
            override fun isReady() = true
        }
        
        val engines = mapOf(ProofSuiteId.VC_LD to mockEngine)
        val result = ErrorHandling.validateEngineAvailability(ProofSuiteId.VC_LD, engines)
        
        assertEquals(null, result, "Should return null for available and ready engine")
    }
    
    @Test
    fun `test validateEngineAvailability with unavailable engine`() {
        val engines = emptyMap<ProofSuiteId, ProofEngine>()
        val result = ErrorHandling.validateEngineAvailability(ProofSuiteId.VC_LD, engines)
        
        assertTrue(result is IssuanceResult.Failure.UnsupportedFormat, "Should return UnsupportedFormat for unavailable engine")
        val failure = result as IssuanceResult.Failure.UnsupportedFormat
        assertEquals(ProofSuiteId.VC_LD, failure.format)
    }
    
    @Test
    fun `test validateEngineAvailability with not ready engine`() {
        val mockEngine = object : ProofEngine {
            override val format = ProofSuiteId.VC_LD
            override val formatName = "Test"
            override val formatVersion = "1.0"
            override val capabilities = ProofEngineCapabilities()
            
            override suspend fun issue(request: org.trustweave.credential.requests.IssuanceRequest): VerifiableCredential {
                throw NotImplementedError()
            }
            
            override suspend fun verify(credential: VerifiableCredential, options: org.trustweave.credential.requests.VerificationOptions): org.trustweave.credential.results.VerificationResult {
                throw NotImplementedError()
            }
            
            override fun isReady() = false
        }
        
        val engines = mapOf(ProofSuiteId.VC_LD to mockEngine)
        val result = ErrorHandling.validateEngineAvailability(ProofSuiteId.VC_LD, engines)
        
        assertTrue(result is IssuanceResult.Failure.AdapterNotReady, "Should return AdapterNotReady for not ready engine")
        val failure = result as IssuanceResult.Failure.AdapterNotReady
        assertEquals(ProofSuiteId.VC_LD, failure.format)
        assertEquals("Proof engine not initialized", failure.reason)
    }
    
    @Test
    fun `test validateEngineAvailability with different format`() {
        val mockEngine = object : ProofEngine {
            override val format = ProofSuiteId.SD_JWT_VC
            override val formatName = "Test"
            override val formatVersion = "1.0"
            override val capabilities = ProofEngineCapabilities()
            
            override suspend fun issue(request: org.trustweave.credential.requests.IssuanceRequest): VerifiableCredential {
                throw NotImplementedError()
            }
            
            override suspend fun verify(credential: VerifiableCredential, options: org.trustweave.credential.requests.VerificationOptions): org.trustweave.credential.results.VerificationResult {
                throw NotImplementedError()
            }
            
            override fun isReady() = true
        }
        
        val engines = mapOf(ProofSuiteId.SD_JWT_VC to mockEngine)
        val result = ErrorHandling.validateEngineAvailability(ProofSuiteId.VC_LD, engines)
        
        assertTrue(result is IssuanceResult.Failure.UnsupportedFormat, "Should return UnsupportedFormat for different format")
        val failure = result as IssuanceResult.Failure.UnsupportedFormat
        assertEquals(ProofSuiteId.VC_LD, failure.format)
    }
    
    // Helper function
    private fun createTestCredential(): VerifiableCredential {
        return VerifiableCredential(
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            issuer = Issuer.IriIssuer(Iri("did:key:test")),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject(
                id = Iri("did:key:holder"),
                claims = emptyMap()
            ),
            proof = null
        )
    }
}



