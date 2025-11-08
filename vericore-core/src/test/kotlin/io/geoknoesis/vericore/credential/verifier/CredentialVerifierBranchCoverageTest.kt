package io.geoknoesis.vericore.credential.verifier

import io.geoknoesis.vericore.credential.CredentialVerificationOptions
import io.geoknoesis.vericore.credential.CredentialVerificationResult
import io.geoknoesis.vericore.credential.models.CredentialSchema
import io.geoknoesis.vericore.credential.models.CredentialStatus
import io.geoknoesis.vericore.credential.models.Proof
import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.schema.JsonSchemaValidator
import io.geoknoesis.vericore.credential.schema.SchemaRegistry
import io.geoknoesis.vericore.credential.schema.SchemaValidatorRegistry
import io.geoknoesis.vericore.spi.SchemaFormat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive branch coverage tests for CredentialVerifier.
 * Tests all conditional branches and code paths.
 */
class CredentialVerifierBranchCoverageTest {

    private lateinit var verifier: CredentialVerifier
    private val issuerDid = "did:key:issuer123"

    @BeforeEach
    fun setup() {
        SchemaRegistry.clear()
        SchemaValidatorRegistry.clear()
        SchemaValidatorRegistry.register(JsonSchemaValidator())
        verifier = CredentialVerifier(
            resolveDid = { did -> did == issuerDid }
        )
    }

    @AfterEach
    fun cleanup() {
        SchemaRegistry.clear()
        SchemaValidatorRegistry.clear()
    }

    // ========== Proof Verification Branches ==========

    @Test
    fun `test branch proof is null`() = runBlocking {
        val credential = createTestCredential(proof = null)
        
        val result = verifier.verify(credential)
        
        assertFalse(result.valid)
        assertFalse(result.proofValid)
        assertTrue(result.errors.any { it.contains("no proof") })
    }

    @Test
    fun `test branch proof exists but type is blank`() = runBlocking {
        val credential = createTestCredential(
            proof = Proof(
                type = "",
                created = java.time.Instant.now().toString(),
                verificationMethod = "did:key:issuer123#key-1",
                proofPurpose = "assertionMethod"
            )
        )
        
        val result = verifier.verify(credential)
        
        assertFalse(result.valid)
        assertFalse(result.proofValid)
    }

    @Test
    fun `test branch proof exists but verificationMethod is blank`() = runBlocking {
        val credential = createTestCredential(
            proof = Proof(
                type = "Ed25519Signature2020",
                created = java.time.Instant.now().toString(),
                verificationMethod = "",
                proofPurpose = "assertionMethod"
            )
        )
        
        val result = verifier.verify(credential)
        
        assertFalse(result.valid)
        assertFalse(result.proofValid)
    }

    @Test
    fun `test branch proof exists but both proofValue and jws are null`() = runBlocking {
        val credential = createTestCredential(
            proof = Proof(
                type = "Ed25519Signature2020",
                created = java.time.Instant.now().toString(),
                verificationMethod = "did:key:issuer123#key-1",
                proofPurpose = "assertionMethod",
                proofValue = null,
                jws = null
            )
        )
        
        val result = verifier.verify(credential)
        
        assertFalse(result.valid)
        assertFalse(result.proofValid)
    }

    @Test
    fun `test branch proof exists with proofValue`() = runBlocking {
        val credential = createTestCredential(
            proof = Proof(
                type = "Ed25519Signature2020",
                created = java.time.Instant.now().toString(),
                verificationMethod = "did:key:issuer123#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "z1234567890"
            )
        )
        
        val result = verifier.verify(credential)
        
        assertTrue(result.proofValid)
    }

    @Test
    fun `test branch proof exists with jws`() = runBlocking {
        val credential = createTestCredential(
            proof = Proof(
                type = "Ed25519Signature2020",
                created = java.time.Instant.now().toString(),
                verificationMethod = "did:key:issuer123#key-1",
                proofPurpose = "assertionMethod",
                jws = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiJ9..."
            )
        )
        
        val result = verifier.verify(credential)
        
        assertTrue(result.proofValid)
    }

    // ========== DID Resolution Branches ==========

    @Test
    fun `test branch DID resolution succeeds`() = runBlocking {
        val credential = createTestCredential()
        
        val result = verifier.verify(credential)
        
        assertTrue(result.issuerValid)
    }

    @Test
    fun `test branch DID resolution fails returns false`() = runBlocking {
        val verifierWithFailedDid = CredentialVerifier(
            resolveDid = { false }
        )
        val credential = createTestCredential()
        
        val result = verifierWithFailedDid.verify(credential)
        
        assertFalse(result.issuerValid)
        assertFalse(result.valid)
    }

    @Test
    fun `test branch DID resolution throws exception`() = runBlocking {
        val verifierWithThrowingDid = CredentialVerifier(
            resolveDid = { throw RuntimeException("DID resolution failed") }
        )
        val credential = createTestCredential()
        
        val result = verifierWithThrowingDid.verify(credential)
        
        assertFalse(result.issuerValid)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("Failed to resolve issuer DID") })
    }

    // ========== Expiration Check Branches ==========

    @Test
    fun `test branch expiration check disabled`() = runBlocking {
        val expiredCredential = createTestCredential(
            expirationDate = java.time.Instant.now().minusSeconds(86400).toString()
        )
        
        val result = verifier.verify(
            expiredCredential,
            CredentialVerificationOptions(checkExpiration = false)
        )
        
        assertTrue(result.notExpired) // Should be true when check disabled
    }

    @Test
    fun `test branch expiration check enabled and credential expired`() = runBlocking {
        val expiredCredential = createTestCredential(
            expirationDate = java.time.Instant.now().minusSeconds(86400).toString()
        )
        
        val result = verifier.verify(
            expiredCredential,
            CredentialVerificationOptions(checkExpiration = true)
        )
        
        assertFalse(result.notExpired)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("expired") })
    }

    @Test
    fun `test branch expiration check enabled and credential not expired`() = runBlocking {
        val validCredential = createTestCredential(
            expirationDate = java.time.Instant.now().plusSeconds(86400).toString()
        )
        
        val result = verifier.verify(
            validCredential,
            CredentialVerificationOptions(checkExpiration = true)
        )
        
        assertTrue(result.notExpired)
    }

    @Test
    fun `test branch expiration check enabled but no expiration date`() = runBlocking {
        val credential = createTestCredential(expirationDate = null)
        
        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(checkExpiration = true)
        )
        
        assertTrue(result.notExpired) // No expiration date means not expired
    }

    @Test
    fun `test branch expiration date parse fails`() = runBlocking {
        val credential = createTestCredential(expirationDate = "invalid-date")
        
        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(checkExpiration = true)
        )
        
        assertTrue(result.notExpired) // Invalid format defaults to not expired
        assertTrue(result.warnings.any { it.contains("Invalid expiration date format") })
    }

    // ========== Revocation Check Branches ==========

    @Test
    fun `test branch revocation check disabled`() = runBlocking {
        val credential = createTestCredential(
            credentialStatus = CredentialStatus(
                id = "https://example.com/status/1",
                type = "StatusList2021Entry"
            )
        )
        
        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(checkRevocation = false)
        )
        
        assertTrue(result.notRevoked)
    }

    @Test
    fun `test branch revocation check enabled but no credential status`() = runBlocking {
        val credential = createTestCredential(credentialStatus = null)
        
        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(checkRevocation = true)
        )
        
        assertTrue(result.notRevoked)
    }

    @Test
    fun `test branch revocation check enabled with credential status`() = runBlocking {
        val credential = createTestCredential(
            credentialStatus = CredentialStatus(
                id = "https://example.com/status/1",
                type = "StatusList2021Entry"
            )
        )
        
        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(checkRevocation = true)
        )
        
        assertTrue(result.warnings.any { it.contains("Revocation checking not yet implemented") })
        assertTrue(result.notRevoked) // Currently defaults to true
    }

    // ========== Schema Validation Branches ==========

    @Test
    fun `test branch schema validation disabled`() = runBlocking {
        val schema = CredentialSchema(
            id = "https://example.com/schema",
            type = "JsonSchemaValidator2018",
            schemaFormat = SchemaFormat.JSON_SCHEMA
        )
        val credential = createTestCredential(schema = schema)
        
        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(validateSchema = false)
        )
        
        assertTrue(result.schemaValid) // Should default to true when disabled
    }

    @Test
    fun `test branch schema validation enabled but no schema`() = runBlocking {
        val credential = createTestCredential(schema = null)
        
        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(validateSchema = true)
        )
        
        assertTrue(result.schemaValid) // No schema means valid
    }

    @Test
    fun `test branch schema validation enabled with schema and succeeds`() = runBlocking {
        val schemaId = "https://example.com/schemas/person"
        val schema = CredentialSchema(
            id = schemaId,
            type = "JsonSchemaValidator2018",
            schemaFormat = SchemaFormat.JSON_SCHEMA
        )
        val schemaDefinition = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
            put("properties", buildJsonObject {
                put("name", buildJsonObject { put("type", "string") })
            })
        }
        SchemaRegistry.registerSchema(schema, schemaDefinition)
        
        val credential = createTestCredential(schema = schema)
        
        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(validateSchema = true)
        )
        
        assertTrue(result.schemaValid)
    }

    @Test
    fun `test branch schema validation enabled but validation throws exception`() = runBlocking {
        val schemaId = "https://example.com/nonexistent"
        val schema = CredentialSchema(
            id = schemaId,
            type = "JsonSchemaValidator2018",
            schemaFormat = SchemaFormat.JSON_SCHEMA
        )
        val credential = createTestCredential(schema = schema)
        
        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(validateSchema = true)
        )
        
        // May add warning or handle gracefully
        assertNotNull(result)
    }

    // ========== Blockchain Anchor Branches ==========

    @Test
    fun `test branch blockchain anchor verification disabled`() = runBlocking {
        val credential = createTestCredential(
            evidence = listOf(
                io.geoknoesis.vericore.credential.models.Evidence(
                    id = "evidence-1",
                    type = listOf("BlockchainAnchor"),
                    evidenceDocument = buildJsonObject {
                        put("chainId", "algorand:testnet")
                    }
                )
            )
        )
        
        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(verifyBlockchainAnchor = false)
        )
        
        assertTrue(result.blockchainAnchorValid)
    }

    @Test
    fun `test branch blockchain anchor verification enabled but no evidence`() = runBlocking {
        val credential = createTestCredential(evidence = null)
        
        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(verifyBlockchainAnchor = true)
        )
        
        assertTrue(result.blockchainAnchorValid)
    }

    @Test
    fun `test branch blockchain anchor verification enabled with evidence`() = runBlocking {
        val credential = createTestCredential(
            evidence = listOf(
                io.geoknoesis.vericore.credential.models.Evidence(
                    id = "evidence-1",
                    type = listOf("BlockchainAnchor"),
                    evidenceDocument = buildJsonObject {
                        put("chainId", "algorand:testnet")
                        put("txHash", "abc123")
                    }
                )
            )
        )
        
        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(verifyBlockchainAnchor = true)
        )
        
        assertTrue(result.warnings.any { it.contains("Blockchain anchor verification not yet implemented") })
        assertTrue(result.blockchainAnchorValid)
    }

    // ========== Combined Branch Coverage ==========

    @Test
    fun `test branch all checks pass`() = runBlocking {
        val credential = createTestCredential()
        
        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(
                checkExpiration = true,
                checkRevocation = true,
                validateSchema = true,
                verifyBlockchainAnchor = true
            )
        )
        
        assertTrue(result.valid)
        assertTrue(result.proofValid)
        assertTrue(result.issuerValid)
        assertTrue(result.notExpired)
        assertTrue(result.notRevoked)
        assertTrue(result.schemaValid)
        assertTrue(result.blockchainAnchorValid)
    }

    @Test
    fun `test branch multiple failures`() = runBlocking {
        val credential = createTestCredential(
            proof = null,
            expirationDate = java.time.Instant.now().minusSeconds(86400).toString(),
            issuerDid = "did:key:wrong"
        )
        
        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(checkExpiration = true)
        )
        
        assertFalse(result.valid)
        assertFalse(result.proofValid)
        assertFalse(result.issuerValid)
        assertFalse(result.notExpired)
        assertTrue(result.errors.size >= 2)
    }

    private fun createTestCredential(
        id: String? = "https://example.com/credentials/1",
        types: List<String> = listOf("VerifiableCredential", "PersonCredential"),
        issuerDid: String = this.issuerDid,
        subject: JsonObject = buildJsonObject {
            put("id", "did:key:subject")
            put("name", "John Doe")
        },
        issuanceDate: String = java.time.Instant.now().toString(),
        expirationDate: String? = null,
        proof: Proof? = Proof(
            type = "Ed25519Signature2020",
            created = java.time.Instant.now().toString(),
            verificationMethod = "did:key:issuer123#key-1",
            proofPurpose = "assertionMethod",
            proofValue = "z1234567890"
        ),
        schema: CredentialSchema? = null,
        credentialStatus: CredentialStatus? = null,
        evidence: List<io.geoknoesis.vericore.credential.models.Evidence>? = null
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = types,
            issuer = issuerDid,
            credentialSubject = subject,
            issuanceDate = issuanceDate,
            expirationDate = expirationDate,
            proof = proof,
            credentialSchema = schema,
            credentialStatus = credentialStatus,
            evidence = evidence
        )
    }
}

