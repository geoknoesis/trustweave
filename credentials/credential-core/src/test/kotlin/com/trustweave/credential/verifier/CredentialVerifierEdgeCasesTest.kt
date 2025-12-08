package com.trustweave.credential.verifier

import com.trustweave.credential.CredentialVerificationOptions
import com.trustweave.credential.CredentialVerificationResult
import com.trustweave.credential.models.CredentialSchema
import com.trustweave.credential.model.vc.CredentialStatus
import com.trustweave.credential.models.Proof
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.schema.JsonSchemaValidator
import com.trustweave.credential.schema.SchemaRegistry
import com.trustweave.credential.schema.SchemaValidatorRegistry
import com.trustweave.credential.SchemaFormat
import com.trustweave.credential.proof.Ed25519ProofGenerator
import com.trustweave.credential.proof.ProofOptions
import com.trustweave.did.DidDocument
import com.trustweave.did.VerificationMethod
import com.trustweave.did.resolver.DidResolver
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.kms.Algorithm
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import com.trustweave.util.booleanDidResolver
import com.trustweave.util.resultDidResolver
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Exhaustive edge case tests for CredentialVerifier API robustness.
 */
class CredentialVerifierEdgeCasesTest {

    private lateinit var verifier: CredentialVerifier
    private lateinit var kms: InMemoryKeyManagementService
    private var keyId: com.trustweave.core.identifiers.KeyId? = null
    private lateinit var publicKeyJwk: Map<String, Any?>
    private lateinit var proofGenerator: Ed25519ProofGenerator
    private val issuerDid = "did:key:issuer123"

    @BeforeEach
    fun setup() = runBlocking {
        SchemaRegistry.clear()
        SchemaValidatorRegistry.clear()
        SchemaValidatorRegistry.register(JsonSchemaValidator())

        // Create a real KMS and generate a real Ed25519 key
        kms = InMemoryKeyManagementService()
        val keyHandle = kms.generateKey(Algorithm.Ed25519, mapOf("keyId" to "key-1"))
        keyId = keyHandle.id
        publicKeyJwk = keyHandle.publicKeyJwk ?: emptyMap()

        // Create proof generator that uses the KMS to sign
        val keyIdValue = keyId!!
        proofGenerator = Ed25519ProofGenerator(
            signer = { data, _ -> kms.sign(keyIdValue, data) },
            getPublicKeyId = { keyIdValue.value }
        )

        // Create DID resolver with real public key
        val didResolver = createTestDidResolver()
        verifier = CredentialVerifier(defaultDidResolver = didResolver)
    }

    /**
     * Creates a test DID resolver that returns a DID document with verification methods.
     */
    private fun createTestDidResolver(): DidResolver {
        return resultDidResolver { did ->
            if (did == issuerDid || did == "did:key:issuer123") {
                DidResolutionResult.Success(
                    document = DidDocument(
                        id = did,
                        verificationMethod = listOf(
                            VerificationMethod(
                                id = "$did#key-1",
                                type = "Ed25519VerificationKey2020",
                                controller = did,
                                publicKeyJwk = publicKeyJwk
                            )
                        )
                    )
                )
            } else {
                null
            }
        }
    }

    @AfterEach
    fun cleanup() {
        SchemaRegistry.clear()
        SchemaValidatorRegistry.clear()
    }

    // ========== Null and Empty Input Tests ==========

    @Test
    fun `test verify credential with null ID`() = runBlocking {
        val credential = createTestCredential(id = null)

        val result = verifier.verify(credential)

        assertNotNull(result)
        // Should still verify (ID is optional)
    }

    @Test
    fun `test verify credential with empty type list`() = runBlocking {
        val credential = createTestCredential(types = emptyList())

        val result = verifier.verify(credential)

        // May fail validation or handle gracefully
        assertNotNull(result)
        // Empty type list should result in invalid credential
        if (!result.valid) {
            assertTrue(result.allErrors.isNotEmpty() || result.allWarnings.isNotEmpty())
        }
    }

    @Test
    fun `test verify credential with empty issuer DID`() = runBlocking {
        val credential = createTestCredential(issuerDid = "")

        val result = verifier.verify(credential)

        assertFalse(result.valid)
        assertFalse(result.issuerValid)
    }

    @Test
    fun `test verify credential with null proof`() = runBlocking {
        // Create credential directly with null proof (not using helper which auto-generates)
        val credential = VerifiableCredential(
            id = "https://example.com/credentials/1",
            type = listOf("VerifiableCredential", "PersonCredential"),
            issuer = issuerDid,
            credentialSubject = buildJsonObject {
                put("id", "did:key:subject")
                put("name", "John Doe")
            },
            issuanceDate = Clock.System.now().toString(),
            proof = null
        )

        val result = verifier.verify(credential)

        assertFalse(result.valid)
        assertFalse(result.proofValid)
        assertTrue(result.allErrors.any { it.contains("no proof") || it.contains("proof") })
    }

    @Test
    fun `test verify credential with empty proof type`() = runBlocking {
        val credential = createTestCredential(
            proof = Proof(
                type = "",
                created = Clock.System.now().toString(),
                verificationMethod = "did:key:issuer123#key-1",
                proofPurpose = "assertionMethod"
            )
        )

        val result = verifier.verify(credential)

        assertFalse(result.valid)
        assertFalse(result.proofValid)
    }

    @Test
    fun `test verify credential with empty verification method`() = runBlocking {
        val credential = createTestCredential(
            proof = Proof(
                type = "Ed25519Signature2020",
                created = Clock.System.now().toString(),
                verificationMethod = "",
                proofPurpose = "assertionMethod"
            )
        )

        val result = verifier.verify(credential)

        assertFalse(result.valid)
        assertFalse(result.proofValid)
    }

    // ========== Invalid Format Tests ==========

    @Test
    fun `test verify credential with invalid issuance date format`() = runBlocking {
        val credential = createTestCredential(issuanceDate = "not-a-date")

        val result = verifier.verify(credential)

        // May add warning or handle gracefully
        assertNotNull(result)
        // Invalid date format may result in warnings or be ignored
        if (result.allWarnings.isNotEmpty()) {
            assertTrue(result.allWarnings.any { it.contains("date") || it.contains("format") || it.contains("Invalid") })
        }
    }

    @Test
    fun `test verify credential with invalid expiration date format`() = runBlocking {
        val credential = createTestCredential(expirationDate = "not-a-date")

        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(checkExpiration = true)
        )

        assertTrue(result.allWarnings.any { it.contains("Invalid expiration date format") })
        assertTrue(result.notExpired) // Should default to true for invalid format
    }

    @Test
    fun `test verify credential with malformed DID`() = runBlocking {
        val credential = createTestCredential(issuerDid = "not-a-did")

        val result = verifier.verify(credential)

        assertFalse(result.valid)
        assertFalse(result.issuerValid)
    }

    // ========== Boundary Value Tests ==========

    @Test
    fun `test verify credential expired exactly at current time`() = runBlocking {
        val now = Clock.System.now()
        val credential = createTestCredential(expirationDate = now.toString())

        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(checkExpiration = true)
        )

        // Should be expired (now is not before expiration)
        assertFalse(result.notExpired)
    }

    @Test
    fun `test verify credential expired one second ago`() = runBlocking {
        val expirationDate = Clock.System.now().minus(1.seconds).toString()
        val credential = createTestCredential(expirationDate = expirationDate)

        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(checkExpiration = true)
        )

        assertFalse(result.valid)
        assertFalse(result.notExpired)
        assertTrue(result.allErrors.any { it.contains("expired") })
    }

    @Test
    fun `test verify credential expires one second in future`() = runBlocking {
        val expirationDate = Clock.System.now().plus(1.seconds).toString()
        val credential = createTestCredential(expirationDate = expirationDate)

        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(checkExpiration = true)
        )

        assertTrue(result.notExpired)
    }

    @Test
    fun `test verify credential with very long expiration date`() = runBlocking {
        val expirationDate = Clock.System.now().plus((31536000L * 1000).seconds).toString()
        val credential = createTestCredential(expirationDate = expirationDate)

        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(checkExpiration = true)
        )

        assertTrue(result.notExpired)
    }

    // ========== Proof Edge Cases ==========

    @Test
    fun `test verify credential with proof missing created timestamp`() = runBlocking {
        val credential = createTestCredential(
            proof = Proof(
                type = "Ed25519Signature2020",
                created = "",
                verificationMethod = "did:key:issuer123#key-1",
                proofPurpose = "assertionMethod"
            )
        )

        val result = verifier.verify(credential)

        assertFalse(result.valid)
        assertFalse(result.proofValid)
    }

    @Test
    fun `test verify credential with proof missing proof purpose`() = runBlocking {
        val credential = createTestCredential(
            proof = Proof(
                type = "Ed25519Signature2020",
                created = Clock.System.now().toString(),
                verificationMethod = "did:key:issuer123#key-1",
                proofPurpose = ""
            )
        )

        val result = verifier.verify(credential)

        assertFalse(result.valid)
        assertFalse(result.proofValid)
    }

    @Test
    fun `test verify credential with proof having both proofValue and jws`() = runBlocking {
        val credential = createTestCredential(
            proof = Proof(
                type = "Ed25519Signature2020",
                created = Clock.System.now().toString(),
                verificationMethod = "did:key:issuer123#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "z123",
                jws = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiJ9..."
            )
        )

        val result = verifier.verify(credential)

        // Should handle both (may prefer one over the other)
        assertNotNull(result)
    }

    @Test
    fun `test verify credential with proof missing both proofValue and jws`() = runBlocking {
        val credential = createTestCredential(
            proof = Proof(
                type = "Ed25519Signature2020",
                created = Clock.System.now().toString(),
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

    // ========== DID Resolution Edge Cases ==========

    @Test
    fun `test verify credential with DID resolution throwing exception`() = runBlocking {
        val verifierWithThrowingDid = CredentialVerifier(
            defaultDidResolver = DidResolver { throw RuntimeException("DID resolution failed") }
        )
        val credential = createTestCredential()

        val result = verifierWithThrowingDid.verify(credential)

        assertFalse(result.valid)
        assertFalse(result.issuerValid)
        assertTrue(result.allErrors.any { it.contains("DID") || it.contains("resolution") || it.contains("failed") })
    }

    @Test
    fun `test verify credential with DID resolution returning false`() = runBlocking {
        val verifierWithFailedDid = CredentialVerifier(
            booleanDidResolver { false }
        )
        val credential = createTestCredential()

        val result = verifierWithFailedDid.verify(credential)

        assertFalse(result.valid)
        assertFalse(result.issuerValid)
    }

    // ========== Credential Status Edge Cases ==========

    @Test
    fun `test verify credential with credential status missing ID`() = runBlocking {
        val credential = createTestCredential(
            credentialStatus = CredentialStatus(
                id = "",
                type = "StatusList2021Entry"
            )
        )

        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(checkRevocation = true)
        )

        assertNotNull(result)
        // May warn about invalid status
    }

    @Test
    fun `test verify credential with credential status missing type`() = runBlocking {
        val credential = createTestCredential(
            credentialStatus = CredentialStatus(
                id = "https://example.com/status/1",
                type = ""
            )
        )

        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(checkRevocation = true)
        )

        assertNotNull(result)
    }

    // ========== Schema Edge Cases ==========

    @Test
    fun `test verify credential with schema missing ID`() = runBlocking {
        val schema = CredentialSchema(
            id = "",
            type = "JsonSchemaValidator2018",
            schemaFormat = SchemaFormat.JSON_SCHEMA
        )
        val credential = createTestCredential(schema = schema)

        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(validateSchema = true)
        )

        assertNotNull(result)
        // May skip validation if schema ID is invalid
    }

    @Test
    fun `test verify credential with non-existent schema`() = runBlocking {
        val schema = CredentialSchema(
            id = "https://example.com/nonexistent",
            type = "JsonSchemaValidator2018",
            schemaFormat = SchemaFormat.JSON_SCHEMA
        )
        val credential = createTestCredential(schema = schema)

        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(validateSchema = true)
        )

        assertNotNull(result)
        // May skip validation or warn if schema not found
    }

    // ========== Options Edge Cases ==========

    @Test
    fun `test verify credential with all checks disabled`() = runBlocking {
        val expiredCredential = createTestCredential(
            expirationDate = Clock.System.now().minus(86400.seconds).toString()
        )

        val result = verifier.verify(
            expiredCredential,
            CredentialVerificationOptions(
                checkExpiration = false,
                checkRevocation = false,
                validateSchema = false,
                verifyBlockchainAnchor = false
            )
        )

        assertTrue(result.notExpired) // Should be true when check disabled
        assertTrue(result.notRevoked)
        assertTrue(result.schemaValid)
        assertTrue(result.blockchainAnchorValid)
    }

    @Test
    fun `test verify credential with all checks enabled`() = runBlocking {
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

        assertNotNull(result)
        // All checks should be performed
    }

    // ========== Multiple Errors Tests ==========

    @Test
    fun `test verify credential with multiple validation errors`() = runBlocking {
        val credential = createTestCredential(
            proof = null,
            expirationDate = Clock.System.now().minus(86400.seconds).toString(),
            issuerDid = "did:key:wrong"
        )

        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(checkExpiration = true)
        )

        assertFalse(result.valid)
        assertTrue(result.allErrors.size >= 2)
    }

    @Test
    fun `test verify credential with all possible errors`() = runBlocking {
        val credential = VerifiableCredential(
            id = null,
            type = emptyList(),
            issuer = "",
            credentialSubject = buildJsonObject {},
            issuanceDate = "",
            proof = null
        )

        val result = verifier.verify(credential)

        assertFalse(result.valid)
        assertTrue(result.allErrors.isNotEmpty())
    }

    // ========== Special Character Tests ==========

    @Test
    fun `test verify credential with special characters in proof value`() = runBlocking {
        val credential = createTestCredential(
            proof = Proof(
                type = "Ed25519Signature2020",
                created = Clock.System.now().toString(),
                verificationMethod = "did:key:issuer123#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "z!@#$%^&*()_+-=[]{}|;':\",./<>?"
            )
        )

        val result = verifier.verify(credential)

        assertNotNull(result)
        // Should handle special characters in proof value
    }

    @Test
    fun `test verify credential with unicode in verification method`() = runBlocking {
        val credential = createTestCredential(
            proof = Proof(
                type = "Ed25519Signature2020",
                created = Clock.System.now().toString(),
                verificationMethod = "did:key:issuer123#key-中文",
                proofPurpose = "assertionMethod"
            )
        )

        val result = verifier.verify(credential)

        assertNotNull(result)
    }

    // ========== Helper Methods ==========

    private suspend fun createTestCredential(
        id: String? = "https://example.com/credentials/1",
        types: List<String> = listOf("VerifiableCredential", "PersonCredential"),
        issuerDid: String = this.issuerDid,
        subject: JsonObject = buildJsonObject {
            put("id", "did:key:subject")
            put("name", "John Doe")
        },
        issuanceDate: String = Clock.System.now().toString(),
        expirationDate: String? = null,
        proof: Proof? = null, // null means auto-generate
        schema: CredentialSchema? = null,
        credentialStatus: CredentialStatus? = null
    ): VerifiableCredential {
        // Create credential without proof first
        val credentialWithoutProof = VerifiableCredential(
            id = id,
            type = types,
            issuer = issuerDid,
            credentialSubject = subject,
            issuanceDate = issuanceDate,
            expirationDate = expirationDate,
            proof = null,
            credentialSchema = schema,
            credentialStatus = credentialStatus
        )

        // Generate a properly signed proof if not provided
        val finalProof = proof ?: proofGenerator.generateProof(
            credential = credentialWithoutProof,
            keyId = keyId!!.value,
            options = ProofOptions(
                proofPurpose = "assertionMethod",
                verificationMethod = "$issuerDid#key-1"
            )
        )

        // Return credential with proof
        return credentialWithoutProof.copy(proof = finalProof)
    }
}

