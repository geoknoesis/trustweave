package com.trustweave.credential.proof

import com.trustweave.credential.models.Proof
import com.trustweave.credential.models.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import java.util.UUID

/**
 * Comprehensive tests for ProofGeneratorRegistry API.
 */
class ProofGeneratorRegistryTest {

    private lateinit var registry: ProofGeneratorRegistry
    private lateinit var generator1: ProofGenerator
    private lateinit var generator2: ProofGenerator

    @BeforeEach
    fun setup() {
        registry = ProofGeneratorRegistry()

        generator1 = Ed25519ProofGenerator(
            signer = { data, _ -> "signature-1-${UUID.randomUUID()}".toByteArray() },
            getPublicKeyId = { "did:key:test#key-1" }
        )

        generator2 = object : ProofGenerator {
            override val proofType = "JwtProof2020"
            override suspend fun generateProof(
                credential: VerifiableCredential,
                keyId: String,
                options: ProofOptions
            ): Proof {
                return Proof(
                    type = proofType,
                    created = java.time.Instant.now().toString(),
                    verificationMethod = "did:key:test#$keyId",
                    proofPurpose = options.proofPurpose,
                    jws = "jws-signature-${UUID.randomUUID()}"
                )
            }
        }
    }

    @AfterEach
    fun cleanup() {
        registry.clear()
    }

    @Test
    fun `test register generator`() = runBlocking {
        registry.register(generator1)

        assertTrue(registry.isSupported("Ed25519Signature2020"))
        assertEquals(generator1, registry.get("Ed25519Signature2020"))
    }

    @Test
    fun `test get generator by type`() = runBlocking {
        registry.register(generator1)
        registry.register(generator2)

        val retrieved = registry.get("Ed25519Signature2020")

        assertNotNull(retrieved)
        assertEquals("Ed25519Signature2020", retrieved?.proofType)
    }

    @Test
    fun `test get generator returns null when not found`() = runBlocking {
        assertNull(registry.get("NonexistentProofType"))
    }

    @Test
    fun `test getRegisteredTypes returns all types`() = runBlocking {
        registry.register(generator1)
        registry.register(generator2)

        val types = registry.getRegisteredTypes()

        assertEquals(2, types.size)
        assertTrue(types.contains("Ed25519Signature2020"))
        assertTrue(types.contains("JwtProof2020"))
    }

    @Test
    fun `test isSupported returns true for registered type`() = runBlocking {
        registry.register(generator1)

        assertTrue(registry.isSupported("Ed25519Signature2020"))
        assertFalse(registry.isSupported("NonexistentProofType"))
    }

    @Test
    fun `test clear removes all generators`() = runBlocking {
        registry.register(generator1)
        registry.register(generator2)
        assertEquals(2, registry.getRegisteredTypes().size)

        registry.clear()

        assertEquals(0, registry.getRegisteredTypes().size)
        assertFalse(registry.isSupported("Ed25519Signature2020"))
    }

    @Test
    fun `test register overwrites existing generator`() = runBlocking {
        registry.register(generator1)

        val newGenerator = Ed25519ProofGenerator(
            signer = { data, _ -> "new-signature".toByteArray() },
            getPublicKeyId = { "did:key:new#key-1" }
        )

        registry.register(newGenerator)

        val retrieved = registry.get("Ed25519Signature2020")
        assertEquals(newGenerator, retrieved)
    }

    @Test
    fun `test generate proof with registered generator`() = runBlocking {
        registry.register(generator1)
        val credential = createTestCredential()

        val proof = generator1.generateProof(
            credential = credential,
            keyId = "key-1",
            options = ProofOptions(proofPurpose = "assertionMethod")
        )

        assertNotNull(proof)
        assertEquals("Ed25519Signature2020", proof.type)
        assertNotNull(proof.proofValue)
    }

    @Test
    fun `test generate proof with options`() = runBlocking {
        registry.register(generator1)
        val credential = createTestCredential()

        val proof = generator1.generateProof(
            credential = credential,
            keyId = "key-1",
            options = ProofOptions(
                proofPurpose = "authentication",
                challenge = "challenge-123",
                domain = "example.com",
                verificationMethod = "did:key:custom#key-1"
            )
        )

        assertEquals("authentication", proof.proofPurpose)
        assertEquals("challenge-123", proof.challenge)
        assertEquals("example.com", proof.domain)
        assertEquals("did:key:custom#key-1", proof.verificationMethod)
    }

    private fun createTestCredential(
        id: String? = "https://example.com/credentials/1",
        types: List<String> = listOf("VerifiableCredential", "PersonCredential"),
        issuerDid: String = "did:key:issuer",
        subject: JsonObject = buildJsonObject {
            put("id", "did:key:subject")
            put("name", "John Doe")
        },
        issuanceDate: String = java.time.Instant.now().toString()
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = types,
            issuer = issuerDid,
            credentialSubject = subject,
            issuanceDate = issuanceDate
        )
    }
}

