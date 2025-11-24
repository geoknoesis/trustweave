package com.trustweave.credential.models

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
            id = "evidence-1",
            type = listOf("DocumentVerification", "IdentityVerification"),
            evidenceDocument = buildJsonObject {
                put("type", "passport")
                put("country", "US")
            },
            verifier = "did:key:verifier",
            evidenceDate = "2024-01-01T00:00:00Z"
        )
        
        assertEquals("evidence-1", evidence.id)
        assertEquals(2, evidence.type.size)
        assertNotNull(evidence.evidenceDocument)
        assertEquals("did:key:verifier", evidence.verifier)
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
            id = "https://example.com/status/1",
            type = "StatusList2021Entry",
            statusPurpose = "revocation",
            statusListIndex = "0",
            statusListCredential = "https://example.com/status-list/1"
        )
        
        assertEquals("https://example.com/status/1", status.id)
        assertEquals("StatusList2021Entry", status.type)
        assertEquals("revocation", status.statusPurpose)
        assertEquals("0", status.statusListIndex)
        assertEquals("https://example.com/status-list/1", status.statusListCredential)
    }

    @Test
    fun `test CredentialStatus with default purpose`() {
        val status = CredentialStatus(
            id = "https://example.com/status/1",
            type = "StatusList2021Entry"
        )
        
        assertEquals("revocation", status.statusPurpose)
        assertNull(status.statusListIndex)
        assertNull(status.statusListCredential)
    }

    @Test
    fun `test CredentialSchema model`() {
        val schema = CredentialSchema(
            id = "https://example.com/schemas/person",
            type = "JsonSchemaValidator2018",
            schemaFormat = SchemaFormat.JSON_SCHEMA
        )
        
        assertEquals("https://example.com/schemas/person", schema.id)
        assertEquals("JsonSchemaValidator2018", schema.type)
        assertEquals(com.trustweave.core.SchemaFormat.JSON_SCHEMA, schema.schemaFormat)
    }

    @Test
    fun `test CredentialSchema with default format`() {
        val schema = CredentialSchema(
            id = "https://example.com/schemas/person",
            type = "JsonSchemaValidator2018"
        )
        
        assertEquals(com.trustweave.core.SchemaFormat.JSON_SCHEMA, schema.schemaFormat)
    }

    @Test
    fun `test TermsOfUse model`() {
        val terms = TermsOfUse(
            id = "terms-1",
            type = "IssuerPolicy",
            termsOfUse = buildJsonObject {
                put("url", "https://example.com/terms")
                put("version", "1.0")
            }
        )
        
        assertEquals("terms-1", terms.id)
        assertEquals("IssuerPolicy", terms.type)
        assertNotNull(terms.termsOfUse)
    }

    @Test
    fun `test TermsOfUse with minimal fields`() {
        val terms = TermsOfUse(
            termsOfUse = buildJsonObject { put("url", "https://example.com/terms") }
        )
        
        assertNull(terms.id)
        assertNull(terms.type)
        assertNotNull(terms.termsOfUse)
    }

    @Test
    fun `test RefreshService model`() {
        val refreshService = RefreshService(
            id = "refresh-1",
            type = "CredentialRefreshService2020",
            serviceEndpoint = "https://example.com/refresh"
        )
        
        assertEquals("refresh-1", refreshService.id)
        assertEquals("CredentialRefreshService2020", refreshService.type)
        assertEquals("https://example.com/refresh", refreshService.serviceEndpoint)
    }
}


