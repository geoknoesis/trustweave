package com.geoknoesis.vericore.credential.template

import com.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive branch coverage tests for CredentialTemplateService.
 * Tests all methods, branches, and edge cases.
 */
class CredentialTemplateServiceBranchCoverageTest {

    private lateinit var service: CredentialTemplateService

    @BeforeEach
    fun setup() {
        service = CredentialTemplateService()
    }

    @Test
    fun `test CredentialTemplateService createTemplate stores template`() = runBlocking {
        val template = createTestTemplate()
        
        val created = service.createTemplate(template)
        
        assertEquals(template, created)
        assertEquals(template, service.getTemplate(template.id))
    }

    @Test
    fun `test CredentialTemplateService getTemplate returns null for non-existent`() {
        assertNull(service.getTemplate("non-existent"))
    }

    @Test
    fun `test CredentialTemplateService issueFromTemplate with all required fields`() = runBlocking {
        val template = createTestTemplate(requiredFields = listOf("name", "email"))
        service.createTemplate(template)
        
        val subject = buildJsonObject {
            put("name", "John Doe")
            put("email", "john@example.com")
        }
        
        val credential = service.issueFromTemplate(
            template.id,
            subject,
            mapOf("issuer" to "did:key:issuer")
        )
        
        assertNotNull(credential)
        assertEquals("did:key:issuer", credential.issuer)
        assertEquals(template.type, credential.type)
    }

    @Test
    fun `test CredentialTemplateService issueFromTemplate throws when template not found`() = runBlocking {
        val subject = buildJsonObject {
            put("name", "John Doe")
        }
        
        assertFailsWith<IllegalArgumentException> {
            service.issueFromTemplate("non-existent", subject)
        }
    }

    @Test
    fun `test CredentialTemplateService issueFromTemplate throws when required field missing`() = runBlocking {
        val template = createTestTemplate(requiredFields = listOf("name", "email"))
        service.createTemplate(template)
        
        val subject = buildJsonObject {
            put("name", "John Doe")
            // Missing email
        }
        
        assertFailsWith<IllegalArgumentException> {
            service.issueFromTemplate(template.id, subject, mapOf("issuer" to "did:key:issuer"))
        }
    }

    @Test
    fun `test CredentialTemplateService issueFromTemplate uses default issuer`() = runBlocking {
        val template = createTestTemplate(defaultIssuer = "did:key:default-issuer")
        service.createTemplate(template)
        
        val subject = buildJsonObject {
            put("name", "John Doe")
        }
        
        val credential = service.issueFromTemplate(template.id, subject)
        
        assertEquals("did:key:default-issuer", credential.issuer)
    }

    @Test
    fun `test CredentialTemplateService issueFromTemplate uses options issuer over default`() = runBlocking {
        val template = createTestTemplate(defaultIssuer = "did:key:default-issuer")
        service.createTemplate(template)
        
        val subject = buildJsonObject {
            put("name", "John Doe")
        }
        
        val credential = service.issueFromTemplate(
            template.id,
            subject,
            mapOf("issuer" to "did:key:custom-issuer")
        )
        
        assertEquals("did:key:custom-issuer", credential.issuer)
    }

    @Test
    fun `test CredentialTemplateService issueFromTemplate throws when no issuer`() = runBlocking {
        val template = createTestTemplate(defaultIssuer = null)
        service.createTemplate(template)
        
        val subject = buildJsonObject {
            put("name", "John Doe")
        }
        
        assertFailsWith<IllegalArgumentException> {
            service.issueFromTemplate(template.id, subject)
        }
    }

    @Test
    fun `test CredentialTemplateService issueFromTemplate calculates expiration date`() = runBlocking {
        val template = createTestTemplate(defaultValidityDays = 365)
        service.createTemplate(template)
        
        val subject = buildJsonObject {
            put("name", "John Doe")
        }
        
        val credential = service.issueFromTemplate(
            template.id,
            subject,
            mapOf("issuer" to "did:key:issuer")
        )
        
        assertNotNull(credential.expirationDate)
    }

    @Test
    fun `test CredentialTemplateService issueFromTemplate with custom ID`() = runBlocking {
        val template = createTestTemplate()
        service.createTemplate(template)
        
        val subject = buildJsonObject {
            put("name", "John Doe")
        }
        
        val credential = service.issueFromTemplate(
            template.id,
            subject,
            mapOf("issuer" to "did:key:issuer", "id" to "custom-credential-id")
        )
        
        assertEquals("custom-credential-id", credential.id)
    }

    @Test
    fun `test CredentialTemplateService issueFromTemplate without expiration`() = runBlocking {
        val template = createTestTemplate(defaultValidityDays = null)
        service.createTemplate(template)
        
        val subject = buildJsonObject {
            put("name", "John Doe")
        }
        
        val credential = service.issueFromTemplate(
            template.id,
            subject,
            mapOf("issuer" to "did:key:issuer")
        )
        
        assertNull(credential.expirationDate)
    }

    @Test
    fun `test CredentialTemplateService listTemplates returns all templates`() = runBlocking {
        val template1 = createTestTemplate(id = "template-1")
        val template2 = createTestTemplate(id = "template-2")
        service.createTemplate(template1)
        service.createTemplate(template2)
        
        val templates = service.listTemplates()
        
        assertEquals(2, templates.size)
    }

    @Test
    fun `test CredentialTemplateService deleteTemplate returns true`() = runBlocking {
        val template = createTestTemplate()
        service.createTemplate(template)
        
        val deleted = service.deleteTemplate(template.id)
        
        assertTrue(deleted)
        assertNull(service.getTemplate(template.id))
    }

    @Test
    fun `test CredentialTemplateService deleteTemplate returns false`() {
        val deleted = service.deleteTemplate("non-existent")
        
        assertFalse(deleted)
    }

    @Test
    fun `test CredentialTemplateService clear removes all templates`() = runBlocking {
        val template1 = createTestTemplate(id = "template-1")
        val template2 = createTestTemplate(id = "template-2")
        service.createTemplate(template1)
        service.createTemplate(template2)
        
        service.clear()
        
        assertTrue(service.listTemplates().isEmpty())
    }

    @Test
    fun `test CredentialTemplateService issueFromTemplate with optional fields`() = runBlocking {
        val template = createTestTemplate(
            requiredFields = listOf("name"),
            optionalFields = listOf("email", "phone")
        )
        service.createTemplate(template)
        
        val subject = buildJsonObject {
            put("name", "John Doe")
            put("email", "john@example.com")
            put("phone", "123-456-7890")
        }
        
        val credential = service.issueFromTemplate(
            template.id,
            subject,
            mapOf("issuer" to "did:key:issuer")
        )
        
        assertNotNull(credential)
    }

    private fun createTestTemplate(
        id: String = "test-template",
        name: String = "Test Template",
        schemaId: String = "schema-1",
        type: List<String> = listOf("VerifiableCredential", "PersonCredential"),
        defaultIssuer: String? = null,
        defaultValidityDays: Int? = null,
        requiredFields: List<String> = emptyList(),
        optionalFields: List<String> = emptyList()
    ): CredentialTemplate {
        return CredentialTemplate(
            id = id,
            name = name,
            schemaId = schemaId,
            type = type,
            defaultIssuer = defaultIssuer,
            defaultValidityDays = defaultValidityDays,
            requiredFields = requiredFields,
            optionalFields = optionalFields
        )
    }
}
