package io.geoknoesis.vericore.credential.wallet

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.PresentationOptions
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive interface contract tests for CredentialStorage.
 * Tests all methods, branches, and edge cases.
 */
class CredentialStorageInterfaceContractTest {

    @Test
    fun `test CredentialStorage store returns credential ID`() = runBlocking {
        val storage = createMockStorage()
        val credential = createTestCredential()
        
        val id = storage.store(credential)
        
        assertNotNull(id)
        assertTrue(id.isNotBlank())
    }

    @Test
    fun `test CredentialStorage store uses credential id if present`() = runBlocking {
        val storage = createMockStorage()
        val credential = createTestCredential(id = "custom-id-123")
        
        val id = storage.store(credential)
        
        assertEquals("custom-id-123", id)
    }

    @Test
    fun `test CredentialStorage get returns stored credential`() = runBlocking {
        val storage = createMockStorage()
        val credential = createTestCredential(id = "test-id")
        storage.store(credential)
        
        val retrieved = storage.get("test-id")
        
        assertNotNull(retrieved)
        assertEquals("test-id", retrieved?.id)
    }

    @Test
    fun `test CredentialStorage get returns null for non-existent credential`() = runBlocking {
        val storage = createMockStorage()
        
        val retrieved = storage.get("non-existent")
        
        assertNull(retrieved)
    }

    @Test
    fun `test CredentialStorage list returns all credentials`() = runBlocking {
        val storage = createMockStorage()
        val cred1 = createTestCredential(id = "cred-1")
        val cred2 = createTestCredential(id = "cred-2")
        storage.store(cred1)
        storage.store(cred2)
        
        val all = storage.list()
        
        assertEquals(2, all.size)
    }

    @Test
    fun `test CredentialStorage list with filter by issuer`() = runBlocking {
        val storage = createMockStorage()
        val cred1 = createTestCredential(id = "cred-1", issuerDid = "did:key:issuer1")
        val cred2 = createTestCredential(id = "cred-2", issuerDid = "did:key:issuer2")
        storage.store(cred1)
        storage.store(cred2)
        
        val filtered = storage.list(CredentialFilter(issuer = "did:key:issuer1"))
        
        assertEquals(1, filtered.size)
        assertEquals("cred-1", filtered.first().id)
    }

    @Test
    fun `test CredentialStorage list with filter by type`() = runBlocking {
        val storage = createMockStorage()
        val cred1 = createTestCredential(id = "cred-1", types = listOf("VerifiableCredential", "PersonCredential"))
        val cred2 = createTestCredential(id = "cred-2", types = listOf("VerifiableCredential", "DegreeCredential"))
        storage.store(cred1)
        storage.store(cred2)
        
        val filtered = storage.list(CredentialFilter(type = listOf("PersonCredential")))
        
        assertEquals(1, filtered.size)
        assertEquals("cred-1", filtered.first().id)
    }

    @Test
    fun `test CredentialStorage delete returns true for existing credential`() = runBlocking {
        val storage = createMockStorage()
        val credential = createTestCredential(id = "cred-1")
        storage.store(credential)
        
        val deleted = storage.delete("cred-1")
        
        assertTrue(deleted)
        assertNull(storage.get("cred-1"))
    }

    @Test
    fun `test CredentialStorage delete returns false for non-existent credential`() = runBlocking {
        val storage = createMockStorage()
        
        val deleted = storage.delete("non-existent")
        
        assertFalse(deleted)
    }

    @Test
    fun `test CredentialStorage query with builder`() = runBlocking {
        val storage = createMockStorage()
        val cred1 = createTestCredential(id = "cred-1", issuerDid = "did:key:issuer1")
        val cred2 = createTestCredential(id = "cred-2", issuerDid = "did:key:issuer2")
        storage.store(cred1)
        storage.store(cred2)
        
        val results = storage.query {
            byIssuer("did:key:issuer1")
        }
        
        assertEquals(1, results.size)
        assertEquals("cred-1", results.first().id)
    }

    @Test
    fun `test CredentialStorage query with multiple filters`() = runBlocking {
        val storage = createMockStorage()
        val cred1 = createTestCredential(
            id = "cred-1",
            issuerDid = "did:key:issuer1",
            types = listOf("VerifiableCredential", "PersonCredential")
        )
        val cred2 = createTestCredential(
            id = "cred-2",
            issuerDid = "did:key:issuer1",
            types = listOf("VerifiableCredential", "DegreeCredential")
        )
        storage.store(cred1)
        storage.store(cred2)
        
        val results = storage.query {
            byIssuer("did:key:issuer1")
            byType("PersonCredential")
        }
        
        assertEquals(1, results.size)
        assertEquals("cred-1", results.first().id)
    }

    @Test
    fun `test CredentialStorage query with notExpired filter`() = runBlocking {
        val storage = createMockStorage()
        val futureDate = java.time.Instant.now().plusSeconds(86400).toString()
        val cred1 = createTestCredential(id = "cred-1", expirationDate = futureDate)
        val cred2 = createTestCredential(id = "cred-2", expirationDate = null)
        storage.store(cred1)
        storage.store(cred2)
        
        val results = storage.query {
            notExpired()
        }
        
        assertTrue(results.size >= 2) // Both should be not expired
    }

    @Test
    fun `test CredentialStorage query with valid filter`() = runBlocking {
        val storage = createMockStorage()
        val futureDate = java.time.Instant.now().plusSeconds(86400).toString()
        val cred1 = createTestCredential(
            id = "cred-1",
            expirationDate = futureDate,
            proof = io.geoknoesis.vericore.credential.models.Proof(
                type = "Ed25519Signature2020",
                created = java.time.Instant.now().toString(),
                verificationMethod = "did:key:issuer#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "test-proof"
            )
        )
        storage.store(cred1)
        
        val results = storage.query {
            valid()
        }
        
        assertTrue(results.isNotEmpty())
    }

    private fun createMockStorage(): CredentialStorage {
        return object : CredentialStorage {
            private val storage = mutableMapOf<String, VerifiableCredential>()
            
            override suspend fun store(credential: VerifiableCredential): String {
                val id = credential.id ?: java.util.UUID.randomUUID().toString()
                storage[id] = credential.copy(id = id)
                return id
            }
            
            override suspend fun get(credentialId: String): VerifiableCredential? {
                return storage[credentialId]
            }
            
            override suspend fun list(filter: CredentialFilter?): List<VerifiableCredential> {
                val all = storage.values.toList()
                return if (filter == null) {
                    all
                } else {
                    all.filter { cred ->
                        (filter.issuer == null || cred.issuer == filter.issuer) &&
                        (filter.type == null || cred.type.any { it in filter.type }) &&
                        (filter.subjectId == null || cred.credentialSubject.jsonObject["id"]?.jsonPrimitive?.content == filter.subjectId) &&
                        (filter.expired == null || (filter.expired == (cred.expirationDate?.let {
                            try {
                                java.time.Instant.parse(it).isBefore(java.time.Instant.now())
                            } catch (e: Exception) {
                                false
                            }
                        } ?: false))) &&
                        (filter.revoked == null || (filter.revoked == (cred.credentialStatus != null)))
                    }
                }
            }
            
            override suspend fun delete(credentialId: String): Boolean {
                return storage.remove(credentialId) != null
            }
            
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> {
                val builder = CredentialQueryBuilder()
                builder.query()
                val predicate = builder.toPredicate()
                return storage.values.filter(predicate)
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
        issuanceDate: String = java.time.Instant.now().toString(),
        expirationDate: String? = null,
        proof: io.geoknoesis.vericore.credential.models.Proof? = null
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = types,
            issuer = issuerDid,
            credentialSubject = subject,
            issuanceDate = issuanceDate,
            expirationDate = expirationDate,
            proof = proof
        )
    }
}



