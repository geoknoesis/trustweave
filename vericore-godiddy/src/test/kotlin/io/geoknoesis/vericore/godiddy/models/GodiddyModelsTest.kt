package io.geoknoesis.vericore.godiddy.models

import kotlinx.serialization.json.*
import kotlin.test.*

/**
 * Tests for godiddy model data classes.
 */
class GodiddyModelsTest {

    @Test
    fun `test GodiddyResolutionResponse with all fields`() {
        val response = GodiddyResolutionResponse(
            didDocument = buildJsonObject { put("id", "did:key:123") },
            didDocumentMetadata = mapOf("created" to buildJsonObject { put("timestamp", "2024-01-01") }),
            didResolutionMetadata = mapOf("duration" to JsonPrimitive(100))
        )
        
        assertNotNull(response.didDocument)
        assertNotNull(response.didDocumentMetadata)
        assertNotNull(response.didResolutionMetadata)
    }

    @Test
    fun `test GodiddyResolutionResponse with defaults`() {
        val response = GodiddyResolutionResponse()
        
        assertNull(response.didDocument)
        assertNull(response.didDocumentMetadata)
        assertNull(response.didResolutionMetadata)
    }

    @Test
    fun `test GodiddyCreateDidRequest`() {
        val request = GodiddyCreateDidRequest(
            method = "key",
            options = mapOf("keyType" to JsonPrimitive("Ed25519"))
        )
        
        assertEquals("key", request.method)
        assertEquals(1, request.options.size)
    }

    @Test
    fun `test GodiddyCreateDidRequest with empty options`() {
        val request = GodiddyCreateDidRequest(method = "key")
        
        assertEquals("key", request.method)
        assertTrue(request.options.isEmpty())
    }

    @Test
    fun `test GodiddyCreateDidResponse with all fields`() {
        val response = GodiddyCreateDidResponse(
            did = "did:key:123",
            didDocument = buildJsonObject { put("id", "did:key:123") },
            jobId = "job-123"
        )
        
        assertEquals("did:key:123", response.did)
        assertNotNull(response.didDocument)
        assertEquals("job-123", response.jobId)
    }

    @Test
    fun `test GodiddyCreateDidResponse with defaults`() {
        val response = GodiddyCreateDidResponse()
        
        assertNull(response.did)
        assertNull(response.didDocument)
        assertNull(response.jobId)
    }

    @Test
    fun `test GodiddyUpdateDidRequest`() {
        val request = GodiddyUpdateDidRequest(
            did = "did:key:123",
            didDocument = buildJsonObject { put("id", "did:key:123") },
            options = mapOf("updateKey" to JsonPrimitive(true))
        )
        
        assertEquals("did:key:123", request.did)
        assertNotNull(request.didDocument)
        assertEquals(1, request.options.size)
    }

    @Test
    fun `test GodiddyUpdateDidRequest with empty options`() {
        val request = GodiddyUpdateDidRequest(
            did = "did:key:123",
            didDocument = buildJsonObject { put("id", "did:key:123") }
        )
        
        assertTrue(request.options.isEmpty())
    }

    @Test
    fun `test GodiddyDeactivateDidRequest`() {
        val request = GodiddyDeactivateDidRequest(
            did = "did:key:123",
            options = mapOf("reason" to JsonPrimitive("no longer needed"))
        )
        
        assertEquals("did:key:123", request.did)
        assertEquals(1, request.options.size)
    }

    @Test
    fun `test GodiddyDeactivateDidRequest with empty options`() {
        val request = GodiddyDeactivateDidRequest(did = "did:key:123")
        
        assertTrue(request.options.isEmpty())
    }

    @Test
    fun `test GodiddyOperationResponse with success`() {
        val response = GodiddyOperationResponse(
            success = true,
            did = "did:key:123",
            didDocument = buildJsonObject { put("id", "did:key:123") },
            jobId = "job-123"
        )
        
        assertTrue(response.success)
        assertEquals("did:key:123", response.did)
        assertNotNull(response.didDocument)
        assertEquals("job-123", response.jobId)
        assertNull(response.error)
    }

    @Test
    fun `test GodiddyOperationResponse with failure`() {
        val response = GodiddyOperationResponse(
            success = false,
            error = "Operation failed"
        )
        
        assertFalse(response.success)
        assertEquals("Operation failed", response.error)
        assertNull(response.did)
    }

    @Test
    fun `test GodiddyIssueCredentialRequest`() {
        val credential = buildJsonObject {
            put("type", buildJsonArray { add("VerifiableCredential") })
            put("issuer", "did:key:issuer")
        }
        val request = GodiddyIssueCredentialRequest(
            credential = credential,
            options = mapOf("proofType" to JsonPrimitive("Ed25519Signature2020"))
        )
        
        assertNotNull(request.credential)
        assertEquals(1, request.options.size)
    }

    @Test
    fun `test GodiddyIssueCredentialRequest with empty options`() {
        val credential = buildJsonObject {
            put("type", buildJsonArray { add("VerifiableCredential") })
        }
        val request = GodiddyIssueCredentialRequest(credential = credential)
        
        assertTrue(request.options.isEmpty())
    }

    @Test
    fun `test GodiddyIssueCredentialResponse with credential`() {
        val credential = buildJsonObject {
            put("id", "cred-123")
        }
        val response = GodiddyIssueCredentialResponse(credential = credential)
        
        assertNotNull(response.credential)
        assertNull(response.error)
    }

    @Test
    fun `test GodiddyIssueCredentialResponse with error`() {
        val response = GodiddyIssueCredentialResponse(error = "Issuance failed")
        
        assertNull(response.credential)
        assertEquals("Issuance failed", response.error)
    }

    @Test
    fun `test GodiddyVerifyCredentialRequest`() {
        val credential = buildJsonObject {
            put("id", "cred-123")
        }
        val request = GodiddyVerifyCredentialRequest(
            credential = credential,
            options = mapOf("checkRevocation" to JsonPrimitive(true))
        )
        
        assertNotNull(request.credential)
        assertEquals(1, request.options.size)
    }

    @Test
    fun `test GodiddyVerifyCredentialResponse with verified true`() {
        val response = GodiddyVerifyCredentialResponse(
            verified = true,
            checks = mapOf("proof" to true, "expiration" to true)
        )
        
        assertTrue(response.verified)
        assertNotNull(response.checks)
        assertNull(response.error)
    }

    @Test
    fun `test GodiddyVerifyCredentialResponse with verified false`() {
        val response = GodiddyVerifyCredentialResponse(
            verified = false,
            error = "Verification failed",
            checks = mapOf("proof" to false)
        )
        
        assertFalse(response.verified)
        assertEquals("Verification failed", response.error)
        assertNotNull(response.checks)
    }

    @Test
    fun `test GodiddyErrorResponse with all fields`() {
        val response = GodiddyErrorResponse(
            error = "NotFound",
            message = "Resource not found",
            code = "404"
        )
        
        assertEquals("NotFound", response.error)
        assertEquals("Resource not found", response.message)
        assertEquals("404", response.code)
    }

    @Test
    fun `test GodiddyErrorResponse with defaults`() {
        val response = GodiddyErrorResponse(error = "Error")
        
        assertEquals("Error", response.error)
        assertNull(response.message)
        assertNull(response.code)
    }

    @Test
    fun `test GodiddyModels equality`() {
        val response1 = GodiddyErrorResponse(error = "Error", code = "500")
        val response2 = GodiddyErrorResponse(error = "Error", code = "500")
        
        assertEquals(response1, response2)
    }

    @Test
    fun `test GodiddyModels copy`() {
        val original = GodiddyErrorResponse(error = "Error")
        val copied = original.copy(message = "New message")
        
        assertEquals(original.error, copied.error)
        assertEquals("New message", copied.message)
    }
}



