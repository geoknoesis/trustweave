package io.geoknoesis.vericore.credential.wallet

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive branch coverage tests for WalletRegistry.
 * Tests all conditional branches in registration and lookup.
 */
class WalletRegistryBranchCoverageTest {

    @BeforeEach
    fun setup() {
        WalletRegistry.clear()
    }

    @AfterEach
    fun cleanup() {
        WalletRegistry.clear()
    }

    // ========== register() Branch Coverage ==========

    @Test
    fun `test branch register wallet without DidManagement`() {
        val wallet = createBasicWallet()
        
        WalletRegistry.register(wallet)
        
        assertNotNull(WalletRegistry.get(wallet.walletId))
        assertNull(WalletRegistry.getByDid("any-did"))
    }

    @Test
    fun `test branch register wallet with DidManagement`() {
        val wallet = createMockDidManagementWallet()
        
        WalletRegistry.register(wallet)
        
        assertNotNull(WalletRegistry.get(wallet.walletId))
        val didWallet = wallet as DidManagement
        assertNotNull(WalletRegistry.getByDid(didWallet.walletDid))
        assertNotNull(WalletRegistry.getByDid(didWallet.holderDid))
    }

    @Test
    fun `test branch register multiple wallets`() {
        val wallet1 = createBasicWallet()
        val wallet2 = createMockDidManagementWallet()
        
        WalletRegistry.register(wallet1)
        WalletRegistry.register(wallet2)
        
        assertEquals(2, WalletRegistry.getAll().size)
    }

    // ========== getByDid() Branch Coverage ==========

    @Test
    fun `test branch getByDid with registered DID`() {
        val wallet = createMockDidManagementWallet()
        WalletRegistry.register(wallet)
        
        val didWallet = wallet as DidManagement
        val retrieved = WalletRegistry.getByDid(didWallet.walletDid)
        
        assertNotNull(retrieved)
        assertEquals(wallet.walletId, retrieved?.walletId)
    }

    @Test
    fun `test branch getByDid with holder DID`() {
        val wallet = createMockDidManagementWallet()
        WalletRegistry.register(wallet)
        
        val didWallet = wallet as DidManagement
        val retrieved = WalletRegistry.getByDid(didWallet.holderDid)
        
        assertNotNull(retrieved)
        assertEquals(wallet.walletId, retrieved?.walletId)
    }

    @Test
    fun `test branch getByDid with unregistered DID`() {
        assertNull(WalletRegistry.getByDid("did:key:nonexistent"))
    }

    // ========== findByCapability() Branch Coverage ==========

    @Test
    fun `test branch findByCapability with CredentialOrganization`() {
        val orgWallet = createMockOrganizationWallet()
        val basicWallet = createBasicWallet()
        
        WalletRegistry.register(orgWallet)
        WalletRegistry.register(basicWallet)
        
        val orgWallets = WalletRegistry.findByCapability(CredentialOrganization::class)
        
        assertEquals(1, orgWallets.size)
        assertEquals(orgWallet.walletId, (orgWallets.first() as Wallet).walletId)
    }

    @Test
    fun `test branch findByCapability with CredentialLifecycle`() {
        val lifecycleWallet = createMockLifecycleWallet()
        val basicWallet = createBasicWallet()
        
        WalletRegistry.register(lifecycleWallet)
        WalletRegistry.register(basicWallet)
        
        val lifecycleWallets = WalletRegistry.findByCapability(CredentialLifecycle::class)
        
        assertEquals(1, lifecycleWallets.size)
    }

    @Test
    fun `test branch findByCapability with DidManagement`() {
        val didWallet = createMockDidManagementWallet()
        val basicWallet = createBasicWallet()
        
        WalletRegistry.register(didWallet)
        WalletRegistry.register(basicWallet)
        
        val didWallets = WalletRegistry.findByCapability(DidManagement::class)
        
        assertEquals(1, didWallets.size)
    }

    @Test
    fun `test branch findByCapability with string feature collections`() {
        val orgWallet = createMockOrganizationWallet()
        val basicWallet = createBasicWallet()
        
        WalletRegistry.register(orgWallet)
        WalletRegistry.register(basicWallet)
        
        val wallets = WalletRegistry.findByCapability("collections")
        
        assertEquals(1, wallets.size)
    }

    @Test
    fun `test branch findByCapability with string feature did-management`() {
        val didWallet = createMockDidManagementWallet()
        val basicWallet = createBasicWallet()
        
        WalletRegistry.register(didWallet)
        WalletRegistry.register(basicWallet)
        
        val wallets = WalletRegistry.findByCapability("did-management")
        
        assertEquals(1, wallets.size)
    }

    @Test
    fun `test branch findByCapability with string feature nonexistent`() {
        val wallet = createBasicWallet()
        WalletRegistry.register(wallet)
        
        val wallets = WalletRegistry.findByCapability("nonexistent-feature")
        
        assertTrue(wallets.isEmpty())
    }

    // ========== unregister() Branch Coverage ==========

    @Test
    fun `test branch unregister wallet without DidManagement`() {
        val wallet = createBasicWallet()
        WalletRegistry.register(wallet)
        
        val result = WalletRegistry.unregister(wallet.walletId)
        
        assertTrue(result)
        assertNull(WalletRegistry.get(wallet.walletId))
    }

    @Test
    fun `test branch unregister wallet with DidManagement`() {
        val wallet = createMockDidManagementWallet()
        WalletRegistry.register(wallet)
        
        val result = WalletRegistry.unregister(wallet.walletId)
        
        assertTrue(result)
        assertNull(WalletRegistry.get(wallet.walletId))
        val didWallet = wallet as? DidManagement
        if (didWallet != null) {
            assertNull(WalletRegistry.getByDid(didWallet.walletDid))
            assertNull(WalletRegistry.getByDid(didWallet.holderDid))
        }
    }

    @Test
    fun `test branch unregister nonexistent wallet`() {
        val result = WalletRegistry.unregister("nonexistent")
        
        assertFalse(result)
    }

    // ========== clear() Branch Coverage ==========

    @Test
    fun `test branch clear removes all wallets`() {
        val wallet1 = createBasicWallet()
        val wallet2 = createMockDidManagementWallet()
        
        WalletRegistry.register(wallet1)
        WalletRegistry.register(wallet2)
        
        WalletRegistry.clear()
        
        assertTrue(WalletRegistry.getAll().isEmpty())
        val didWallet2 = wallet2 as? DidManagement
        if (didWallet2 != null) {
            assertNull(WalletRegistry.getByDid(didWallet2.walletDid))
        }
    }

    // ========== Helper Methods ==========

    private fun createBasicWallet(): Wallet {
        return object : Wallet {
            override val walletId = "wallet-${System.currentTimeMillis()}"
            private val storage = mutableMapOf<String, VerifiableCredential>()

            override suspend fun store(credential: VerifiableCredential): String {
                val id = credential.id ?: "cred-${System.currentTimeMillis()}"
                storage[id] = credential
                return id
            }

            override suspend fun get(credentialId: String) = storage[credentialId]
            override suspend fun list(filter: CredentialFilter?): List<VerifiableCredential> = storage.values.toList()
            override suspend fun delete(credentialId: String) = storage.remove(credentialId) != null
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> = storage.values.toList()
        }
    }

    private fun createMockOrganizationWallet(): Wallet {
        return object : Wallet, CredentialOrganization {
            override val walletId = "org-wallet-${System.currentTimeMillis()}"
            private val storage = mutableMapOf<String, VerifiableCredential>()

            override suspend fun store(credential: VerifiableCredential): String {
                val id = credential.id ?: "cred-${System.currentTimeMillis()}"
                storage[id] = credential
                return id
            }

            override suspend fun get(credentialId: String) = storage[credentialId]
            override suspend fun list(filter: CredentialFilter?): List<VerifiableCredential> = storage.values.toList()
            override suspend fun delete(credentialId: String) = storage.remove(credentialId) != null
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> = storage.values.toList()

            override suspend fun createCollection(name: String, description: String?) = "collection-1"
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
    }

    private fun createMockLifecycleWallet(): Wallet {
        return object : Wallet, CredentialLifecycle {
            override val walletId = "lifecycle-wallet-${System.currentTimeMillis()}"
            private val storage = mutableMapOf<String, VerifiableCredential>()

            override suspend fun store(credential: VerifiableCredential): String {
                val id = credential.id ?: "cred-${System.currentTimeMillis()}"
                storage[id] = credential
                return id
            }

            override suspend fun get(credentialId: String) = storage[credentialId]
            override suspend fun list(filter: CredentialFilter?): List<VerifiableCredential> = storage.values.toList()
            override suspend fun delete(credentialId: String) = storage.remove(credentialId) != null
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> = storage.values.toList()

            override suspend fun archive(credentialId: String) = false
            override suspend fun unarchive(credentialId: String) = false
            override suspend fun getArchived() = emptyList<VerifiableCredential>()
            override suspend fun refreshCredential(credentialId: String) = null
        }
    }

    private fun createMockDidManagementWallet(): Wallet {
        return object : Wallet, DidManagement {
            override val walletId = "did-wallet-${System.currentTimeMillis()}"
            override val walletDid = "did:key:wallet123"
            override val holderDid = "did:key:holder123"
            private val storage = mutableMapOf<String, VerifiableCredential>()

            override suspend fun store(credential: VerifiableCredential): String {
                val id = credential.id ?: "cred-${System.currentTimeMillis()}"
                storage[id] = credential
                return id
            }

            override suspend fun get(credentialId: String) = storage[credentialId]
            override suspend fun list(filter: CredentialFilter?): List<VerifiableCredential> = storage.values.toList()
            override suspend fun delete(credentialId: String) = storage.remove(credentialId) != null
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> = storage.values.toList()

            override suspend fun createDid(method: String, options: Map<String, Any?>) = "did:$method:123"
            override suspend fun getDids() = listOf(walletDid)
            override suspend fun getPrimaryDid() = walletDid
            override suspend fun setPrimaryDid(did: String) = false
            override suspend fun resolveDid(did: String) = null
        }
    }
}

