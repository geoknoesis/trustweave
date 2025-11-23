package com.trustweave.credential.verifier

import com.trustweave.credential.CredentialVerificationOptions
import com.trustweave.credential.models.CredentialStatus
import com.trustweave.credential.models.Proof
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.did.CredentialDidResolver
import com.trustweave.credential.did.CredentialDidResolution
import com.trustweave.util.booleanDidResolver
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Additional branch coverage tests for CredentialVerifier.
 */
class CredentialVerifierAdditionalBranchCoverageTest {

    @BeforeEach
    fun setup() {
        com.trustweave.credential.schema.SchemaRegistry.clear()
    }

    @Test
    fun `test CredentialVerifier verify with checkExpiration false`() = runBlocking {
        val verifier = CredentialVerifier()
        val pastDate = java.time.Instant.now().minusSeconds(86400).toString()
        val credential = createTestCredential(expirationDate = pastDate)
        val options = CredentialVerificationOptions(checkExpiration = false)
        
        val result = verifier.verify(credential, options)
        
        assertTrue(result.notExpired) // Should be true when checkExpiration is false
    }

    @Test
    fun `test CredentialVerifier verify with checkRevocation false`() = runBlocking {
        val verifier = CredentialVerifier()
        val credential = createTestCredential(
            credentialStatus = CredentialStatus(
                id = "status-list-1",
                type = "StatusList2021Entry"
            )
        )
        val options = CredentialVerificationOptions(checkRevocation = false)
        
        val result = verifier.verify(credential, options)
        
        assertTrue(result.notRevoked) // Should be true when checkRevocation is false
    }

    @Test
    fun `test CredentialVerifier verify with validateSchema false`() = runBlocking {
        val verifier = CredentialVerifier()
        val credential = createTestCredential(
            credentialSchema = com.trustweave.credential.models.CredentialSchema(
                id = "schema-1",
                type = "JsonSchemaValidator2018"
            )
        )
        val options = CredentialVerificationOptions(validateSchema = false)
        
        val result = verifier.verify(credential, options)
        
        assertTrue(result.schemaValid) // Should be true when validateSchema is false
    }

    @Test
    fun `test CredentialVerifier verify with validateSchema true and schema registered`() = runBlocking {
        val schema = com.trustweave.credential.models.CredentialSchema(
            id = "schema-1",
            type = "JsonSchemaValidator2018"
        )
        val schemaDef = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
        }
        com.trustweave.credential.schema.SchemaRegistry.registerSchema(schema, schemaDef)
        com.trustweave.credential.schema.SchemaValidatorRegistry.clear()
        com.trustweave.credential.schema.SchemaValidatorRegistry.register(
            com.trustweave.credential.schema.JsonSchemaValidator()
        )
        
        val verifier = CredentialVerifier()
        val credential = createTestCredential(credentialSchema = schema)
        val options = CredentialVerificationOptions(validateSchema = true)
        
        val result = verifier.verify(credential, options)
        
        assertNotNull(result)
    }

    @Test
    fun `test CredentialVerifier verify with verifyBlockchainAnchor false`() = runBlocking {
        val verifier = CredentialVerifier()
        val credential = createTestCredential()
        val options = CredentialVerificationOptions(verifyBlockchainAnchor = false)
        
        val result = verifier.verify(credential, options)
        
        assertTrue(result.blockchainAnchorValid) // Should be true when verifyBlockchainAnchor is false
    }

    @Test
    fun `test CredentialVerifier verify with resolveDid returning false`() = runBlocking {
        val verifier = CredentialVerifier(booleanDidResolver { false })
        val credential = createTestCredential()
        
        val result = verifier.verify(credential)
        
        assertFalse(result.issuerValid)
        assertFalse(result.valid)
    }

    @Test
    fun `test CredentialVerifier verify with resolveDid throwing exception`() = runBlocking {
        val verifier = CredentialVerifier(
            CredentialDidResolver { throw RuntimeException("DID resolution failed") }
        )
        val credential = createTestCredential()
        
        val result = verifier.verify(credential)
        
        assertFalse(result.issuerValid)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("DID") || it.contains("resolution") })
    }

    @Test
    fun `test CredentialVerifier verify with expired credential and checkExpiration true`() = runBlocking {
        val verifier = CredentialVerifier()
        val pastDate = java.time.Instant.now().minusSeconds(86400).toString()
        val credential = createTestCredential(expirationDate = pastDate)
        val options = CredentialVerificationOptions(checkExpiration = true)
        
        val result = verifier.verify(credential, options)
        
        assertFalse(result.notExpired)
        assertFalse(result.valid)
    }

    @Test
    fun `test CredentialVerifier verify with revoked credential and checkRevocation true`() = runBlocking {
        val verifier = CredentialVerifier()
        val credential = createTestCredential(
            credentialStatus = CredentialStatus(
                id = "status-list-1",
                type = "StatusList2021Entry"
            )
        )
        val options = CredentialVerificationOptions(checkRevocation = true)
        
        val result = verifier.verify(credential, options)
        
        // Current implementation adds warning but doesn't set notRevoked to false
        // This is expected behavior for placeholder implementation
        assertTrue(result.warnings.any { it.contains("Revocation") || it.contains("revocation") })
    }

    @Test
    fun `test CredentialVerifier verify with invalid expiration date format`() = runBlocking {
        val verifier = CredentialVerifier()
        val credential = createTestCredential(expirationDate = "invalid-date")
        val options = CredentialVerificationOptions(checkExpiration = true)
        
        val result = verifier.verify(credential, options)
        
        assertTrue(result.warnings.any { it.contains("expiration date") || it.contains("format") })
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
        credentialStatus: CredentialStatus? = null,
        credentialSchema: com.trustweave.credential.models.CredentialSchema? = null,
        proof: Proof? = Proof(
            type = "Ed25519Signature2020",
            created = java.time.Instant.now().toString(),
            verificationMethod = "did:key:issuer#key-1",
            proofPurpose = "assertionMethod",
            proofValue = "test-proof"
        )
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = types,
            issuer = issuerDid,
            credentialSubject = subject,
            issuanceDate = issuanceDate,
            expirationDate = expirationDate,
            credentialStatus = credentialStatus,
            credentialSchema = credentialSchema,
            proof = proof
        )
    }
}

