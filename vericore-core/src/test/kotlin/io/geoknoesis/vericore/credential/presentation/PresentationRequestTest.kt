package io.geoknoesis.vericore.credential.presentation

import kotlin.test.*

/**
 * Tests for PresentationRequest and PresentationQuery data classes.
 */
class PresentationRequestTest {

    @Test
    fun `test PresentationRequest with all fields`() {
        val query = PresentationQuery(
            type = "CredentialQuery",
            credentialTypes = listOf("PersonCredential"),
            requiredFields = listOf("name", "email"),
            issuer = "did:key:issuer"
        )
        val request = PresentationRequest(
            id = "request-123",
            query = query,
            challenge = "challenge-123",
            domain = "example.com",
            expires = "2024-12-31T23:59:59Z"
        )
        
        assertEquals("request-123", request.id)
        assertEquals(query, request.query)
        assertEquals("challenge-123", request.challenge)
        assertEquals("example.com", request.domain)
        assertEquals("2024-12-31T23:59:59Z", request.expires)
    }

    @Test
    fun `test PresentationRequest with defaults`() {
        val query = PresentationQuery(type = "DIDAuthentication")
        val request = PresentationRequest(
            query = query,
            challenge = "challenge-123"
        )
        
        assertNotNull(request.id) // Auto-generated UUID
        assertEquals(query, request.query)
        assertEquals("challenge-123", request.challenge)
        assertNull(request.domain)
        assertNull(request.expires)
    }

    @Test
    fun `test PresentationRequest equality`() {
        val query = PresentationQuery(type = "CredentialQuery")
        val request1 = PresentationRequest(
            id = "request-123",
            query = query,
            challenge = "challenge-123"
        )
        val request2 = PresentationRequest(
            id = "request-123",
            query = query,
            challenge = "challenge-123"
        )
        
        assertEquals(request1, request2)
    }

    @Test
    fun `test PresentationRequest toString`() {
        val query = PresentationQuery(type = "DIDAuthentication")
        val request = PresentationRequest(
            query = query,
            challenge = "challenge-123"
        )
        
        val str = request.toString()
        assertTrue(str.contains("challenge-123"))
    }

    @Test
    fun `test PresentationRequest copy`() {
        val query = PresentationQuery(type = "DIDAuthentication")
        val request = PresentationRequest(
            query = query,
            challenge = "challenge-123"
        )
        
        val copied = request.copy(domain = "example.com")
        
        assertEquals(request.id, copied.id)
        assertEquals(request.query, copied.query)
        assertEquals(request.challenge, copied.challenge)
        assertEquals("example.com", copied.domain)
    }

    @Test
    fun `test PresentationQuery with all fields`() {
        val query = PresentationQuery(
            type = "CredentialQuery",
            credentialTypes = listOf("PersonCredential", "EmailCredential"),
            requiredFields = listOf("name", "email", "age"),
            issuer = "did:key:issuer"
        )
        
        assertEquals("CredentialQuery", query.type)
        assertEquals(2, query.credentialTypes?.size)
        assertEquals(3, query.requiredFields?.size)
        assertEquals("did:key:issuer", query.issuer)
    }

    @Test
    fun `test PresentationQuery with defaults`() {
        val query = PresentationQuery(type = "DIDAuthentication")
        
        assertEquals("DIDAuthentication", query.type)
        assertNull(query.credentialTypes)
        assertNull(query.requiredFields)
        assertNull(query.issuer)
    }

    @Test
    fun `test PresentationQuery with empty lists`() {
        val query = PresentationQuery(
            type = "CredentialQuery",
            credentialTypes = emptyList(),
            requiredFields = emptyList()
        )
        
        assertNotNull(query.credentialTypes)
        assertTrue(query.credentialTypes!!.isEmpty())
        assertNotNull(query.requiredFields)
        assertTrue(query.requiredFields!!.isEmpty())
    }

    @Test
    fun `test PresentationQuery equality`() {
        val query1 = PresentationQuery(
            type = "CredentialQuery",
            credentialTypes = listOf("PersonCredential"),
            issuer = "did:key:issuer"
        )
        val query2 = PresentationQuery(
            type = "CredentialQuery",
            credentialTypes = listOf("PersonCredential"),
            issuer = "did:key:issuer"
        )
        
        assertEquals(query1, query2)
    }

    @Test
    fun `test PresentationQuery toString`() {
        val query = PresentationQuery(
            type = "CredentialQuery",
            credentialTypes = listOf("PersonCredential")
        )
        
        val str = query.toString()
        assertTrue(str.contains("CredentialQuery"))
    }

    @Test
    fun `test PresentationQuery copy`() {
        val query = PresentationQuery(type = "DIDAuthentication")
        
        val copied = query.copy(
            credentialTypes = listOf("PersonCredential"),
            issuer = "did:key:issuer"
        )
        
        assertEquals(query.type, copied.type)
        assertEquals(1, copied.credentialTypes?.size)
        assertEquals("did:key:issuer", copied.issuer)
    }

    @Test
    fun `test PresentationRequest with DIDAuthentication query`() {
        val query = PresentationQuery(type = "DIDAuthentication")
        val request = PresentationRequest(
            query = query,
            challenge = "auth-challenge"
        )
        
        assertEquals("DIDAuthentication", request.query.type)
    }

    @Test
    fun `test PresentationRequest with multiple credential types`() {
        val query = PresentationQuery(
            type = "CredentialQuery",
            credentialTypes = listOf("PersonCredential", "EmailCredential", "PhoneCredential")
        )
        val request = PresentationRequest(
            query = query,
            challenge = "challenge-123"
        )
        
        assertEquals(3, request.query.credentialTypes?.size)
    }

    @Test
    fun `test PresentationRequest with multiple required fields`() {
        val query = PresentationQuery(
            type = "CredentialQuery",
            requiredFields = listOf("name", "email", "phone", "address")
        )
        val request = PresentationRequest(
            query = query,
            challenge = "challenge-123"
        )
        
        assertEquals(4, request.query.requiredFields?.size)
    }
}


