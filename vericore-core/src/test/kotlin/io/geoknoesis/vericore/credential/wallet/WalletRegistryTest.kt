package io.geoknoesis.vericore.credential.wallet

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for WalletRegistry API.
 */
class WalletRegistryTest {

    private lateinit var wallet1: Wallet
    private lateinit var wallet2: Wallet

    @BeforeEach
    fun setup() {
        WalletRegistry.clear()
        
        wallet1 = createTestWallet("wallet-1", "did:key:wallet1")
        wallet2 = createTestWallet("wallet-2", "did:key:wallet2")
    }

    @AfterEach
    fun cleanup() {
        WalletRegistry.clear()
    }

    @Test
    fun `test register wallet`() = runBlocking {
        WalletRegistry.register(wallet1)
        
        assertEquals(wallet1, WalletRegistry.get("wallet-1"))
    }

    @Test
    fun `test get wallet by ID`() = runBlocking {
        WalletRegistry.register(wallet1)
        WalletRegistry.register(wallet2)
        
        val retrieved = WalletRegistry.get("wallet-1")
        
        assertNotNull(retrieved)
        assertEquals("wallet-1", retrieved?.walletId)
    }

    @Test
    fun `test get wallet returns null when not found`() = runBlocking {
        assertNull(WalletRegistry.get("nonexistent"))
    }

    @Test
    fun `test get wallet by DID`() = runBlocking {
        val wallet = createTestWalletWithDid("wallet-1", "did:key:wallet1")
        WalletRegistry.register(wallet)
        
        val byDid = WalletRegistry.getByDid("did:key:wallet1")
        
        assertNotNull(byDid)
        assertEquals("wallet-1", byDid?.walletId)
    }

    @Test
    fun `test get wallet by DID returns null when not found`() = runBlocking {
        assertNull(WalletRegistry.getByDid("did:key:nonexistent"))
    }

    @Test
    fun `test getAll returns all wallets`() = runBlocking {
        WalletRegistry.register(wallet1)
        WalletRegistry.register(wallet2)
        
        val all = WalletRegistry.getAll()
        
        assertEquals(2, all.size)
        assertTrue(all.any { it.walletId == "wallet-1" })
        assertTrue(all.any { it.walletId == "wallet-2" })
    }

    @Test
    fun `test unregister wallet`() = runBlocking {
        WalletRegistry.register(wallet1)
        assertNotNull(WalletRegistry.get("wallet-1"))
        
        val unregistered = WalletRegistry.unregister("wallet-1")
        
        assertTrue(unregistered)
        assertNull(WalletRegistry.get("wallet-1"))
    }

    @Test
    fun `test unregister wallet returns false when not found`() = runBlocking {
        assertFalse(WalletRegistry.unregister("nonexistent"))
    }

    @Test
    fun `test clear all wallets`() = runBlocking {
        WalletRegistry.register(wallet1)
        WalletRegistry.register(wallet2)
        assertEquals(2, WalletRegistry.getAll().size)
        
        WalletRegistry.clear()
        
        assertEquals(0, WalletRegistry.getAll().size)
        assertNull(WalletRegistry.get("wallet-1"))
        assertNull(WalletRegistry.get("wallet-2"))
    }

    @Test
    fun `test findByCapability with type`() = runBlocking {
        val orgWallet = object : Wallet, CredentialOrganization {
            override val walletId = "org-wallet"
            override val capabilities = WalletCapabilities(collections = true)
            override suspend fun store(credential: VerifiableCredential) = "cred-1"
            override suspend fun get(credentialId: String) = null
            override suspend fun list(filter: CredentialFilter?) = emptyList<VerifiableCredential>()
            override suspend fun delete(credentialId: String) = false
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit) = emptyList<VerifiableCredential>()
            override suspend fun getStatistics() = WalletStatistics(0, 0, 0)
            override suspend fun createCollection(name: String, description: String?) = "coll-1"
            override suspend fun getCollection(collectionId: String) = null
            override suspend fun listCollections() = emptyList<CredentialCollection>()
            override suspend fun deleteCollection(collectionId: String) = false
            override suspend fun addToCollection(credentialId: String, collectionId: String) = false
            override suspend fun removeFromCollection(credentialId: String, collectionId: String) = false
            override suspend fun getCredentialsInCollection(collectionId: String) = emptyList<VerifiableCredential>()
            override suspend fun tagCredential(credentialId: String, tags: Set<String>) = false
            override suspend fun untagCredential(credentialId: String, tags: Set<String>) = false
            override suspend fun getTags(credentialId: String) = emptySet<String>()
            override suspend fun getAllTags() = emptySet<String>()
            override suspend fun findByTag(tag: String) = emptyList<VerifiableCredential>()
            override suspend fun addMetadata(credentialId: String, metadata: Map<String, Any>) = false
            override suspend fun getMetadata(credentialId: String) = null
            override suspend fun updateNotes(credentialId: String, notes: String?) = false
        }
        
        WalletRegistry.register(orgWallet)
        WalletRegistry.register(wallet1)
        
        val orgWallets = WalletRegistry.findByCapability(CredentialOrganization::class)
        
        assertEquals(1, orgWallets.size)
        assertEquals("org-wallet", (orgWallets.first() as Wallet).walletId)
    }

    @Test
    fun `test findByCapability with string`() = runBlocking {
        val walletWithCollections = object : Wallet, CredentialOrganization {
            override val walletId = "collections-wallet"
            override val capabilities = WalletCapabilities(collections = true)
            override suspend fun store(credential: VerifiableCredential) = "cred-1"
            override suspend fun get(credentialId: String) = null
            override suspend fun list(filter: CredentialFilter?) = emptyList<VerifiableCredential>()
            override suspend fun delete(credentialId: String) = false
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit) = emptyList<VerifiableCredential>()
            override suspend fun getStatistics() = WalletStatistics(0, 0, 0)
            override suspend fun createCollection(name: String, description: String?) = "coll-1"
            override suspend fun getCollection(collectionId: String) = null
            override suspend fun listCollections() = emptyList<CredentialCollection>()
            override suspend fun deleteCollection(collectionId: String) = false
            override suspend fun addToCollection(credentialId: String, collectionId: String) = false
            override suspend fun removeFromCollection(credentialId: String, collectionId: String) = false
            override suspend fun getCredentialsInCollection(collectionId: String) = emptyList<VerifiableCredential>()
            override suspend fun tagCredential(credentialId: String, tags: Set<String>) = false
            override suspend fun untagCredential(credentialId: String, tags: Set<String>) = false
            override suspend fun getTags(credentialId: String) = emptySet<String>()
            override suspend fun getAllTags() = emptySet<String>()
            override suspend fun findByTag(tag: String) = emptyList<VerifiableCredential>()
            override suspend fun addMetadata(credentialId: String, metadata: Map<String, Any>) = false
            override suspend fun getMetadata(credentialId: String) = null
            override suspend fun updateNotes(credentialId: String, notes: String?) = false
        }
        
        WalletRegistry.register(walletWithCollections)
        WalletRegistry.register(wallet1)
        
        val walletsWithCollections = WalletRegistry.findByCapability("collections")
        
        assertEquals(1, walletsWithCollections.size)
        assertEquals("collections-wallet", walletsWithCollections.first().walletId)
    }

    @Test
    fun `test findByCapability returns empty list when no matches`() = runBlocking {
        WalletRegistry.register(wallet1)
        
        val orgWallets = WalletRegistry.findByCapability(CredentialOrganization::class)
        
        assertTrue(orgWallets.isEmpty())
    }

    private fun createTestWallet(walletId: String, did: String? = null): Wallet {
        return object : Wallet {
            override val walletId = walletId
            override val capabilities = WalletCapabilities()
            
            override suspend fun store(credential: VerifiableCredential) = credential.id ?: "cred-${System.currentTimeMillis()}"
            override suspend fun get(credentialId: String) = null
            override suspend fun list(filter: CredentialFilter?) = emptyList<VerifiableCredential>()
            override suspend fun delete(credentialId: String) = false
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit) = emptyList<VerifiableCredential>()
            override suspend fun getStatistics() = WalletStatistics(0, 0, 0)
        }
    }

    private interface MockWalletWithDid : Wallet, DidManagement
    
    private fun createTestWalletWithDid(walletId: String, did: String): MockWalletWithDid {
        val walletDidValue = did
        return object : MockWalletWithDid {
            override val walletId = walletId
            override val walletDid = walletDidValue
            override val holderDid = walletDidValue
            override val capabilities = WalletCapabilities(didManagement = true)
            
            override suspend fun store(credential: VerifiableCredential) = credential.id ?: "cred-${System.currentTimeMillis()}"
            override suspend fun get(credentialId: String) = null
            override suspend fun list(filter: CredentialFilter?) = emptyList<VerifiableCredential>()
            override suspend fun delete(credentialId: String) = false
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit) = emptyList<VerifiableCredential>()
            override suspend fun getStatistics() = WalletStatistics(0, 0, 0)
            override suspend fun createDid(method: String, options: Map<String, Any?>) = walletDidValue
            override suspend fun getDids() = listOf(walletDidValue)
            override suspend fun getPrimaryDid() = walletDidValue
            override suspend fun setPrimaryDid(did: String) = did == walletDidValue
            override suspend fun resolveDid(did: String) = if (did == walletDidValue) mapOf("id" to did) else null
        }
    }
}

