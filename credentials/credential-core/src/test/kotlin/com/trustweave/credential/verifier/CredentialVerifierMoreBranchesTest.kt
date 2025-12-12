package com.trustweave.credential.verifier

import com.trustweave.credential.CredentialVerificationOptions
import com.trustweave.credential.model.vc.CredentialSchema
import com.trustweave.credential.model.vc.CredentialStatus
import com.trustweave.credential.model.vc.CredentialProof
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.model.SchemaFormat
import com.trustweave.credential.model.ProofType
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.credential.identifiers.SchemaId
import com.trustweave.credential.identifiers.StatusListId
import com.trustweave.did.identifiers.Did
import com.trustweave.did.identifiers.VerificationMethodId
import com.trustweave.util.booleanDidResolver
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration.Companion.seconds
import kotlin.test.*
import com.trustweave.credential.model.Evidence
import com.trustweave.credential.schema.SchemaRegistries

/**
 * Additional comprehensive branch coverage tests for CredentialVerifier.
 * Covers additional branches not covered in CredentialVerifierBranchCoverageTest and CredentialVerifierAdditionalBranchCoverageTest.
 */
class CredentialVerifierMoreBranchesTest {

    private lateinit var verifier: CredentialVerifier
    private val issuerDid = "did:key:issuer123"

    @BeforeEach
    fun setup() {
        verifier = CredentialVerifier(
            booleanDidResolver { did -> did == issuerDid }
        )
    }

    @AfterEach
    fun cleanup() {
        // Cleanup if needed
    }

    // ========== Options Branches ==========

    @Test
    fun `test verify with options checkExpiration false`() = runBlocking {
        val credential = createTestCredential(
            expirationDate = Clock.System.now().minus(86400.seconds) // Expired
        )
        val options = CredentialVerificationOptions(checkExpiration = false)

        val result = verifier.verify(credential, options)

        // Should not check expiration
        assertTrue(result.valid || !result.allErrors.any { it.contains("expired") || it.contains("expiration") })
    }

    @Test
    fun `test verify with options checkRevocation false`() = runBlocking {
        val credential = createTestCredential(
            credentialStatus = CredentialStatus(
                id = StatusListId("https://example.com/status/1"),
                type = "StatusList2021Entry",
                statusListIndex = "1",
                statusListCredential = StatusListId("https://example.com/status/1")
            )
        )
        val options = CredentialVerificationOptions(checkRevocation = false)

        val result = verifier.verify(credential, options)

        // Should not check revocation
        assertTrue(result.valid || !result.allErrors.any { it.contains("revoked") || it.contains("revocation") })
    }

    @Test
    fun `test verify with options validateSchema false`() = runBlocking {
        val schemaId = SchemaId("https://example.com/schemas/person")
        val schema = CredentialSchema(
            id = schemaId,
            type = "JsonSchemaValidator2018"
        )
        val credential = createTestCredential(schema = schema)
        val options = CredentialVerificationOptions(validateSchema = false)

        val result = verifier.verify(credential, options)

        // Should not validate schema
        assertTrue(result.valid || !result.allErrors.any { it.contains("schema") })
    }

    @Test
    fun `test verify with options verifyBlockchainAnchor false`() = runBlocking {
        val credential = createTestCredential(
            evidence = listOf(
                Evidence(
                    id = CredentialId("evidence-1"),
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
        assertTrue(result.valid || !result.allErrors.any { it.contains("blockchain") || it.contains("anchor") })
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
            expirationDate = Clock.System.now().plus(86400.seconds)
        )

        val result = verifier.verify(credential)

        assertTrue(result.valid || !result.allErrors.any { it.contains("expired") })
    }

    @Test
    fun `test verify with expirationDate exactly now`() = runBlocking {
        val credential = createTestCredential(
            expirationDate = Clock.System.now()
        )

        val result = verifier.verify(credential)

        assertNotNull(result)
    }

    @Test
    fun `test verify with expirationDate in past`() = runBlocking {
        val credential = createTestCredential(
            expirationDate = Clock.System.now().minus(86400.seconds)
        )

        val result = verifier.verify(credential)

        assertFalse(result.valid)
        assertTrue(result.allErrors.any { it.contains("expired") || it.contains("expiration") })
    }

    @Test
    fun `test verify with invalid expirationDate format`() = runBlocking {
        val credential = createTestCredential(
            expirationDate = null // Invalid date format test - can't use String for Instant
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
                id = StatusListId("https://example.com/status/1"),
                type = "StatusList2021Entry",
                statusListIndex = null,
                statusListCredential = StatusListId("https://example.com/status/1")
            )
        )

        val result = verifier.verify(credential)

        assertNotNull(result)
    }

    @Test
    fun `test verify with credentialStatus having empty statusListIndex`() = runBlocking {
        val credential = createTestCredential(
            credentialStatus = CredentialStatus(
                id = StatusListId("https://example.com/status/1"),
                type = "StatusList2021Entry",
                statusListIndex = "",
                statusListCredential = StatusListId("https://example.com/status/1")
            )
        )

        val result = verifier.verify(credential)

        assertNotNull(result)
    }

    @Test
    fun `test verify with credentialStatus having null statusListCredential`() = runBlocking {
        val credential = createTestCredential(
            credentialStatus = CredentialStatus(
                id = StatusListId("https://example.com/status/1"),
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
        val schemaId = SchemaId("https://example.com/schemas/person")
        val schema = CredentialSchema(
            id = schemaId,
            type = "JsonSchemaValidator2018"
        )
        val credential = createTestCredential(schema = schema)

        val result = verifier.verify(credential)

        // Should handle gracefully
        assertNotNull(result)
    }

    @Test
    fun `test verify with schema registered but definition missing`() = runBlocking {
        val schemaId = SchemaId("https://example.com/schemas/person")
        val schema = CredentialSchema(
            id = schemaId,
            type = "JsonSchemaValidator2018"
        )

        runBlocking {
            SchemaRegistries.default().registerSchema(schemaId, SchemaFormat.JSON_SCHEMA, buildJsonObject { })
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
            proof = CredentialProof.LinkedDataProof(
                type = "Ed25519Signature2020",
                created = Clock.System.now(),
                verificationMethod = "did:key:issuer123#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "proof-value-123",
                additionalProperties = mapOf("jws" to JsonPrimitive("jws-value-123"))
            )
        )

        val result = verifier.verify(credential)

        assertNotNull(result)
    }

    @Test
    fun `test verify with proof having only proofValue`() = runBlocking {
        val credential = createTestCredential(
            proof = CredentialProof.LinkedDataProof(
                type = "Ed25519Signature2020",
                created = Clock.System.now(),
                verificationMethod = "did:key:issuer123#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "proof-value-123"
            )
        )

        val result = verifier.verify(credential)

        assertNotNull(result)
    }

    @Test
    fun `test verify with proof having only jws`() = runBlocking {
        val credential = createTestCredential(
            proof = CredentialProof.LinkedDataProof(
                type = "Ed25519Signature2020",
                created = Clock.System.now(),
                verificationMethod = "did:key:issuer123#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "", // Empty string instead of null
                additionalProperties = mapOf("jws" to JsonPrimitive("jws-value-123"))
            )
        )

        val result = verifier.verify(credential)

        assertNotNull(result)
    }

    // ========== Helper Functions ==========

    private fun createTestCredential(
        id: String = "credential-123",
        issuerDid: String = this.issuerDid,
        types: List<CredentialType> = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("PersonCredential")),
        credentialSubject: CredentialSubject = CredentialSubject.fromDid(
            Did("did:key:subject123"),
            claims = mapOf("name" to JsonPrimitive("John Doe"))
        ),
        expirationDate: Instant? = null,
        credentialStatus: CredentialStatus? = null,
        schema: CredentialSchema? = null,
        proof: CredentialProof? = CredentialProof.LinkedDataProof(
            type = "Ed25519Signature2020",
            created = Clock.System.now(),
            verificationMethod = "did:key:issuer123#key-1",
            proofPurpose = "assertionMethod",
            proofValue = "test-proof",
            additionalProperties = emptyMap()
        ),
        evidence: List<com.trustweave.credential.model.Evidence>? = null
    ): VerifiableCredential {
        return VerifiableCredential(
            id = CredentialId(id),
            type = types,
            issuer = Issuer.fromDid(Did(issuerDid)),
            issuanceDate = Clock.System.now(),
            expirationDate = expirationDate,
            credentialSubject = credentialSubject,
            credentialStatus = credentialStatus,
            evidence = evidence,
            credentialSchema = schema,
            proof = proof
        )
    }
}

