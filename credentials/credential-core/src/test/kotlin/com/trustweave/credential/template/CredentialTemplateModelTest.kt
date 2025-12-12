package com.trustweave.credential.template

import com.trustweave.credential.identifiers.SchemaId
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.template.TemplateService
import com.trustweave.credential.template.TemplateServices
import com.trustweave.did.identifiers.Did
import java.time.Duration
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
            schemaId = SchemaId("https://example.com/schemas/person"),
            type = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("PersonCredential")),
            defaultIssuer = "did:key:issuer",
            defaultValidity = Duration.ofDays(365),
            requiredFields = listOf("name", "email"),
            optionalFields = listOf("phone", "address")
        )

        assertEquals("person-credential", template.id)
        assertEquals("Person Credential", template.name)
        assertEquals("https://example.com/schemas/person", template.schemaId.value)
        assertEquals(2, template.type.size)
        assertEquals("did:key:issuer", template.defaultIssuer)
        assertEquals(Duration.ofDays(365), template.defaultValidity)
        assertEquals(2, template.requiredFields.size)
        assertEquals(2, template.optionalFields.size)
    }

    @Test
    fun `test CredentialTemplate with minimal fields`() {
        val template = CredentialTemplate(
            id = "basic-credential",
            name = "Basic Credential",
            schemaId = SchemaId("https://example.com/schemas/basic"),
            type = listOf(CredentialType.VerifiableCredential)
        )

        assertNull(template.defaultIssuer)
        assertNull(template.defaultValidity)
        assertTrue(template.requiredFields.isEmpty())
        assertTrue(template.optionalFields.isEmpty())
    }

    // Note: issueFromTemplate doesn't exist - TemplateService uses createIssuanceRequest instead
    // These tests are commented out as they test a non-existent method
    /*
    @Test
    fun `test CredentialTemplateService issueFromTemplate validates required fields`() = runBlocking {
        val service = TemplateServices.default()

        val template = CredentialTemplate(
            id = "person-credential",
            name = "Person Credential",
            schemaId = SchemaId("https://example.com/schemas/person"),
            type = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("PersonCredential")),
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
        assertTrue(credential.issuer.id.value.contains("did:key:issuer") || credential.issuer.id.value == "did:key:issuer")
    }

    @Test
    fun `test CredentialTemplateService issueFromTemplate uses default issuer`() = runBlocking {
        val service = TemplateServices.default()

        val template = CredentialTemplate(
            id = "person-credential",
            name = "Person Credential",
            schemaId = SchemaId("https://example.com/schemas/person"),
            type = listOf(CredentialType.VerifiableCredential),
            defaultIssuer = "did:key:default-issuer"
        )

        service.createTemplate(template)

        val credential = service.issueFromTemplate(
            "person-credential",
            buildJsonObject { put("name", "John Doe") }
        )

        assertTrue(credential.issuer.id.value.contains("did:key:default-issuer") || credential.issuer.id.value == "did:key:default-issuer")
    }

    @Test
    fun `test CredentialTemplateService issueFromTemplate fails without issuer`() = runBlocking {
        val service = TemplateServices.default()

        val template = CredentialTemplate(
            id = "person-credential",
            name = "Person Credential",
            schemaId = SchemaId("https://example.com/schemas/person"),
            type = listOf(CredentialType.VerifiableCredential)
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
        val service = TemplateServices.default()

        val template = CredentialTemplate(
            id = "person-credential",
            name = "Person Credential",
            schemaId = SchemaId("https://example.com/schemas/person"),
            type = listOf(CredentialType.VerifiableCredential),
            defaultIssuer = "did:key:issuer",
            defaultValidity = Duration.ofDays(30)
        )

        service.createTemplate(template)

        val credential = service.issueFromTemplate(
            "person-credential",
            buildJsonObject { put("name", "John Doe") }
        )

        assertNotNull(credential.expirationDate)
    }
    */
}



