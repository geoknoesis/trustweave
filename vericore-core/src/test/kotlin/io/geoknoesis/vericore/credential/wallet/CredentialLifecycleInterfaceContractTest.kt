package io.geoknoesis.vericore.credential.wallet

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive interface contract tests for CredentialLifecycle.
 * Tests all methods, branches, and edge cases.
 */
class CredentialLifecycleInterfaceContractTest {

    @Test
    fun `test CredentialLifecycle archive returns true`() = runBlocking {
        val lifecycle = createMockLifecycle()
        val cred = createTestCredential(id = "cred-1")
        (lifecycle as CredentialStorage).store(cred)
        
        val archived = lifecycle.archive("cred-1")
        
        assertTrue(archived)
        assertTrue(lifecycle.getArchived().any { it.id == "cred-1" })
    }

    @Test
    fun `test CredentialLifecycle archive returns false for non-existent credential`() = runBlocking {
        val lifecycle = createMockLifecycle()
        
        val archived = lifecycle.archive("non-existent")
        
        assertFalse(archived)
    }

    @Test
    fun `test CredentialLifecycle unarchive returns true`() = runBlocking {
        val lifecycle = createMockLifecycle()
        val cred = createTestCredential(id = "cred-1")
        (lifecycle as CredentialStorage).store(cred)
        lifecycle.archive("cred-1")
        
        val unarchived = lifecycle.unarchive("cred-1")
        
        assertTrue(unarchived)
        assertTrue(lifecycle.getArchived().none { it.id == "cred-1" })
    }

    @Test
    fun `test CredentialLifecycle unarchive returns false for non-existent credential`() = runBlocking {
        val lifecycle = createMockLifecycle()
        
        val unarchived = lifecycle.unarchive("non-existent")
        
        assertFalse(unarchived)
    }

    @Test
    fun `test CredentialLifecycle getArchived returns archived credentials`() = runBlocking {
        val lifecycle = createMockLifecycle()
        val cred1 = createTestCredential(id = "cred-1")
        val cred2 = createTestCredential(id = "cred-2")
        (lifecycle as CredentialStorage).store(cred1)
        (lifecycle as CredentialStorage).store(cred2)
        lifecycle.archive("cred-1")
        lifecycle.archive("cred-2")
        
        val archived = lifecycle.getArchived()
        
        assertEquals(2, archived.size)
        assertTrue(archived.any { it.id == "cred-1" })
        assertTrue(archived.any { it.id == "cred-2" })
    }

    @Test
    fun `test CredentialLifecycle getArchived returns empty list when no archived`() = runBlocking {
        val lifecycle = createMockLifecycle()
        
        val archived = lifecycle.getArchived()
        
        assertTrue(archived.isEmpty())
    }

    @Test
    fun `test CredentialLifecycle refreshCredential returns refreshed credential`() = runBlocking {
        val lifecycle = createMockLifecycle()
        val cred = createTestCredential(id = "cred-1")
        (lifecycle as CredentialStorage).store(cred)
        
        val refreshed = lifecycle.refreshCredential("cred-1")
        
        assertNotNull(refreshed)
        assertEquals("cred-1", refreshed?.id)
    }

    @Test
    fun `test CredentialLifecycle refreshCredential returns null for non-existent credential`() = runBlocking {
        val lifecycle = createMockLifecycle()
        
        val refreshed = lifecycle.refreshCredential("non-existent")
        
        assertNull(refreshed)
    }

    @Test
    fun `test CredentialLifecycle archive then unarchive works correctly`() = runBlocking {
        val lifecycle = createMockLifecycle()
        val cred = createTestCredential(id = "cred-1")
        (lifecycle as CredentialStorage).store(cred)
        
        assertTrue(lifecycle.archive("cred-1"))
        assertTrue(lifecycle.getArchived().any { it.id == "cred-1" })
        
        assertTrue(lifecycle.unarchive("cred-1"))
        assertTrue(lifecycle.getArchived().none { it.id == "cred-1" })
    }

    private fun createMockLifecycle(): CredentialLifecycle {
        return object : CredentialStorage, CredentialLifecycle {
            private val storage = mutableMapOf<String, VerifiableCredential>()
            private val archived = mutableSetOf<String>()
            
            override suspend fun store(credential: VerifiableCredential): String {
                val id = credential.id ?: java.util.UUID.randomUUID().toString()
                storage[id] = credential.copy(id = id)
                return id
            }
            
            override suspend fun get(credentialId: String): VerifiableCredential? = storage[credentialId]
            
            override suspend fun list(filter: CredentialFilter?): List<VerifiableCredential> {
                return storage.values.filter { it.id !in archived }
            }
            
            override suspend fun delete(credentialId: String): Boolean = storage.remove(credentialId) != null
            
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> {
                val builder = CredentialQueryBuilder()
                builder.query()
                return storage.values.filter { it.id !in archived }.filter(builder.toPredicate())
            }
            
            override suspend fun archive(credentialId: String): Boolean {
                if (!storage.containsKey(credentialId)) return false
                archived.add(credentialId)
                return true
            }
            
            override suspend fun unarchive(credentialId: String): Boolean {
                return archived.remove(credentialId)
            }
            
            override suspend fun getArchived(): List<VerifiableCredential> {
                return archived.mapNotNull { storage[it] }
            }
            
            override suspend fun refreshCredential(credentialId: String): VerifiableCredential? {
                return storage[credentialId]?.copy(
                    issuanceDate = java.time.Instant.now().toString()
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

