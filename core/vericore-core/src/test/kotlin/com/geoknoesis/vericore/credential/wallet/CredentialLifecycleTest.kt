package com.geoknoesis.vericore.credential.wallet

import com.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*
import java.util.UUID

/**
 * Comprehensive tests for CredentialLifecycle API using mock implementations.
 */
class CredentialLifecycleTest {

    @Test
    fun `test archive credential`() = runBlocking {
        val wallet = createMockLifecycleWallet()
        val credential = createTestCredential()
        val credentialId = wallet.store(credential)
        
        val archived = wallet.archive(credentialId)
        
        assertTrue(archived)
    }

    @Test
    fun `test archive credential fails when not found`() = runBlocking {
        val wallet = createMockLifecycleWallet()
        
        assertFalse(wallet.archive("nonexistent"))
    }

    @Test
    fun `test unarchive credential`() = runBlocking {
        val wallet = createMockLifecycleWallet()
        val credential = createTestCredential()
        val credentialId = wallet.store(credential)
        wallet.archive(credentialId)
        
        val unarchived = wallet.unarchive(credentialId)
        
        assertTrue(unarchived)
    }

    @Test
    fun `test get archived credentials`() = runBlocking {
        val wallet = createMockLifecycleWallet()
        val credential1 = createTestCredential(id = "cred-1")
        val credential2 = createTestCredential(id = "cred-2")
        val id1 = wallet.store(credential1)
        val id2 = wallet.store(credential2)
        wallet.archive(id1)
        wallet.archive(id2)
        
        val archived = wallet.getArchived()
        
        assertEquals(2, archived.size)
    }

    @Test
    fun `test refresh credential`() = runBlocking {
        val wallet = createMockLifecycleWallet()
        val credential = createTestCredential(
            refreshService = com.geoknoesis.vericore.credential.models.RefreshService(
                id = "https://example.com/refresh",
                type = "CredentialRefreshService2020",
                serviceEndpoint = "https://example.com/refresh"
            )
        )
        val credentialId = wallet.store(credential)
        
        val refreshed = wallet.refreshCredential(credentialId)
        
        // Placeholder implementation may return null
        // This test verifies the method executes
        assertNotNull(refreshed) // May be null in placeholder implementation
    }

    @Test
    fun `test refresh credential returns null when not found`() = runBlocking {
        val wallet = createMockLifecycleWallet()
        
        assertNull(wallet.refreshCredential("nonexistent"))
    }

    @Test
    fun `test archived credentials not in normal list`() = runBlocking {
        val wallet = createMockLifecycleWallet()
        val credential = createTestCredential()
        val credentialId = wallet.store(credential)
        wallet.archive(credentialId)
        
        val all = wallet.list(null)
        
        assertFalse(all.any { it.id == credential.id })
    }

    private fun createMockLifecycleWallet(): MockLifecycleWallet {
        return object : MockLifecycleWallet {
            private val credentials = mutableMapOf<String, VerifiableCredential>()
            private val archivedCredentials = mutableMapOf<String, VerifiableCredential>()
            
            override val walletId = UUID.randomUUID().toString()
            override val capabilities = WalletCapabilities(
                archive = true,
                refresh = true
            )
            
            override suspend fun store(credential: VerifiableCredential): String {
                val id = credential.id ?: UUID.randomUUID().toString()
                credentials[id] = credential
                return id
            }
            override suspend fun get(credentialId: String) = credentials[credentialId] ?: archivedCredentials[credentialId]
            override suspend fun list(filter: CredentialFilter?) = credentials.values.toList()
            override suspend fun delete(credentialId: String) = credentials.remove(credentialId) != null || archivedCredentials.remove(credentialId) != null
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> {
                val builder = CredentialQueryBuilder()
                builder.query()
                val predicate = builder.createPredicate()
                return credentials.values.filter(predicate)
            }
            override suspend fun getStatistics() = WalletStatistics(credentials.size, archivedCredentials.size, 0)
            
            override suspend fun archive(credentialId: String): Boolean {
                val credential = credentials.remove(credentialId) ?: return false
                archivedCredentials[credentialId] = credential
                return true
            }
            
            override suspend fun unarchive(credentialId: String): Boolean {
                val credential = archivedCredentials.remove(credentialId) ?: return false
                credentials[credentialId] = credential
                return true
            }
            
            override suspend fun getArchived() = archivedCredentials.values.toList()
            
            override suspend fun refreshCredential(credentialId: String): VerifiableCredential? {
                val credential = credentials[credentialId] ?: archivedCredentials[credentialId] ?: return null
                // Placeholder: return same credential
                // Real implementation would call refresh service
                return credential
            }
        }
    }
    
    private interface MockLifecycleWallet : Wallet, CredentialLifecycle

    private fun createTestCredential(
        id: String? = "https://example.com/credentials/1",
        types: List<String> = listOf("VerifiableCredential", "PersonCredential"),
        issuerDid: String = "did:key:issuer",
        subject: JsonObject = buildJsonObject {
            put("id", "did:key:subject")
            put("name", "John Doe")
        },
        issuanceDate: String = java.time.Instant.now().toString(),
        refreshService: com.geoknoesis.vericore.credential.models.RefreshService? = null
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = types,
            issuer = issuerDid,
            credentialSubject = subject,
            issuanceDate = issuanceDate,
            refreshService = refreshService
        )
    }
}

