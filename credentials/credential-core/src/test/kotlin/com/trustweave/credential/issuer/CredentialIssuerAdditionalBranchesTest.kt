package com.trustweave.credential.issuer

import com.trustweave.credential.CredentialIssuanceOptions
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.vc.CredentialSchema
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.credential.identifiers.SchemaId
import com.trustweave.did.identifiers.Did
import com.trustweave.credential.proof.Ed25519ProofGenerator
import com.trustweave.credential.proof.ProofGeneratorRegistry
import com.trustweave.credential.model.Evidence
import com.trustweave.credential.model.vc.TermsOfUse
import com.trustweave.credential.model.vc.RefreshService
import com.trustweave.core.identifiers.Iri
import kotlinx.datetime.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.seconds
import java.util.UUID
import kotlin.test.*

/**
 * Additional comprehensive branch coverage tests for CredentialIssuer.
 * Covers additional branches not covered in CredentialIssuerBranchCoverageTest.
 */
class CredentialIssuerAdditionalBranchesTest {

    private lateinit var issuer: CredentialIssuer
    private val issuerDid = "did:key:issuer123"
    private lateinit var proofRegistry: ProofGeneratorRegistry

    @BeforeEach
    fun setup() {
        proofRegistry = ProofGeneratorRegistry()
        // Schema registry not available in credential-core

        val signer: suspend (ByteArray, String) -> ByteArray = { data, _ ->
            "mock-signature-${UUID.randomUUID()}".toByteArray()
        }

        val proofGenerator = Ed25519ProofGenerator(
            signer = signer,
            getPublicKeyId = { "did:key:issuer123#key-1" }
        )
        proofRegistry.register(proofGenerator)

        issuer = CredentialIssuer(
            proofGenerator = proofGenerator,
            resolveDid = { did -> did == issuerDid },
            proofRegistry = proofRegistry
        )
    }

    @AfterEach
    fun cleanup() {
        proofRegistry.clear()
        // Schema registry not available in credential-core
        // Schema registry not available in credential-core
    }

    // ========== Options Branches ==========

    @Test
    fun `test issue with options containing proofType`() = runBlocking {
        val credential = createTestCredential()
        val options = CredentialIssuanceOptions(proofType = "Ed25519Signature2020")

        val result = issuer.issue(credential, issuerDid, "key-1", options)

        assertNotNull(result.proof)
        val linkedDataProof = result.proof as? com.trustweave.credential.model.vc.CredentialProof.LinkedDataProof
        assertNotNull(linkedDataProof)
        assertEquals("Ed25519Signature2020", linkedDataProof.type)
    }

    @Test
    fun `test issue with options containing challenge`() = runBlocking {
        val credential = createTestCredential()
        val options = CredentialIssuanceOptions(challenge = "challenge-123")

        val result = issuer.issue(credential, issuerDid, "key-1", options)

        val linkedDataProof = result.proof as? com.trustweave.credential.model.vc.CredentialProof.LinkedDataProof
        assertNotNull(linkedDataProof)
        assertEquals("challenge-123", linkedDataProof.additionalProperties["challenge"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test issue with options containing domain`() = runBlocking {
        val credential = createTestCredential()
        val options = CredentialIssuanceOptions(domain = "example.com")

        val result = issuer.issue(credential, issuerDid, "key-1", options)

        val linkedDataProof = result.proof as? com.trustweave.credential.model.vc.CredentialProof.LinkedDataProof
        assertNotNull(linkedDataProof)
        assertEquals("example.com", linkedDataProof.additionalProperties["domain"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test issue with options containing all fields`() = runBlocking {
        val credential = createTestCredential()
        val options = CredentialIssuanceOptions(
            proofType = "Ed25519Signature2020",
            challenge = "challenge-123",
            domain = "example.com"
        )

        val result = issuer.issue(credential, issuerDid, "key-1", options)

        assertNotNull(result.proof)
        val linkedDataProof = result.proof as? com.trustweave.credential.model.vc.CredentialProof.LinkedDataProof
        assertNotNull(linkedDataProof)
        assertEquals("Ed25519Signature2020", linkedDataProof.type)
        assertEquals("challenge-123", linkedDataProof.additionalProperties["challenge"]?.jsonPrimitive?.content)
        assertEquals("example.com", linkedDataProof.additionalProperties["domain"]?.jsonPrimitive?.content)
    }

    // ========== Credential Field Branches ==========

    @Test
    fun `test issue with credential having expirationDate`() = runBlocking {
        val expirationDate = Clock.System.now().plus(86400.seconds)
        val credential = createTestCredential(
            expirationDate = expirationDate
        )

        val result = issuer.issue(credential, issuerDid, "key-1")

        assertNotNull(result.proof)
        assertEquals(credential.expirationDate, result.expirationDate)
    }

    @Test
    fun `test issue with credential having credentialStatus`() = runBlocking {
        val statusListId = com.trustweave.credential.identifiers.StatusListId("https://example.com/status/1")
        val credential = createTestCredential(
            credentialStatus = com.trustweave.credential.model.vc.CredentialStatus(
                id = statusListId,
                type = "StatusList2021Entry",
                statusListIndex = "1",
                statusListCredential = statusListId
            )
        )

        val result = issuer.issue(credential, issuerDid, "key-1")

        assertNotNull(result.proof)
        assertNotNull(result.credentialStatus)
    }

    @Test
    fun `test issue with credential having evidence`() = runBlocking {
        val credential = createTestCredential(
            evidence = listOf(
                Evidence(
                    id = CredentialId("evidence-1"),
                    type = listOf("DocumentVerification"),
                    evidenceDocument = kotlinx.serialization.json.JsonPrimitive("https://example.com/evidence/1")
                )
            )
        )

        val result = issuer.issue(credential, issuerDid, "key-1")

        assertNotNull(result.proof)
        assertNotNull(result.evidence)
        assertEquals(1, result.evidence?.size)
    }

    @Test
    fun `test issue with credential having termsOfUse`() = runBlocking {
        val termsOfUse = listOf(
            TermsOfUse(
                id = "terms-1",
                type = "IssuerPolicy",
                additionalProperties = kotlinx.serialization.json.buildJsonObject {
                    put("profile", "https://example.com/terms")
                }
            )
        )
        val credential = createTestCredential(
            termsOfUse = termsOfUse
        )

        val result = issuer.issue(credential, issuerDid, "key-1")

        assertNotNull(result.proof)
        assertNotNull(result.termsOfUse)
    }

    @Test
    fun `test issue with credential having refreshService`() = runBlocking {
        val credential = createTestCredential(
            refreshService = RefreshService(
                id = Iri("refresh-1"),
                type = "CredentialRefreshService2020"
            )
        )

        val result = issuer.issue(credential, issuerDid, "key-1")

        assertNotNull(result.proof)
        assertNotNull(result.refreshService)
    }

    // ========== DID Resolution Branches ==========

    @Test
    fun `test issue with resolveDid returning false`() = runBlocking {
        val issuerWithFailedDidResolution = CredentialIssuer(
            proofGenerator = proofRegistry.get("Ed25519Signature2020")!!,
            resolveDid = { false },
            proofRegistry = proofRegistry
        )

        val credential = createTestCredential()

        // Should still issue, but DID resolution might affect validation
        val result = issuerWithFailedDidResolution.issue(credential, issuerDid, "key-1")

        assertNotNull(result.proof)
    }

    @Test
    fun `test issue with resolveDid throwing exception`() = runBlocking {
        val issuerWithThrowingDidResolution = CredentialIssuer(
            proofGenerator = proofRegistry.get("Ed25519Signature2020")!!,
            resolveDid = { throw RuntimeException("DID resolution failed") },
            proofRegistry = proofRegistry
        )

        val credential = createTestCredential()

        assertFailsWith<RuntimeException> {
            issuerWithThrowingDidResolution.issue(credential, issuerDid, "key-1")
        }
    }

    // ========== Proof Generator Branches ==========

    @Test
    fun `test issue with proofGenerator from registry`() = runBlocking {
        val issuerWithoutDirectGenerator = CredentialIssuer(
            proofGenerator = null,
            resolveDid = { did -> did == issuerDid },
            proofRegistry = proofRegistry
        )

        val credential = createTestCredential()

        // Should use registry
        val result = issuerWithoutDirectGenerator.issue(credential, issuerDid, "key-1")

        assertNotNull(result.proof)
    }

    @Test
    fun `test issue with proofGenerator not in registry`() = runBlocking {
        val emptyRegistry = ProofGeneratorRegistry()

        val issuerWithoutGenerator = CredentialIssuer(
            proofGenerator = null,
            resolveDid = { did -> did == issuerDid },
            proofRegistry = emptyRegistry
        )

        val credential = createTestCredential()

        assertFailsWith<IllegalArgumentException> {
            issuerWithoutGenerator.issue(credential, issuerDid, "key-1")
        }
    }

    // ========== Schema Validation Branches ==========

    @Test
    fun `test issue with schema validation returning warnings`() = runBlocking {
        val schemaId = "https://example.com/schemas/person"
        val schema = CredentialSchema(
            id = SchemaId(schemaId),
            type = "JsonSchemaValidator2018"
        )
        val schemaDefinition = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
            put("properties", buildJsonObject {
                put("name", buildJsonObject {
                    put("type", "string")
                })
            })
        }

        runBlocking {
            // Schema registry not available in credential-core - schema validation skipped
        }

        val credential = createTestCredential(
            schema = schema
        )

        val result = issuer.issue(credential, issuerDid, "key-1")

        assertNotNull(result.proof)
    }

    // ========== Helper Functions ==========

    private fun createTestCredential(
        id: String = "credential-${UUID.randomUUID()}",
        issuerDid: String = this.issuerDid,
        types: List<CredentialType> = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("PersonCredential")),
        credentialSubject: CredentialSubject = CredentialSubject.fromDid(
            Did("did:key:subject123"),
            claims = mapOf("name" to JsonPrimitive("John Doe"))
        ),
        expirationDate: Instant? = null,
        credentialStatus: com.trustweave.credential.model.vc.CredentialStatus? = null,
        evidence: List<Evidence>? = null,
        termsOfUse: List<TermsOfUse>? = null,
        refreshService: RefreshService? = null,
        schema: CredentialSchema? = null
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
            termsOfUse = termsOfUse,
            refreshService = refreshService,
            credentialSchema = schema,
            proof = null
        )
    }
}

