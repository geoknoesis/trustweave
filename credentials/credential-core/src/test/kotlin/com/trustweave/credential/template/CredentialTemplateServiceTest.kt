package com.trustweave.credential.template

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.identifiers.SchemaId
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.format.ProofSuiteId
import com.trustweave.did.identifiers.Did
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days
import java.time.Duration

/**
 * Comprehensive tests for TemplateService API.
 */
class CredentialTemplateServiceTest {

    private lateinit var service: TemplateService

    @BeforeEach
    fun setup() = runBlocking {
        service = TemplateServices.default()
        service.clear()
    }

    @AfterEach
    fun cleanup() = runBlocking {
        service.clear()
    }

    @Test
    fun `test create template`() = runBlocking {
        val template = CredentialTemplate(
            id = "person-template",
            name = "Person Credential Template",
            schemaId = SchemaId("https://example.com/schemas/person"),
            type = listOf(CredentialType.fromString("VerifiableCredential"), CredentialType.fromString("PersonCredential")),
            defaultIssuer = "did:key:issuer",
            defaultValidity = Duration.ofDays(365),
            requiredFields = listOf("name", "email")
        )

        service.createTemplate(template)

        val created = service.getTemplate("person-template")
        assertNotNull(created)
        assertEquals(template.id, created?.id)
        assertEquals(template.name, created?.name)
    }

    @Test
    fun `test get template`() = runBlocking {
        val template = CredentialTemplate(
            id = "person-template",
            name = "Person Credential Template",
            schemaId = SchemaId("https://example.com/schemas/person"),
            type = listOf(CredentialType.fromString("VerifiableCredential"), CredentialType.fromString("PersonCredential"))
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
    fun `test create issuance request from template`() = runBlocking {
        val template = CredentialTemplate(
            id = "person-template",
            name = "Person Credential Template",
            schemaId = SchemaId("https://example.com/schemas/person"),
            type = listOf(CredentialType.fromString("VerifiableCredential"), CredentialType.fromString("PersonCredential")),
            defaultIssuer = "did:key:issuer",
            defaultValidity = Duration.ofDays(365),
            requiredFields = listOf("name", "email")
        )

        service.createTemplate(template)

        val subjectClaims = mapOf(
            "name" to JsonPrimitive("John Doe"),
            "email" to JsonPrimitive("john@example.com")
        )

        val request = service.createIssuanceRequest(
            templateId = "person-template",
            format = ProofSuiteId.VC_LD,
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            credentialSubject = CredentialSubject.fromDid(Did("did:key:subject"), claims = subjectClaims)
        )

        assertNotNull(request)
        assertEquals(template.type, request.type)
        assertNotNull(request.validUntil)
    }

    @Test
    fun `test create issuance request fails when template not found`() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            service.createIssuanceRequest(
                templateId = "nonexistent",
                format = ProofSuiteId.VC_LD,
                issuer = Issuer.fromDid(Did("did:key:issuer")),
                credentialSubject = CredentialSubject.fromDid(Did("did:key:subject"), claims = mapOf("name" to JsonPrimitive("John Doe")))
            )
        }
    }

    @Test
    fun `test create issuance request fails when required field missing`() = runBlocking {
        val template = CredentialTemplate(
            id = "person-template",
            name = "Person Credential Template",
            schemaId = SchemaId("https://example.com/schemas/person"),
            type = listOf(CredentialType.fromString("VerifiableCredential"), CredentialType.fromString("PersonCredential")),
            requiredFields = listOf("name", "email")
        )

        service.createTemplate(template)

        assertFailsWith<IllegalArgumentException> {
            service.createIssuanceRequest(
                templateId = "person-template",
                format = ProofSuiteId.VC_LD,
                issuer = Issuer.fromDid(Did("did:key:issuer")),
                credentialSubject = CredentialSubject.fromDid(Did("did:key:subject"), claims = mapOf("name" to JsonPrimitive("John Doe")))
                // Missing "email" field
            )
        }
    }

    @Test
    fun `test create issuance request with custom issuer`() = runBlocking {
        val template = CredentialTemplate(
            id = "person-template",
            name = "Person Credential Template",
            schemaId = SchemaId("https://example.com/schemas/person"),
            type = listOf(CredentialType.fromString("VerifiableCredential"), CredentialType.fromString("PersonCredential")),
            defaultIssuer = "did:key:default-issuer"
        )

        service.createTemplate(template)

        val request = service.createIssuanceRequest(
            templateId = "person-template",
            format = ProofSuiteId.VC_LD,
            issuer = Issuer.fromDid(Did("did:key:custom-issuer")),
            credentialSubject = CredentialSubject.fromDid(Did("did:key:subject"), claims = mapOf("name" to JsonPrimitive("John Doe")))
        )

        assertEquals(Issuer.fromDid(Did("did:key:custom-issuer")), request.issuer)
    }

    @Test
    fun `test create issuance request works without default issuer`() = runBlocking {
        val template = CredentialTemplate(
            id = "person-template",
            name = "Person Credential Template",
            schemaId = SchemaId("https://example.com/schemas/person"),
            type = listOf(CredentialType.fromString("VerifiableCredential"), CredentialType.fromString("PersonCredential"))
            // No defaultIssuer
        )

        service.createTemplate(template)

        val request = service.createIssuanceRequest(
            templateId = "person-template",
            format = ProofSuiteId.VC_LD,
            issuer = Issuer.fromDid(Did("did:key:custom-issuer")),
            credentialSubject = CredentialSubject.fromDid(Did("did:key:subject"), claims = mapOf("name" to JsonPrimitive("John Doe")))
        )

        assertNotNull(request)
    }

    @Test
    fun `test create issuance request with custom issuedAt`() = runBlocking {
        val template = CredentialTemplate(
            id = "person-template",
            name = "Person Credential Template",
            schemaId = SchemaId("https://example.com/schemas/person"),
            type = listOf(CredentialType.fromString("VerifiableCredential"), CredentialType.fromString("PersonCredential")),
            defaultIssuer = "did:key:issuer"
        )

        service.createTemplate(template)

        val customIssuedAt = Clock.System.now().minus(1.days)
        val request = service.createIssuanceRequest(
            templateId = "person-template",
            format = ProofSuiteId.VC_LD,
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            credentialSubject = CredentialSubject.fromDid(Did("did:key:subject"), claims = mapOf("name" to JsonPrimitive("John Doe"))),
            issuedAt = customIssuedAt
        )

        assertEquals(customIssuedAt, request.issuedAt)
    }

    @Test
    fun `test create issuance request calculates expiration date from template`() = runBlocking {
        val template = CredentialTemplate(
            id = "person-template",
            name = "Person Credential Template",
            schemaId = SchemaId("https://example.com/schemas/person"),
            type = listOf(CredentialType.fromString("VerifiableCredential"), CredentialType.fromString("PersonCredential")),
            defaultIssuer = "did:key:issuer",
            defaultValidity = Duration.ofDays(30)
        )

        service.createTemplate(template)

        val now = Clock.System.now()
        val request = service.createIssuanceRequest(
            templateId = "person-template",
            format = ProofSuiteId.VC_LD,
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            credentialSubject = CredentialSubject.fromDid(Did("did:key:subject"), claims = mapOf("name" to JsonPrimitive("John Doe"))),
            issuedAt = now
        )

        assertNotNull(request.validUntil)
        val expectedExpiration = now.plus(30.days)
        // Allow 1 second tolerance
        assertTrue(kotlin.math.abs((request.validUntil!!.epochSeconds - expectedExpiration.epochSeconds)) <= 1)
    }

    @Test
    fun `test list templates`() = runBlocking {
        val template1 = CredentialTemplate(
            id = "template-1",
            name = "Template 1",
            schemaId = SchemaId("schema-1"),
            type = listOf(CredentialType.fromString("VerifiableCredential"))
        )
        val template2 = CredentialTemplate(
            id = "template-2",
            name = "Template 2",
            schemaId = SchemaId("schema-2"),
            type = listOf(CredentialType.fromString("VerifiableCredential"))
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
            schemaId = SchemaId("https://example.com/schemas/person"),
            type = listOf(CredentialType.fromString("VerifiableCredential"), CredentialType.fromString("PersonCredential"))
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
            schemaId = SchemaId("schema-1"),
            type = listOf(CredentialType.fromString("VerifiableCredential"))
        )
        val template2 = CredentialTemplate(
            id = "template-2",
            name = "Template 2",
            schemaId = SchemaId("schema-2"),
            type = listOf(CredentialType.fromString("VerifiableCredential"))
        )

        service.createTemplate(template1)
        service.createTemplate(template2)
        assertEquals(2, service.listTemplates().size)

        service.clear()

        assertEquals(0, service.listTemplates().size)
    }

    @Test
    fun `test create issuance request with optional fields`() = runBlocking {
        val template = CredentialTemplate(
            id = "person-template",
            name = "Person Credential Template",
            schemaId = SchemaId("https://example.com/schemas/person"),
            type = listOf(CredentialType.fromString("VerifiableCredential"), CredentialType.fromString("PersonCredential")),
            defaultIssuer = "did:key:issuer",
            requiredFields = listOf("name"),
            optionalFields = listOf("email", "phone")
        )

        service.createTemplate(template)

        val claims = mapOf(
            "name" to JsonPrimitive("John Doe"),
            "email" to JsonPrimitive("john@example.com")
            // phone is optional, not included
        )

        val request = service.createIssuanceRequest(
            templateId = "person-template",
            format = ProofSuiteId.VC_LD,
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            credentialSubject = CredentialSubject.fromDid(Did("did:key:subject"), claims = claims)
        )

        assertNotNull(request)
        assertEquals("John Doe", request.credentialSubject.claims["name"]?.jsonPrimitive?.content)
    }
}

