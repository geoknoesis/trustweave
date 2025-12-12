package com.trustweave.credential.models

import com.trustweave.credential.model.Evidence
import com.trustweave.credential.model.vc.CredentialStatus
import com.trustweave.credential.model.vc.CredentialSchema
import com.trustweave.credential.model.vc.TermsOfUse
import com.trustweave.credential.model.vc.RefreshService
import com.trustweave.credential.model.SchemaFormat
import com.trustweave.credential.model.StatusPurpose
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.credential.identifiers.IssuerId
import com.trustweave.credential.identifiers.SchemaId
import com.trustweave.credential.identifiers.StatusListId
import com.trustweave.core.identifiers.Iri
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for Evidence, CredentialStatus, CredentialSchema, TermsOfUse, RefreshService models.
 */
class CredentialModelsTest {

    @Test
    fun `test Evidence model`() {
        val evidence = Evidence(
            id = CredentialId("evidence-1"),
            type = listOf("DocumentVerification", "IdentityVerification"),
            evidenceDocument = buildJsonObject {
                put("type", "passport")
                put("country", "US")
            },
            verifier = IssuerId("did:key:verifier"),
            evidenceDate = "2024-01-01T00:00:00Z"
        )

        assertEquals("evidence-1", evidence.id?.value)
        assertEquals(2, evidence.type.size)
        assertNotNull(evidence.evidenceDocument)
        assertEquals("did:key:verifier", evidence.verifier?.value)
        assertEquals("2024-01-01T00:00:00Z", evidence.evidenceDate)
    }

    @Test
    fun `test Evidence with minimal fields`() {
        val evidence = Evidence(
            type = listOf("DocumentVerification")
        )

        assertNull(evidence.id)
        assertNull(evidence.evidenceDocument)
        assertNull(evidence.verifier)
        assertNull(evidence.evidenceDate)
    }

    @Test
    fun `test CredentialStatus model`() {
        val status = CredentialStatus(
            id = StatusListId("https://example.com/status/1"),
            type = "StatusList2021Entry",
            statusPurpose = StatusPurpose.REVOCATION,
            statusListIndex = "0",
            statusListCredential = StatusListId("https://example.com/status-list/1")
        )

        assertEquals("https://example.com/status/1", status.id.value)
        assertEquals("StatusList2021Entry", status.type)
        assertEquals(StatusPurpose.REVOCATION, status.statusPurpose)
        assertEquals("0", status.statusListIndex)
        assertEquals("https://example.com/status-list/1", status.statusListCredential?.value)
    }

    @Test
    fun `test CredentialStatus with default purpose`() {
        val status = CredentialStatus(
            id = StatusListId("https://example.com/status/1"),
            type = "StatusList2021Entry"
        )

        assertEquals(StatusPurpose.REVOCATION, status.statusPurpose)
        assertNull(status.statusListIndex)
        assertNull(status.statusListCredential)
    }

    @Test
    fun `test CredentialSchema model`() {
        val schema = CredentialSchema(
            id = SchemaId("https://example.com/schemas/person"),
            type = "JsonSchemaValidator2018"
        )

        assertEquals("https://example.com/schemas/person", schema.id.value)
        assertEquals("JsonSchemaValidator2018", schema.type)
    }

    @Test
    fun `test CredentialSchema with default format`() {
        val schema = CredentialSchema(
            id = SchemaId("https://example.com/schemas/person"),
            type = "JsonSchemaValidator2018"
        )

        assertEquals("https://example.com/schemas/person", schema.id.value)
    }

    @Test
    fun `test TermsOfUse model`() {
        val terms = TermsOfUse(
            id = "terms-1",
            type = "IssuerPolicy",
            additionalProperties = mapOf(
                "url" to JsonPrimitive("https://example.com/terms"),
                "version" to JsonPrimitive("1.0")
            )
        )

        assertEquals("terms-1", terms.id)
        assertEquals("IssuerPolicy", terms.type)
        assertNotNull(terms.additionalProperties)
    }

    @Test
    fun `test TermsOfUse with minimal fields`() {
        val terms = TermsOfUse(
            additionalProperties = mapOf("url" to JsonPrimitive("https://example.com/terms"))
        )

        assertNull(terms.id)
        assertNull(terms.type)
        assertNotNull(terms.additionalProperties)
    }

    @Test
    fun `test RefreshService model`() {
        val refreshService = RefreshService(
            id = Iri("refresh-1"),
            type = "CredentialRefreshService2020"
        )

        assertEquals("refresh-1", refreshService.id.value)
        assertEquals("CredentialRefreshService2020", refreshService.type)
    }
}


