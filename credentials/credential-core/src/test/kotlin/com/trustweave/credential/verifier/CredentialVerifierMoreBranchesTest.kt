package com.trustweave.credential.verifier

import com.trustweave.credential.CredentialVerificationOptions
import com.trustweave.credential.models.CredentialSchema
import com.trustweave.credential.models.CredentialStatus
import com.trustweave.credential.models.Proof
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.schema.JsonSchemaValidator
import com.trustweave.credential.schema.SchemaRegistry
import com.trustweave.credential.schema.SchemaValidatorRegistry
import com.trustweave.credential.SchemaFormat
import com.trustweave.util.booleanDidResolver
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.*

/**
 * Additional comprehensive branch coverage tests for CredentialVerifier.
 * Covers additional branches not covered in CredentialVerifierBranchCoverageTest and CredentialVerifierAdditionalBranchCoverageTest.
 */
class CredentialVerifierMoreBranchesTest {

    private lateinit var verifier: CredentialVerifier
    private val issuerDid = "did:key:issuer123"

    @BeforeEach
    fun setup() {
        SchemaRegistry.clear()
        SchemaValidatorRegistry.clear()
        SchemaValidatorRegistry.register(JsonSchemaValidator())
        verifier = CredentialVerifier(
            booleanDidResolver { did -> did == issuerDid }
        )
    }

    @AfterEach
    fun cleanup() {
        SchemaRegistry.clear()
        SchemaValidatorRegistry.clear()
    }

    // ========== Options Branches ==========

    @Test
    fun `test verify with options checkExpiration false`() = runBlocking {
        val credential = createTestCredential(
            expirationDate = Instant.now().minusSeconds(86400).toString() // Expired
        )
        val options = CredentialVerificationOptions(checkExpiration = false)
        
        val result = verifier.verify(credential, options)
        
        // Should not check expiration
        assertTrue(result.valid || !result.errors.any { it.contains("expired") || it.contains("expiration") })
    }

    @Test
    fun `test verify with options checkRevocation false`() = runBlocking {
        val credential = createTestCredential(
            credentialStatus = CredentialStatus(
                id = "https://example.com/status/1",
                type = "StatusList2021Entry",
                statusListIndex = "1",
                statusListCredential = "https://example.com/status/1"
            )
        )
        val options = CredentialVerificationOptions(checkRevocation = false)
        
        val result = verifier.verify(credential, options)
        
        // Should not check revocation
        assertTrue(result.valid || !result.errors.any { it.contains("revoked") || it.contains("revocation") })
    }

    @Test
    fun `test verify with options validateSchema false`() = runBlocking {
        val schemaId = "https://example.com/schemas/person"
        val schema = CredentialSchema(
            id = schemaId,
            type = "JsonSchemaValidator2018",
            schemaFormat = SchemaFormat.JSON_SCHEMA
        )
        val credential = createTestCredential(schema = schema)
        val options = CredentialVerificationOptions(validateSchema = false)
        
        val result = verifier.verify(credential, options)
        
        // Should not validate schema
        assertTrue(result.valid || !result.errors.any { it.contains("schema") })
    }

    @Test
    fun `test verify with options verifyBlockchainAnchor false`() = runBlocking {
        val credential = createTestCredential(
            evidence = listOf(
                com.trustweave.credential.models.Evidence(
                    id = "evidence-1",
                    type = listOf("BlockchainAnchorEvidence"),
                    evidenceDocument = buildJsonObject {
                        put("id", "https://example.com/evidence/1")
                    }
                )
            )
        )
        val options = CredentialVerificationOptions(verifyBlockchainAnchor = false)
        
        val result = verifier.verify(credential, options)
        
        // Should not verify blockchain anchor
        assertTrue(result.valid || !result.errors.any { it.contains("blockchain") || it.contains("anchor") })
    }

    @Test
    fun `test verify with all options disabled`() = runBlocking {
        val credential = createTestCredential()
        val options = CredentialVerificationOptions(
            checkExpiration = false,
            checkRevocation = false,
            validateSchema = false,
            verifyBlockchainAnchor = false
        )
        
        val result = verifier.verify(credential, options)
        
        assertNotNull(result)
    }

    // ========== Expiration Date Branches ==========

    @Test
    fun `test verify with expirationDate in future`() = runBlocking {
        val credential = createTestCredential(
            expirationDate = Instant.now().plusSeconds(86400).toString()
        )
        
        val result = verifier.verify(credential)
        
        assertTrue(result.valid || !result.errors.any { it.contains("expired") })
    }

    @Test
    fun `test verify with expirationDate exactly now`() = runBlocking {
        val credential = createTestCredential(
            expirationDate = Instant.now().toString()
        )
        
        val result = verifier.verify(credential)
        
        assertNotNull(result)
    }

    @Test
    fun `test verify with expirationDate in past`() = runBlocking {
        val credential = createTestCredential(
            expirationDate = Instant.now().minusSeconds(86400).toString()
        )
        
        val result = verifier.verify(credential)
        
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("expired") || it.contains("expiration") })
    }

    @Test
    fun `test verify with invalid expirationDate format`() = runBlocking {
        val credential = createTestCredential(
            expirationDate = "invalid-date-format"
        )
        
        val result = verifier.verify(credential)
        
        // Should handle gracefully
        assertNotNull(result)
    }

    // ========== Credential Status Branches ==========

    @Test
    fun `test verify with credentialStatus having null statusListIndex`() = runBlocking {
        val credential = createTestCredential(
            credentialStatus = CredentialStatus(
                id = "https://example.com/status/1",
                type = "StatusList2021Entry",
                statusListIndex = null,
                statusListCredential = "https://example.com/status/1"
            )
        )
        
        val result = verifier.verify(credential)
        
        assertNotNull(result)
    }

    @Test
    fun `test verify with credentialStatus having empty statusListIndex`() = runBlocking {
        val credential = createTestCredential(
            credentialStatus = CredentialStatus(
                id = "https://example.com/status/1",
                type = "StatusList2021Entry",
                statusListIndex = "",
                statusListCredential = "https://example.com/status/1"
            )
        )
        
        val result = verifier.verify(credential)
        
        assertNotNull(result)
    }

    @Test
    fun `test verify with credentialStatus having null statusListCredential`() = runBlocking {
        val credential = createTestCredential(
            credentialStatus = CredentialStatus(
                id = "https://example.com/status/1",
                type = "StatusList2021Entry",
                statusListIndex = "1",
                statusListCredential = null
            )
        )
        
        val result = verifier.verify(credential)
        
        assertNotNull(result)
    }

    // ========== Schema Validation Branches ==========

    @Test
    fun `test verify with schema not registered`() = runBlocking {
        val schemaId = "https://example.com/schemas/person"
        val schema = CredentialSchema(
            id = schemaId,
            type = "JsonSchemaValidator2018",
            schemaFormat = SchemaFormat.JSON_SCHEMA
        )
        val credential = createTestCredential(schema = schema)
        
        val result = verifier.verify(credential)
        
        // Should handle gracefully
        assertNotNull(result)
    }

    @Test
    fun `test verify with schema registered but definition missing`() = runBlocking {
        val schemaId = "https://example.com/schemas/person"
        val schema = CredentialSchema(
            id = schemaId,
            type = "JsonSchemaValidator2018",
            schemaFormat = SchemaFormat.JSON_SCHEMA
        )
        
        runBlocking {
            SchemaRegistry.registerSchema(schema, buildJsonObject { })
        }
        
        val credential = createTestCredential(schema = schema)
        
        val result = verifier.verify(credential)
        
        // Should handle gracefully
        assertNotNull(result)
    }

    // ========== DID Resolution Branches ==========

    @Test
    fun `test verify with resolveDid returning false for issuer`() = runBlocking {
        val verifierWithFailedResolution = CredentialVerifier(
            booleanDidResolver { false }
        )
        
        val credential = createTestCredential()
        
        val result = verifierWithFailedResolution.verify(credential)
        
        assertFalse(result.valid)
        assertFalse(result.issuerValid)
    }

    @Test
    fun `test verify with resolveDid returning true for different DID`() = runBlocking {
        val verifierWithDifferentResolution = CredentialVerifier(
            booleanDidResolver { did -> did == "did:key:different" }
        )
        
        val credential = createTestCredential()
        
        val result = verifierWithDifferentResolution.verify(credential)
        
        assertFalse(result.valid)
        assertFalse(result.issuerValid)
    }

    // ========== Proof Verification Branches ==========

    @Test
    fun `test verify with proof having both proofValue and jws`() = runBlocking {
        val credential = createTestCredential(
            proof = Proof(
                type = "Ed25519Signature2020",
                created = Instant.now().toString(),
                verificationMethod = "did:key:issuer123#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "proof-value-123",
                jws = "jws-value-123"
            )
        )
        
        val result = verifier.verify(credential)
        
        assertNotNull(result)
    }

    @Test
    fun `test verify with proof having only proofValue`() = runBlocking {
        val credential = createTestCredential(
            proof = Proof(
                type = "Ed25519Signature2020",
                created = Instant.now().toString(),
                verificationMethod = "did:key:issuer123#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "proof-value-123",
                jws = null
            )
        )
        
        val result = verifier.verify(credential)
        
        assertNotNull(result)
    }

    @Test
    fun `test verify with proof having only jws`() = runBlocking {
        val credential = createTestCredential(
            proof = Proof(
                type = "Ed25519Signature2020",
                created = Instant.now().toString(),
                verificationMethod = "did:key:issuer123#key-1",
                proofPurpose = "assertionMethod",
                proofValue = null,
                jws = "jws-value-123"
            )
        )
        
        val result = verifier.verify(credential)
        
        assertNotNull(result)
    }

    // ========== Helper Functions ==========

    private fun createTestCredential(
        id: String = "credential-123",
        issuerDid: String = this.issuerDid,
        types: List<String> = listOf("VerifiableCredential", "PersonCredential"),
        credentialSubject: JsonObject = buildJsonObject {
            put("id", "did:key:subject123")
            put("name", "John Doe")
        },
        expirationDate: String? = null,
        credentialStatus: CredentialStatus? = null,
        schema: CredentialSchema? = null,
        proof: Proof? = Proof(
            type = "Ed25519Signature2020",
            created = Instant.now().toString(),
            verificationMethod = "did:key:issuer123#key-1",
            proofPurpose = "assertionMethod"
        ),
        evidence: List<com.trustweave.credential.models.Evidence>? = null
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = types,
            issuer = issuerDid,
            issuanceDate = Instant.now().toString(),
            expirationDate = expirationDate,
            credentialSubject = credentialSubject,
            credentialStatus = credentialStatus,
            evidence = evidence,
            credentialSchema = schema,
            proof = proof
        )
    }
}

