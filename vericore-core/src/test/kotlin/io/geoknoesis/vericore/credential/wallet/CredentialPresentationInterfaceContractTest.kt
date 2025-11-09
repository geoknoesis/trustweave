package io.geoknoesis.vericore.credential.wallet

import io.geoknoesis.vericore.credential.PresentationOptions
import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.models.VerifiablePresentation
import io.geoknoesis.vericore.credential.proof.Ed25519ProofGenerator
import io.geoknoesis.vericore.credential.proof.ProofGeneratorRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive interface contract tests for CredentialPresentation.
 * Tests all methods, branches, and edge cases.
 */
class CredentialPresentationInterfaceContractTest {

    @BeforeEach
    fun setup() {
        ProofGeneratorRegistry.clear()
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )
        ProofGeneratorRegistry.register(generator)
    }

    @Test
    fun `test CredentialPresentation createPresentation returns presentation`() = runBlocking {
        val presentation = createMockPresentation()
        val cred1 = createTestCredential(id = "cred-1")
        val cred2 = createTestCredential(id = "cred-2")
        (presentation as CredentialStorage).store(cred1)
        (presentation as CredentialStorage).store(cred2)
        
        val result = presentation.createPresentation(
            credentialIds = listOf("cred-1", "cred-2"),
            holderDid = "did:key:holder",
            options = PresentationOptions(
                holderDid = "did:key:holder",
                proofType = "Ed25519Signature2020",
                keyId = "key-1"
            )
        )
        
        assertNotNull(result)
        assertEquals(2, result.verifiableCredential.size)
        assertEquals("did:key:holder", result.holder)
    }

    @Test
    fun `test CredentialPresentation createPresentation throws for non-existent credential`() = runBlocking {
        val presentation = createMockPresentation()
        
        assertFailsWith<IllegalArgumentException> {
            presentation.createPresentation(
                credentialIds = listOf("non-existent"),
                holderDid = "did:key:holder",
                options = PresentationOptions(holderDid = "did:key:holder")
            )
        }
    }

    @Test
    fun `test CredentialPresentation createSelectiveDisclosure returns presentation`() = runBlocking {
        val presentation = createMockPresentation()
        val cred = createTestCredential(id = "cred-1")
        (presentation as CredentialStorage).store(cred)
        
        val result = presentation.createSelectiveDisclosure(
            credentialIds = listOf("cred-1"),
            disclosedFields = listOf("credentialSubject.name"),
            holderDid = "did:key:holder",
            options = PresentationOptions(holderDid = "did:key:holder")
        )
        
        assertNotNull(result)
        assertEquals(1, result.verifiableCredential.size)
    }

    @Test
    fun `test CredentialPresentation createSelectiveDisclosure throws for non-existent credential`() = runBlocking {
        val presentation = createMockPresentation()
        
        assertFailsWith<IllegalArgumentException> {
            presentation.createSelectiveDisclosure(
                credentialIds = listOf("non-existent"),
                disclosedFields = listOf("name"),
                holderDid = "did:key:holder",
                options = PresentationOptions(holderDid = "did:key:holder")
            )
        }
    }

    @Test
    fun `test CredentialPresentation createPresentation with challenge and domain`() = runBlocking {
        val presentation = createMockPresentation()
        val cred = createTestCredential(id = "cred-1")
        (presentation as CredentialStorage).store(cred)
        
        val result = presentation.createPresentation(
            credentialIds = listOf("cred-1"),
            holderDid = "did:key:holder",
            options = PresentationOptions(
                holderDid = "did:key:holder",
                challenge = "challenge-123",
                domain = "example.com",
                proofType = "Ed25519Signature2020",
                keyId = "key-1"
            )
        )
        
        assertNotNull(result)
        assertEquals("challenge-123", result.challenge)
        assertEquals("example.com", result.domain)
    }

    private fun createMockPresentation(): CredentialPresentation {
        return object : CredentialStorage, CredentialPresentation {
            private val storage = mutableMapOf<String, VerifiableCredential>()
            
            override suspend fun store(credential: VerifiableCredential): String {
                val id = credential.id ?: java.util.UUID.randomUUID().toString()
                storage[id] = credential.copy(id = id)
                return id
            }
            
            override suspend fun get(credentialId: String): VerifiableCredential? = storage[credentialId]
            
            override suspend fun list(filter: CredentialFilter?): List<VerifiableCredential> = storage.values.toList()
            
            override suspend fun delete(credentialId: String): Boolean = storage.remove(credentialId) != null
            
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> {
                val builder = CredentialQueryBuilder()
                builder.query()
                return storage.values.filter(builder.toPredicate())
            }
            
            override suspend fun createPresentation(
                credentialIds: List<String>,
                holderDid: String,
                options: PresentationOptions
            ): VerifiablePresentation {
                val credentials = credentialIds.mapNotNull { storage[it] }
                if (credentials.size != credentialIds.size) {
                    throw IllegalArgumentException("One or more credentials not found")
                }
                
                return VerifiablePresentation(
                    id = java.util.UUID.randomUUID().toString(),
                    type = listOf("VerifiablePresentation"),
                    verifiableCredential = credentials,
                    holder = holderDid,
                    proof = null,
                    challenge = options.challenge,
                    domain = options.domain
                )
            }
            
            override suspend fun createSelectiveDisclosure(
                credentialIds: List<String>,
                disclosedFields: List<String>,
                holderDid: String,
                options: PresentationOptions
            ): VerifiablePresentation {
                val credentials = credentialIds.mapNotNull { storage[it] }
                if (credentials.size != credentialIds.size) {
                    throw IllegalArgumentException("One or more credentials not found")
                }
                
                // Simplified selective disclosure - in real implementation would filter fields
                return VerifiablePresentation(
                    id = java.util.UUID.randomUUID().toString(),
                    type = listOf("VerifiablePresentation"),
                    verifiableCredential = credentials,
                    holder = holderDid,
                    proof = null,
                    challenge = options.challenge,
                    domain = options.domain
                )
            }
        }
    }

    private fun createTestCredential(
        id: String? = null,
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


