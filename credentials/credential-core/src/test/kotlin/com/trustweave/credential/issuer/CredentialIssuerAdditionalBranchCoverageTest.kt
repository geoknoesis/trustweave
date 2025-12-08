package com.trustweave.credential.issuer

import com.trustweave.credential.CredentialIssuanceOptions
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.proof.Ed25519ProofGenerator
import com.trustweave.credential.proof.ProofGeneratorRegistry
import com.trustweave.credential.schema.SchemaRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlinx.datetime.Clock

/**
 * Additional branch coverage tests for CredentialIssuer.
 */
class CredentialIssuerAdditionalBranchCoverageTest {

    private lateinit var proofRegistry: ProofGeneratorRegistry

    @BeforeEach
    fun setup() {
        proofRegistry = ProofGeneratorRegistry()
        SchemaRegistry.clear()
    }

    @Test
    fun `test CredentialIssuer issue with proofGenerator in constructor`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )
        val issuer = CredentialIssuer(proofGenerator = generator, proofRegistry = proofRegistry)

        val credential = createTestCredential(issuerDid = "did:key:issuer")
        val result = issuer.issue(credential, "did:key:issuer", "key-1")

        assertNotNull(result.proof)
    }

    @Test
    fun `test CredentialIssuer issue uses ProofGeneratorRegistry when proofGenerator is null`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )
        proofRegistry.register(generator)

        val issuer = CredentialIssuer(proofGenerator = null, proofRegistry = proofRegistry)

        val credential = createTestCredential(issuerDid = "did:key:issuer")
        val options = CredentialIssuanceOptions(proofType = "Ed25519Signature2020")
        val result = issuer.issue(credential, "did:key:issuer", "key-1", options)

        assertNotNull(result.proof)
    }

    @Test
    fun `test CredentialIssuer issue with resolveDid returning false`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )
        val issuer = CredentialIssuer(
            proofGenerator = generator,
            resolveDid = { false },
            proofRegistry = proofRegistry
        )

        val credential = createTestCredential(issuerDid = "did:key:issuer")

        assertFailsWith<IllegalArgumentException> {
            issuer.issue(credential, "did:key:issuer", "key-1")
        }
    }

    @Test
    fun `test CredentialIssuer issue with resolveDid throwing exception`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )
        val issuer = CredentialIssuer(
            proofGenerator = generator,
            resolveDid = { throw RuntimeException("DID resolution failed") },
            proofRegistry = proofRegistry
        )

        val credential = createTestCredential(issuerDid = "did:key:issuer")

        assertFailsWith<RuntimeException> {
            issuer.issue(credential, "did:key:issuer", "key-1")
        }
    }

    @Test
    fun `test CredentialIssuer issue with schema validation`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )
        val issuer = CredentialIssuer(proofGenerator = generator, proofRegistry = proofRegistry)

        val schema = com.trustweave.credential.models.CredentialSchema(
            id = "schema-1",
            type = "JsonSchemaValidator2018"
        )
        val schemaDef = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
        }
        SchemaRegistry.registerSchema(schema, schemaDef)

        val credential = createTestCredential(
            issuerDid = "did:key:issuer",
            credentialSchema = schema
        )

        val result = issuer.issue(credential, "did:key:issuer", "key-1")

        assertNotNull(result.proof)
    }

    @Test
    fun `test CredentialIssuer issue with verificationMethod in additionalOptions`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )
        val issuer = CredentialIssuer(proofGenerator = generator, proofRegistry = proofRegistry)

        val credential = createTestCredential(issuerDid = "did:key:issuer")
        val options = CredentialIssuanceOptions(
            additionalOptions = mapOf("verificationMethod" to "did:key:custom#key-1")
        )

        val result = issuer.issue(credential, "did:key:issuer", "key-1", options)

        assertEquals("did:key:custom#key-1", result.proof?.verificationMethod)
    }

    @Test
    fun `test CredentialIssuer issue with challenge in options`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )
        val issuer = CredentialIssuer(proofGenerator = generator, proofRegistry = proofRegistry)

        val credential = createTestCredential(issuerDid = "did:key:issuer")
        val options = CredentialIssuanceOptions(challenge = "challenge-123")

        val result = issuer.issue(credential, "did:key:issuer", "key-1", options)

        assertEquals("challenge-123", result.proof?.challenge)
    }

    @Test
    fun `test CredentialIssuer issue with domain in options`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )
        val issuer = CredentialIssuer(proofGenerator = generator, proofRegistry = proofRegistry)

        val credential = createTestCredential(issuerDid = "did:key:issuer")
        val options = CredentialIssuanceOptions(domain = "example.com")

        val result = issuer.issue(credential, "did:key:issuer", "key-1", options)

        assertEquals("example.com", result.proof?.domain)
    }

    private fun createTestCredential(
        id: String? = null,
        types: List<String> = listOf("VerifiableCredential", "PersonCredential"),
        issuerDid: String = "did:key:issuer",
        subject: JsonObject = buildJsonObject {
            put("id", "did:key:subject")
            put("name", "John Doe")
        },
        issuanceDate: String = Clock.System.now().toString(),
        credentialSchema: com.trustweave.credential.models.CredentialSchema? = null
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = types,
            issuer = issuerDid,
            credentialSubject = subject,
            issuanceDate = issuanceDate,
            credentialSchema = credentialSchema
        )
    }
}



