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
import kotlinx.datetime.Instant
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
        // Schema registry not available in credential-core
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

        val schema = CredentialSchema(
            id = SchemaId("schema-1"),
            type = "JsonSchemaValidator2018"
        )
        // Schema registry not available in credential-core - schema validation skipped

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

        val linkedDataProof = result.proof as? com.trustweave.credential.model.vc.CredentialProof.LinkedDataProof
        assertNotNull(linkedDataProof)
        assertEquals("did:key:custom#key-1", linkedDataProof.verificationMethod)
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

        assertTrue(result.proof is com.trustweave.credential.model.vc.CredentialProof.LinkedDataProof)
        val linkedDataProof = result.proof as? com.trustweave.credential.model.vc.CredentialProof.LinkedDataProof
        assertNotNull(linkedDataProof)
        // challenge would be in additionalProperties if needed
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

        assertTrue(result.proof is com.trustweave.credential.model.vc.CredentialProof.LinkedDataProof)
        val linkedDataProof = result.proof as? com.trustweave.credential.model.vc.CredentialProof.LinkedDataProof
        assertNotNull(linkedDataProof)
        // domain would be in additionalProperties if needed
    }

    private fun createTestCredential(
        id: String? = null,
        types: List<CredentialType> = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("PersonCredential")),
        issuerDid: String = "did:key:issuer",
        subject: CredentialSubject = CredentialSubject.fromDid(
            Did("did:key:subject"),
            claims = mapOf("name" to JsonPrimitive("John Doe"))
        ),
        issuanceDate: Instant = Clock.System.now(),
        credentialSchema: CredentialSchema? = null
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id?.let { CredentialId(it) },
            type = types,
            issuer = Issuer.fromDid(Did(issuerDid)),
            credentialSubject = subject,
            issuanceDate = issuanceDate,
            credentialSchema = credentialSchema
        )
    }
}



