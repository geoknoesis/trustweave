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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import java.time.Duration

/**
 * Comprehensive branch coverage tests for TemplateService.
 * Tests all methods, branches, and edge cases.
 */
class CredentialTemplateServiceBranchCoverageTest {

    private lateinit var service: TemplateService

    @BeforeEach
    fun setup() = runBlocking {
        service = TemplateServices.default()
        service.clear()
    }

    @Test
    fun `test TemplateService createTemplate stores template`() = runBlocking {
        val template = createTestTemplate()

        service.createTemplate(template)

        val retrieved = service.getTemplate(template.id)
        assertNotNull(retrieved)
        assertEquals(template.id, retrieved?.id)
    }

    @Test
    fun `test TemplateService getTemplate returns null for non-existent`() = runBlocking {
        assertNull(service.getTemplate("non-existent"))
    }

    @Test
    fun `test TemplateService createIssuanceRequest with all required fields`() = runBlocking {
        val template = createTestTemplate(requiredFields = listOf("name", "email"))
        service.createTemplate(template)

        val claims = mapOf(
            "name" to JsonPrimitive("John Doe"),
            "email" to JsonPrimitive("john@example.com")
        )

        val request = service.createIssuanceRequest(
            templateId = template.id,
            format = ProofSuiteId.VC_LD,
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            credentialSubject = CredentialSubject.fromDid(Did("did:key:subject"), claims = claims)
        )

        assertNotNull(request)
        assertEquals(Issuer.fromDid(Did("did:key:issuer")), request.issuer)
        assertEquals(template.type, request.type)
    }

    @Test
    fun `test TemplateService createIssuanceRequest throws when template not found`() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            service.createIssuanceRequest(
                templateId = "non-existent",
                format = ProofSuiteId.VC_LD,
                issuer = Issuer.fromDid(Did("did:key:issuer")),
                credentialSubject = CredentialSubject.fromDid(Did("did:key:subject"), claims = mapOf("name" to JsonPrimitive("John Doe")))
            )
        }
    }

    @Test
    fun `test TemplateService createIssuanceRequest throws when required field missing`() = runBlocking {
        val template = createTestTemplate(requiredFields = listOf("name", "email"))
        service.createTemplate(template)

        assertFailsWith<IllegalArgumentException> {
            service.createIssuanceRequest(
                templateId = template.id,
                format = ProofSuiteId.VC_LD,
                issuer = Issuer.fromDid(Did("did:key:issuer")),
                credentialSubject = CredentialSubject.fromDid(Did("did:key:subject"), claims = mapOf("name" to JsonPrimitive("John Doe")))
                // Missing email
            )
        }
    }

    @Test
    fun `test TemplateService createIssuanceRequest with custom issuer`() = runBlocking {
        val template = createTestTemplate(defaultIssuer = "did:key:default-issuer")
        service.createTemplate(template)

        val request = service.createIssuanceRequest(
            templateId = template.id,
            format = ProofSuiteId.VC_LD,
            issuer = Issuer.fromDid(Did("did:key:custom-issuer")),
            credentialSubject = CredentialSubject.fromDid(Did("did:key:subject"), claims = mapOf("name" to JsonPrimitive("John Doe")))
        )

        assertEquals(Issuer.fromDid(Did("did:key:custom-issuer")), request.issuer)
    }

    @Test
    fun `test TemplateService createIssuanceRequest uses provided issuer`() = runBlocking {
        val template = createTestTemplate(defaultIssuer = "did:key:default-issuer")
        service.createTemplate(template)

        val request = service.createIssuanceRequest(
            templateId = template.id,
            format = ProofSuiteId.VC_LD,
            issuer = Issuer.fromDid(Did("did:key:custom-issuer")),
            credentialSubject = CredentialSubject.fromDid(Did("did:key:subject"), claims = mapOf("name" to JsonPrimitive("John Doe")))
        )

        assertEquals(Issuer.fromDid(Did("did:key:custom-issuer")), request.issuer)
    }

    @Test
    fun `test TemplateService createIssuanceRequest works without default issuer`() = runBlocking {
        val template = createTestTemplate(defaultIssuer = null)
        service.createTemplate(template)

        val request = service.createIssuanceRequest(
            templateId = template.id,
            format = ProofSuiteId.VC_LD,
            issuer = Issuer.fromDid(Did("did:key:custom-issuer")),
            credentialSubject = CredentialSubject.fromDid(Did("did:key:subject"), claims = mapOf("name" to JsonPrimitive("John Doe")))
        )

        assertNotNull(request)
    }

    @Test
    fun `test TemplateService createIssuanceRequest calculates expiration date`() = runBlocking {
        val template = createTestTemplate(defaultValidity = Duration.ofDays(365))
        service.createTemplate(template)

        val request = service.createIssuanceRequest(
            templateId = template.id,
            format = ProofSuiteId.VC_LD,
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            credentialSubject = CredentialSubject.fromDid(Did("did:key:subject"), claims = mapOf("name" to JsonPrimitive("John Doe")))
        )

        assertNotNull(request.validUntil)
    }

    @Test
    fun `test TemplateService createIssuanceRequest with custom issuedAt`() = runBlocking {
        val template = createTestTemplate()
        service.createTemplate(template)

        val customIssuedAt = kotlinx.datetime.Clock.System.now().minus(kotlin.time.Duration.parse("PT1D"))
        val request = service.createIssuanceRequest(
            templateId = template.id,
            format = ProofSuiteId.VC_LD,
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            credentialSubject = CredentialSubject.fromDid(Did("did:key:subject"), claims = mapOf("name" to JsonPrimitive("John Doe"))),
            issuedAt = customIssuedAt
        )

        assertEquals(customIssuedAt, request.issuedAt)
    }

    @Test
    fun `test TemplateService createIssuanceRequest without expiration`() = runBlocking {
        val template = createTestTemplate(defaultValidity = null)
        service.createTemplate(template)

        val request = service.createIssuanceRequest(
            templateId = template.id,
            format = ProofSuiteId.VC_LD,
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            credentialSubject = CredentialSubject.fromDid(Did("did:key:subject"), claims = mapOf("name" to JsonPrimitive("John Doe")))
        )

        assertNull(request.validUntil)
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
    fun `test TemplateService deleteTemplate returns false`() = runBlocking {
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
    fun `test TemplateService createIssuanceRequest with optional fields`() = runBlocking {
        val template = createTestTemplate(
            requiredFields = listOf("name"),
            optionalFields = listOf("email", "phone")
        )
        service.createTemplate(template)

        val claims = mapOf(
            "name" to JsonPrimitive("John Doe"),
            "email" to JsonPrimitive("john@example.com"),
            "phone" to JsonPrimitive("123-456-7890")
        )

        val request = service.createIssuanceRequest(
            templateId = template.id,
            format = ProofSuiteId.VC_LD,
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            credentialSubject = CredentialSubject.fromDid(Did("did:key:subject"), claims = claims)
        )

        assertNotNull(request)
    }

    private fun createTestTemplate(
        id: String = "test-template",
        name: String = "Test Template",
        schemaId: String = "schema-1",
        type: List<String> = listOf("VerifiableCredential", "PersonCredential"),
        defaultIssuer: String? = null,
        defaultValidity: Duration? = null,
        requiredFields: List<String> = emptyList(),
        optionalFields: List<String> = emptyList()
    ): CredentialTemplate {
        return CredentialTemplate(
            id = id,
            name = name,
            schemaId = SchemaId(schemaId),
            type = type.map { CredentialType.fromString(it) },
            defaultIssuer = defaultIssuer,
            defaultValidity = defaultValidity,
            requiredFields = requiredFields,
            optionalFields = optionalFields
        )
    }
}
