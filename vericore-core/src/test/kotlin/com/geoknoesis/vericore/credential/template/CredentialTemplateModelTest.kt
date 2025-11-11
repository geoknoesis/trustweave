package com.geoknoesis.vericore.credential.template

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for CredentialTemplate model and additional edge cases for CredentialTemplateService.
 */
class CredentialTemplateModelTest {

    @Test
    fun `test CredentialTemplate with all fields`() {
        val template = CredentialTemplate(
            id = "person-credential",
            name = "Person Credential",
            schemaId = "https://example.com/schemas/person",
            type = listOf("VerifiableCredential", "PersonCredential"),
            defaultIssuer = "did:key:issuer",
            defaultValidityDays = 365,
            requiredFields = listOf("name", "email"),
            optionalFields = listOf("phone", "address")
        )
        
        assertEquals("person-credential", template.id)
        assertEquals("Person Credential", template.name)
        assertEquals("https://example.com/schemas/person", template.schemaId)
        assertEquals(2, template.type.size)
        assertEquals("did:key:issuer", template.defaultIssuer)
        assertEquals(365, template.defaultValidityDays)
        assertEquals(2, template.requiredFields.size)
        assertEquals(2, template.optionalFields.size)
    }

    @Test
    fun `test CredentialTemplate with minimal fields`() {
        val template = CredentialTemplate(
            id = "basic-credential",
            name = "Basic Credential",
            schemaId = "https://example.com/schemas/basic",
            type = listOf("VerifiableCredential")
        )
        
        assertNull(template.defaultIssuer)
        assertNull(template.defaultValidityDays)
        assertTrue(template.requiredFields.isEmpty())
        assertTrue(template.optionalFields.isEmpty())
    }

    @Test
    fun `test CredentialTemplateService issueFromTemplate validates required fields`() = runBlocking {
        val service = CredentialTemplateService()
        
        val template = CredentialTemplate(
            id = "person-credential",
            name = "Person Credential",
            schemaId = "https://example.com/schemas/person",
            type = listOf("VerifiableCredential", "PersonCredential"),
            requiredFields = listOf("name", "email")
        )
        
        service.createTemplate(template)
        
        // Missing required field
        assertFailsWith<IllegalArgumentException> {
            service.issueFromTemplate(
                "person-credential",
                buildJsonObject { put("name", "John Doe") }
            )
        }
        
        // All required fields present
        val credential = service.issueFromTemplate(
            "person-credential",
            buildJsonObject {
                put("name", "John Doe")
                put("email", "john@example.com")
            },
            mapOf("issuer" to "did:key:issuer")
        )
        
        assertNotNull(credential)
        assertEquals("did:key:issuer", credential.issuer)
    }

    @Test
    fun `test CredentialTemplateService issueFromTemplate uses default issuer`() = runBlocking {
        val service = CredentialTemplateService()
        
        val template = CredentialTemplate(
            id = "person-credential",
            name = "Person Credential",
            schemaId = "https://example.com/schemas/person",
            type = listOf("VerifiableCredential"),
            defaultIssuer = "did:key:default-issuer"
        )
        
        service.createTemplate(template)
        
        val credential = service.issueFromTemplate(
            "person-credential",
            buildJsonObject { put("name", "John Doe") }
        )
        
        assertEquals("did:key:default-issuer", credential.issuer)
    }

    @Test
    fun `test CredentialTemplateService issueFromTemplate fails without issuer`() = runBlocking {
        val service = CredentialTemplateService()
        
        val template = CredentialTemplate(
            id = "person-credential",
            name = "Person Credential",
            schemaId = "https://example.com/schemas/person",
            type = listOf("VerifiableCredential")
        )
        
        service.createTemplate(template)
        
        assertFailsWith<IllegalArgumentException> {
            service.issueFromTemplate(
                "person-credential",
                buildJsonObject { put("name", "John Doe") }
            )
        }
    }

    @Test
    fun `test CredentialTemplateService issueFromTemplate calculates expiration date`() = runBlocking {
        val service = CredentialTemplateService()
        
        val template = CredentialTemplate(
            id = "person-credential",
            name = "Person Credential",
            schemaId = "https://example.com/schemas/person",
            type = listOf("VerifiableCredential"),
            defaultIssuer = "did:key:issuer",
            defaultValidityDays = 30
        )
        
        service.createTemplate(template)
        
        val credential = service.issueFromTemplate(
            "person-credential",
            buildJsonObject { put("name", "John Doe") }
        )
        
        assertNotNull(credential.expirationDate)
    }
}



