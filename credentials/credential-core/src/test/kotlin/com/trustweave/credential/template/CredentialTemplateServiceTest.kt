package com.trustweave.credential.template

import com.trustweave.credential.model.vc.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant as KotlinInstant
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant as KotlinInstant
import kotlin.time.Duration.Companion.seconds

/**
 * Comprehensive tests for CredentialTemplateService API.
 */
class CredentialTemplateServiceTest {

    private val service = CredentialTemplateService()

    @BeforeEach
    fun setup() {
        service.clear()
    }

    @AfterEach
    fun cleanup() {
        service.clear()
    }

    @Test
    fun `test create template`() = runBlocking {
        val template = CredentialTemplate(
            id = "person-template",
            name = "Person Credential Template",
            schemaId = "https://example.com/schemas/person",
            type = listOf("VerifiableCredential", "PersonCredential"),
            defaultIssuer = "did:key:issuer",
            defaultValidityDays = 365,
            requiredFields = listOf("name", "email")
        )

        val created = service.createTemplate(template)

        assertEquals(template.id, created.id)
        assertEquals(template.name, created.name)
    }

    @Test
    fun `test get template`() = runBlocking {
        val template = CredentialTemplate(
            id = "person-template",
            name = "Person Credential Template",
            schemaId = "https://example.com/schemas/person",
            type = listOf("VerifiableCredential", "PersonCredential")
        )

        service.createTemplate(template)

        val retrieved = service.getTemplate("person-template")

        assertNotNull(retrieved)
        assertEquals(template.id, retrieved?.id)
    }

    @Test
    fun `test get template returns null when not found`() = runBlocking {
        assertNull(service.getTemplate("nonexistent"))
    }

    @Test
    fun `test issue from template`() = runBlocking {
        val template = CredentialTemplate(
            id = "person-template",
            name = "Person Credential Template",
            schemaId = "https://example.com/schemas/person",
            type = listOf("VerifiableCredential", "PersonCredential"),
            defaultIssuer = "did:key:issuer",
            defaultValidityDays = 365,
            requiredFields = listOf("name", "email")
        )

        service.createTemplate(template)

        val subject = buildJsonObject {
            put("id", "did:key:subject")
            put("name", "John Doe")
            put("email", "john@example.com")
        }

        val credential = service.issueFromTemplate("person-template", subject)

        assertNotNull(credential)
        assertEquals("did:key:issuer", credential.issuer)
        assertEquals(template.type, credential.type)
        assertNotNull(credential.expirationDate)
        assertEquals(subject, credential.credentialSubject)
    }

    @Test
    fun `test issue from template fails when template not found`() = runBlocking {
        val subject = buildJsonObject {
            put("name", "John Doe")
        }

        assertFailsWith<IllegalArgumentException> {
            service.issueFromTemplate("nonexistent", subject)
        }
    }

    @Test
    fun `test issue from template fails when required field missing`() = runBlocking {
        val template = CredentialTemplate(
            id = "person-template",
            name = "Person Credential Template",
            schemaId = "https://example.com/schemas/person",
            type = listOf("VerifiableCredential", "PersonCredential"),
            requiredFields = listOf("name", "email")
        )

        service.createTemplate(template)

        val subject = buildJsonObject {
            put("name", "John Doe")
            // Missing "email" field
        }

        assertFailsWith<IllegalArgumentException> {
            service.issueFromTemplate("person-template", subject)
        }
    }

    @Test
    fun `test issue from template with custom issuer`() = runBlocking {
        val template = CredentialTemplate(
            id = "person-template",
            name = "Person Credential Template",
            schemaId = "https://example.com/schemas/person",
            type = listOf("VerifiableCredential", "PersonCredential"),
            defaultIssuer = "did:key:default-issuer"
        )

        service.createTemplate(template)

        val subject = buildJsonObject {
            put("name", "John Doe")
        }

        val credential = service.issueFromTemplate(
            templateId = "person-template",
            subject = subject,
            options = mapOf("issuer" to "did:key:custom-issuer")
        )

        assertEquals("did:key:custom-issuer", credential.issuer)
    }

    @Test
    fun `test issue from template fails when no issuer provided`() = runBlocking {
        val template = CredentialTemplate(
            id = "person-template",
            name = "Person Credential Template",
            schemaId = "https://example.com/schemas/person",
            type = listOf("VerifiableCredential", "PersonCredential")
            // No defaultIssuer
        )

        service.createTemplate(template)

        val subject = buildJsonObject {
            put("name", "John Doe")
        }

        assertFailsWith<IllegalArgumentException> {
            service.issueFromTemplate("person-template", subject)
        }
    }

    @Test
    fun `test issue from template with custom ID`() = runBlocking {
        val template = CredentialTemplate(
            id = "person-template",
            name = "Person Credential Template",
            schemaId = "https://example.com/schemas/person",
            type = listOf("VerifiableCredential", "PersonCredential"),
            defaultIssuer = "did:key:issuer"
        )

        service.createTemplate(template)

        val subject = buildJsonObject {
            put("name", "John Doe")
        }

        val credential = service.issueFromTemplate(
            templateId = "person-template",
            subject = subject,
            options = mapOf("id" to "https://example.com/credentials/custom-123")
        )

        assertEquals("https://example.com/credentials/custom-123", credential.id)
    }

    @Test
    fun `test issue from template calculates expiration date`() = runBlocking {
        val template = CredentialTemplate(
            id = "person-template",
            name = "Person Credential Template",
            schemaId = "https://example.com/schemas/person",
            type = listOf("VerifiableCredential", "PersonCredential"),
            defaultIssuer = "did:key:issuer",
            defaultValidityDays = 30
        )

        service.createTemplate(template)

        val subject = buildJsonObject {
            put("name", "John Doe")
        }

        val credential = service.issueFromTemplate("person-template", subject)

        assertNotNull(credential.expirationDate)
        val expiration = KotlinInstant.parse(credential.expirationDate!!)
        val now = Clock.System.now()
        val expectedExpiration = now.plus((30L * 24 * 60 * 60).seconds)

        // Allow 1 second tolerance
        assertTrue(kotlin.math.abs(expiration.epochSeconds - expectedExpiration.epochSeconds) <= 1)
    }

    @Test
    fun `test list templates`() = runBlocking {
        val template1 = CredentialTemplate(
            id = "template-1",
            name = "Template 1",
            schemaId = "schema-1",
            type = listOf("VerifiableCredential")
        )
        val template2 = CredentialTemplate(
            id = "template-2",
            name = "Template 2",
            schemaId = "schema-2",
            type = listOf("VerifiableCredential")
        )

        service.createTemplate(template1)
        service.createTemplate(template2)

        val templates = service.listTemplates()

        assertEquals(2, templates.size)
        assertTrue(templates.any { it.id == "template-1" })
        assertTrue(templates.any { it.id == "template-2" })
    }

    @Test
    fun `test delete template`() = runBlocking {
        val template = CredentialTemplate(
            id = "person-template",
            name = "Person Credential Template",
            schemaId = "https://example.com/schemas/person",
            type = listOf("VerifiableCredential", "PersonCredential")
        )

        service.createTemplate(template)
        assertNotNull(service.getTemplate("person-template"))

        val deleted = service.deleteTemplate("person-template")

        assertTrue(deleted)
        assertNull(service.getTemplate("person-template"))
    }

    @Test
    fun `test delete template returns false when not found`() = runBlocking {
        assertFalse(service.deleteTemplate("nonexistent"))
    }

    @Test
    fun `test clear all templates`() = runBlocking {
        val template1 = CredentialTemplate(
            id = "template-1",
            name = "Template 1",
            schemaId = "schema-1",
            type = listOf("VerifiableCredential")
        )
        val template2 = CredentialTemplate(
            id = "template-2",
            name = "Template 2",
            schemaId = "schema-2",
            type = listOf("VerifiableCredential")
        )

        service.createTemplate(template1)
        service.createTemplate(template2)
        assertEquals(2, service.listTemplates().size)

        service.clear()

        assertEquals(0, service.listTemplates().size)
    }

    @Test
    fun `test issue from template with optional fields`() = runBlocking {
        val template = CredentialTemplate(
            id = "person-template",
            name = "Person Credential Template",
            schemaId = "https://example.com/schemas/person",
            type = listOf("VerifiableCredential", "PersonCredential"),
            defaultIssuer = "did:key:issuer",
            requiredFields = listOf("name"),
            optionalFields = listOf("email", "phone")
        )

        service.createTemplate(template)

        val subject = buildJsonObject {
            put("name", "John Doe")
            put("email", "john@example.com")
            // phone is optional, not included
        }

        val credential = service.issueFromTemplate("person-template", subject)

        assertNotNull(credential)
        assertEquals("John Doe", credential.credentialSubject.jsonObject["name"]?.jsonPrimitive?.content)
    }
}

