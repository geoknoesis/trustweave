package io.geoknoesis.vericore.credential.transform

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive branch coverage tests for CredentialTransformer.
 * Tests all methods, branches, and edge cases.
 */
class CredentialTransformerBranchCoverageTest {

    @Test
    fun `test CredentialTransformer toJwt returns JWT string`() = runBlocking {
        val transformer = CredentialTransformer()
        val credential = createTestCredential()
        
        val jwt = transformer.toJwt(credential)
        
        assertNotNull(jwt)
        assertTrue(jwt.isNotBlank())
    }

    @Test
    fun `test CredentialTransformer fromJwt returns credential`() = runBlocking {
        val transformer = CredentialTransformer()
        val credential = createTestCredential()
        val jwt = transformer.toJwt(credential)
        
        val restored = transformer.fromJwt(jwt)
        
        assertNotNull(restored)
        assertEquals(credential.issuer, restored.issuer)
    }

    @Test
    fun `test CredentialTransformer toJsonLd returns JSON object`() = runBlocking {
        val transformer = CredentialTransformer()
        val credential = createTestCredential()
        
        val jsonLd = transformer.toJsonLd(credential)
        
        assertNotNull(jsonLd)
        assertTrue(jsonLd.containsKey("issuer"))
        assertTrue(jsonLd.containsKey("type"))
    }

    @Test
    fun `test CredentialTransformer fromJsonLd returns credential`() = runBlocking {
        val transformer = CredentialTransformer()
        val credential = createTestCredential()
        val jsonLd = transformer.toJsonLd(credential)
        
        val restored = transformer.fromJsonLd(jsonLd)
        
        assertNotNull(restored)
        assertEquals(credential.issuer, restored.issuer)
    }

    @Test
    fun `test CredentialTransformer toCbor returns bytes`() = runBlocking {
        val transformer = CredentialTransformer()
        val credential = createTestCredential()
        
        val cbor = transformer.toCbor(credential)
        
        assertNotNull(cbor)
        assertTrue(cbor.isNotEmpty())
    }

    @Test
    fun `test CredentialTransformer fromCbor returns credential`() = runBlocking {
        val transformer = CredentialTransformer()
        val credential = createTestCredential()
        val cbor = transformer.toCbor(credential)
        
        val restored = transformer.fromCbor(cbor)
        
        assertNotNull(restored)
        assertEquals(credential.issuer, restored.issuer)
    }

    @Test
    fun `test CredentialTransformer toJwt with proof`() = runBlocking {
        val transformer = CredentialTransformer()
        val credential = createTestCredential(
            proof = io.geoknoesis.vericore.credential.models.Proof(
                type = "Ed25519Signature2020",
                created = java.time.Instant.now().toString(),
                verificationMethod = "did:key:issuer#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "test-proof"
            )
        )
        
        val jwt = transformer.toJwt(credential)
        
        assertNotNull(jwt)
    }

    @Test
    fun `test CredentialTransformer toJsonLd with all fields`() = runBlocking {
        val transformer = CredentialTransformer()
        val credential = createTestCredential(
            expirationDate = java.time.Instant.now().plusSeconds(86400).toString(),
            credentialStatus = io.geoknoesis.vericore.credential.models.CredentialStatus(
                id = "status-list-1",
                type = "StatusList2021Entry"
            ),
            proof = io.geoknoesis.vericore.credential.models.Proof(
                type = "Ed25519Signature2020",
                created = java.time.Instant.now().toString(),
                verificationMethod = "did:key:issuer#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "test-proof"
            )
        )
        
        val jsonLd = transformer.toJsonLd(credential)
        
        assertNotNull(jsonLd)
    }

    @Test
    fun `test CredentialTransformer round trip JWT`() = runBlocking {
        val transformer = CredentialTransformer()
        val original = createTestCredential()
        
        val jwt = transformer.toJwt(original)
        val restored = transformer.fromJwt(jwt)
        
        assertEquals(original.issuer, restored.issuer)
        assertEquals(original.type, restored.type)
    }

    @Test
    fun `test CredentialTransformer round trip JSON-LD`() = runBlocking {
        val transformer = CredentialTransformer()
        val original = createTestCredential()
        
        val jsonLd = transformer.toJsonLd(original)
        val restored = transformer.fromJsonLd(jsonLd)
        
        assertEquals(original.issuer, restored.issuer)
        assertEquals(original.type, restored.type)
    }

    @Test
    fun `test CredentialTransformer round trip CBOR`() = runBlocking {
        val transformer = CredentialTransformer()
        val original = createTestCredential()
        
        val cbor = transformer.toCbor(original)
        val restored = transformer.fromCbor(cbor)
        
        assertEquals(original.issuer, restored.issuer)
        assertEquals(original.type, restored.type)
    }

    private fun createTestCredential(
        id: String? = null,
        types: List<String> = listOf("VerifiableCredential", "PersonCredential"),
        issuerDid: String = "did:key:issuer",
        subject: JsonObject = buildJsonObject {
            put("id", "did:key:subject")
            put("name", "John Doe")
        },
        issuanceDate: String = java.time.Instant.now().toString(),
        expirationDate: String? = null,
        credentialStatus: io.geoknoesis.vericore.credential.models.CredentialStatus? = null,
        proof: io.geoknoesis.vericore.credential.models.Proof? = null
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = types,
            issuer = issuerDid,
            credentialSubject = subject,
            issuanceDate = issuanceDate,
            expirationDate = expirationDate,
            credentialStatus = credentialStatus,
            proof = proof
        )
    }
}


