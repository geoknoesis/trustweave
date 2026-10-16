package org.trustweave.credential.template.internal

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.identifiers.SchemaId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.template.CredentialTemplate
import org.trustweave.credential.template.TemplateServices
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class DefaultTemplateServiceTest {
    private val issuedAt = Instant.parse("2025-01-01T00:00:00Z")
    private val issuer = Issuer.IriIssuer(Iri("did:key:issuer"))

    private fun template(
        id: String = "t1",
        validity: kotlin.time.Duration? = null,
        required: List<String> = emptyList(),
    ) = CredentialTemplate(
        id = id,
        name = "Template $id",
        schemaId = SchemaId("https://example.com/schema"),
        type = listOf(CredentialType.VerifiableCredential),
        defaultValidity = validity,
        requiredFields = required,
    )

    private fun subject(vararg claims: String) =
        CredentialSubject(id = Iri("did:key:holder"), claims = claims.associateWith { JsonPrimitive("v") })

    private fun request(
        service: org.trustweave.credential.template.TemplateService,
        templateId: String = "t1",
        subject: CredentialSubject = subject(),
        validUntil: Instant? = null,
    ) = runBlocking { service.createIssuanceRequest(templateId, ProofSuiteId.VC_LD, issuer, subject, issuedAt, validUntil) }

    @Test
    fun `stores retrieves lists deletes and clears templates`() =
        runBlocking {
            val service = TemplateServices.default()
            assertNull(service.getTemplate("t1"))
            assertEquals(emptyList(), service.listTemplates())

            service.createTemplate(template("t1"))
            service.createTemplate(template("t2"))
            assertEquals("Template t1", service.getTemplate("t1")?.name)
            assertEquals(setOf("t1", "t2"), service.listTemplates().map { it.id }.toSet())

            assertTrue(service.deleteTemplate("t1"))
            assertFalse(service.deleteTemplate("t1"))
            assertNull(service.getTemplate("t1"))

            service.clear()
            assertEquals(emptyList(), service.listTemplates())
        }

    @Test
    fun `creating a template with an existing id replaces it`() =
        runBlocking {
            val service = TemplateServices.default()
            service.createTemplate(template("t1"))
            service.createTemplate(template("t1").copy(name = "Renamed"))
            assertEquals(1, service.listTemplates().size)
            assertEquals("Renamed", service.getTemplate("t1")?.name)
        }

    @Test
    fun `issuance request carries the template types and caller inputs`() {
        val service = TemplateServices.default()
        runBlocking { service.createTemplate(template()) }
        val subject = subject("name")

        val request = request(service, subject = subject)

        assertEquals(ProofSuiteId.VC_LD, request.format)
        assertEquals(listOf(CredentialType.VerifiableCredential), request.type)
        assertEquals(issuer, request.issuer)
        assertEquals(subject, request.credentialSubject)
        assertEquals(issuedAt, request.issuedAt)
        assertNull(request.validUntil)
    }

    @Test
    fun `unknown template id fails with the id in the message`() {
        val service = TemplateServices.default()
        val e = assertFailsWith<IllegalArgumentException> { request(service, "missing") }
        assertEquals("Template not found: missing", e.message)
    }

    @Test
    fun `missing required claim is rejected naming the field`() {
        val service = TemplateServices.default()
        runBlocking { service.createTemplate(template(required = listOf("name", "email"))) }

        val e = assertFailsWith<IllegalArgumentException> { request(service, subject = subject("name")) }
        assertEquals("Required field 'email' is missing in claims", e.message)

        // Supplying every required field (plus extras) passes.
        request(service, subject = subject("name", "email", "extra"))
    }

    @Test
    fun `default validity is added to issuedAt when no explicit validUntil is given`() {
        val service = TemplateServices.default()
        runBlocking {
            service.createTemplate(template("days", validity = 30.days))
            service.createTemplate(template("mixed", validity = 1.hours + 30.minutes))
        }
        assertEquals(issuedAt + 30.days, request(service, "days").validUntil)
        assertEquals(issuedAt + 90.minutes, request(service, "mixed").validUntil)
    }

    @Test
    fun `explicit validUntil overrides the template default`() {
        val service = TemplateServices.default()
        runBlocking { service.createTemplate(template(validity = 30.days)) }
        val explicit = Instant.parse("2025-02-01T00:00:00Z")
        assertEquals(explicit, request(service, validUntil = explicit).validUntil)
    }
}
