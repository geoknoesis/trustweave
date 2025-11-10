package io.geoknoesis.vericore.credential.wallet

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.did.DidCreationOptions
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*
import java.time.Instant

/**
 * Comprehensive tests for Wallet interface methods and extension functions.
 */
class WalletInterfaceTest {

    @Test
    fun `test Wallet capabilities property`() = runBlocking {
        val wallet = createBasicWallet()
        
        val capabilities = wallet.capabilities
        
        assertTrue(capabilities.credentialStorage)
        assertTrue(capabilities.credentialQuery)
        assertFalse(capabilities.collections)
        assertFalse(capabilities.didManagement)
    }

    @Test
    fun `test Wallet capabilities with organization`() = runBlocking {
        val wallet = createOrganizationWallet()
        
        val capabilities = wallet.capabilities
        
        assertTrue(capabilities.collections)
        assertTrue(capabilities.tags)
        assertTrue(capabilities.metadata)
    }

    @Test
    fun `test Wallet supports method`() = runBlocking {
        val wallet = createBasicWallet()
        
        // Wallet always implements CredentialStorage, but supports() only checks optional capabilities
        assertFalse(wallet.supports(CredentialOrganization::class))
        assertFalse(wallet.supports(CredentialLifecycle::class))
    }

    @Test
    fun `test Wallet supports with organization`() = runBlocking {
        val wallet = createOrganizationWallet()
        
        assertTrue(wallet.supports(CredentialOrganization::class))
        assertFalse(wallet.supports(CredentialLifecycle::class))
    }

    @Test
    fun `test Wallet getStatistics`() = runBlocking {
        val wallet = createBasicWallet()
        
        val cred1 = createTestCredential(
            id = "cred-1",
            expirationDate = Instant.now().plusSeconds(86400).toString(),
            proof = createTestProof()
        )
        val cred2 = createTestCredential(
            id = "cred-2",
            expirationDate = Instant.now().minusSeconds(86400).toString(), // Expired
            proof = createTestProof()
        )
        val cred3 = createTestCredential(
            id = "cred-3",
            expirationDate = null,
            proof = null // No proof
        )
        
        wallet.store(cred1)
        wallet.store(cred2)
        wallet.store(cred3)
        
        val stats = wallet.getStatistics()
        
        assertEquals(3, stats.totalCredentials)
        // cred1: has proof (true), not expired (true), not revoked (true) -> valid
        // cred2: has proof (true), expired (false) -> not valid
        // cred3: no proof (false) -> not valid
        assertEquals(1, stats.validCredentials)
        assertEquals(1, stats.expiredCredentials) // cred2
        assertEquals(0, stats.revokedCredentials)
    }

    @Test
    fun `test Wallet getStatistics with revoked credentials`() = runBlocking {
        val wallet = createBasicWallet()
        
        val cred = createTestCredential(
            id = "cred-revoked",
            credentialStatus = io.geoknoesis.vericore.credential.models.CredentialStatus(
                id = "https://example.com/status/1",
                type = "StatusList2021Entry"
            ),
            proof = createTestProof()
        )
        
        wallet.store(cred)
        
        val stats = wallet.getStatistics()
        
        assertEquals(1, stats.totalCredentials)
        // Has proof (true), not expired (true), but has credentialStatus (false) -> not valid
        assertEquals(0, stats.validCredentials)
        assertEquals(1, stats.revokedCredentials)
    }

    @Test
    fun `test WalletCapabilities supports method`() {
        val capabilities = WalletCapabilities(
            collections = true,
            tags = true,
            didManagement = true
        )
        
        assertTrue(capabilities.supports("collections"))
        assertTrue(capabilities.supports("tags"))
        assertTrue(capabilities.supports("didManagement"))
        assertTrue(capabilities.supports("did-management"))
        assertTrue(capabilities.supports("DID-MANAGEMENT")) // Case insensitive
        assertFalse(capabilities.supports("archive"))
    }

    @Test
    fun `test WalletCapabilities supports with presentation aliases`() {
        val capabilities = WalletCapabilities(
            createPresentation = true,
            selectiveDisclosure = true
        )
        
        assertTrue(capabilities.supports("createPresentation"))
        assertTrue(capabilities.supports("presentation"))
        assertTrue(capabilities.supports("selectiveDisclosure"))
        assertTrue(capabilities.supports("selective-disclosure"))
    }

    @Test
    fun `test Wallet withOrganization extension`() = runBlocking {
        val wallet = createOrganizationWallet()
        
        val result = wallet.withOrganization { org ->
            org.createCollection("Test Collection")
            "success"
        }
        
        assertEquals("success", result)
    }

    @Test
    fun `test Wallet withOrganization returns null for basic wallet`() = runBlocking {
        val wallet = createBasicWallet()
        
        val result = wallet.withOrganization { org ->
            "should not execute"
        }
        
        assertNull(result)
    }

    @Test
    fun `test Wallet withLifecycle extension`() = runBlocking {
        val wallet = createLifecycleWallet()
        
        val cred = createTestCredential()
        wallet.store(cred)
        
        val result = wallet.withLifecycle { lifecycle ->
            lifecycle.archive(cred.id!!)
            "success"
        }
        
        assertEquals("success", result)
    }

    @Test
    fun `test Wallet withPresentation extension`() = runBlocking {
        val wallet = createPresentationWallet()
        
        val result = wallet.withPresentation { presentation ->
            "success"
        }
        
        assertEquals("success", result)
    }

    @Test
    fun `test Wallet withDidManagement extension`() = runBlocking {
        val wallet = createDidManagementWallet()
        
        val result = wallet.withDidManagement { did ->
            did.createDid("key")
            "success"
        }
        
        assertEquals("success", result)
    }

    @Test
    fun `test Wallet withKeyManagement extension`() = runBlocking {
        val wallet = createKeyManagementWallet()
        
        val result = wallet.withKeyManagement { kms ->
            kms.generateKey("Ed25519")
            "success"
        }
        
        assertEquals("success", result)
    }

    @Test
    fun `test Wallet withIssuance extension`() = runBlocking {
        val wallet = createIssuanceWallet()
        
        val result = wallet.withIssuance { issuance ->
            "success"
        }
        
        assertEquals("success", result)
    }

    private fun createBasicWallet(): Wallet {
        return object : Wallet {
            override val walletId = "wallet-1"
            private val storage = mutableMapOf<String, VerifiableCredential>()
            
            override suspend fun store(credential: VerifiableCredential): String {
                val id = credential.id ?: "cred-${System.currentTimeMillis()}"
                storage[id] = credential
                return id
            }
            
            override suspend fun get(credentialId: String) = storage[credentialId]
            override suspend fun list(filter: CredentialFilter?) = storage.values.toList()
            override suspend fun delete(credentialId: String) = storage.remove(credentialId) != null
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit) = emptyList<VerifiableCredential>()
        }
    }

    private fun createOrganizationWallet(): Wallet {
        return object : Wallet, CredentialOrganization {
            override val walletId = "wallet-org"
            private val storage = mutableMapOf<String, VerifiableCredential>()
            private val collections = mutableMapOf<String, CredentialCollection>()
            
            override suspend fun store(credential: VerifiableCredential) = credential.id ?: "cred-${System.currentTimeMillis()}"
                .also { storage[it] = credential }
            
            override suspend fun get(credentialId: String) = storage[credentialId]
            override suspend fun list(filter: CredentialFilter?) = storage.values.toList()
            override suspend fun delete(credentialId: String) = storage.remove(credentialId) != null
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit) = emptyList<VerifiableCredential>()
            
            override suspend fun createCollection(name: String, description: String?) = "collection-${collections.size + 1}"
                .also { collections[it] = CredentialCollection(id = it, name = name, description = description) }
            
            override suspend fun getCollection(collectionId: String) = collections[collectionId]
            override suspend fun listCollections() = collections.values.toList()
            override suspend fun deleteCollection(collectionId: String) = collections.remove(collectionId) != null
            override suspend fun addToCollection(credentialId: String, collectionId: String) = true
            override suspend fun removeFromCollection(credentialId: String, collectionId: String) = true
            override suspend fun getCredentialsInCollection(collectionId: String) = emptyList<VerifiableCredential>()
            override suspend fun tagCredential(credentialId: String, tags: Set<String>) = true
            override suspend fun untagCredential(credentialId: String, tags: Set<String>) = true
            override suspend fun getTags(credentialId: String) = emptySet<String>()
            override suspend fun getAllTags() = emptySet<String>()
            override suspend fun findByTag(tag: String) = emptyList<VerifiableCredential>()
            override suspend fun addMetadata(credentialId: String, metadata: Map<String, Any>) = true
            override suspend fun getMetadata(credentialId: String) = null
            override suspend fun updateNotes(credentialId: String, notes: String?) = true
        }
    }

    private fun createLifecycleWallet(): Wallet {
        return object : Wallet, CredentialLifecycle {
            override val walletId = "wallet-lifecycle"
            private val storage = mutableMapOf<String, VerifiableCredential>()
            private val archived = mutableSetOf<String>()
            
            override suspend fun store(credential: VerifiableCredential) = credential.id ?: "cred-${System.currentTimeMillis()}"
                .also { storage[it] = credential }
            
            override suspend fun get(credentialId: String) = storage[credentialId]
            override suspend fun list(filter: CredentialFilter?) = storage.values.toList()
            override suspend fun delete(credentialId: String) = storage.remove(credentialId) != null
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit) = emptyList<VerifiableCredential>()
            
            override suspend fun archive(credentialId: String) = archived.add(credentialId)
            override suspend fun unarchive(credentialId: String) = archived.remove(credentialId)
            override suspend fun getArchived() = archived.mapNotNull { storage[it] }
            override suspend fun refreshCredential(credentialId: String) = storage[credentialId]
        }
    }

    private fun createPresentationWallet(): Wallet {
        return object : Wallet, CredentialPresentation {
            override val walletId = "wallet-presentation"
            private val storage = mutableMapOf<String, VerifiableCredential>()
            
            override suspend fun store(credential: VerifiableCredential) = credential.id ?: "cred-${System.currentTimeMillis()}"
                .also { storage[it] = credential }
            
            override suspend fun get(credentialId: String) = storage[credentialId]
            override suspend fun list(filter: CredentialFilter?) = storage.values.toList()
            override suspend fun delete(credentialId: String) = storage.remove(credentialId) != null
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit) = emptyList<VerifiableCredential>()
            
            override suspend fun createPresentation(credentialIds: List<String>, holderDid: String, options: io.geoknoesis.vericore.credential.PresentationOptions) = TODO()
            override suspend fun createSelectiveDisclosure(credentialIds: List<String>, disclosedFields: List<String>, holderDid: String, options: io.geoknoesis.vericore.credential.PresentationOptions) = TODO()
        }
    }

    private fun createDidManagementWallet(): Wallet {
        return object : Wallet, DidManagement {
            override val walletId = "wallet-did"
            override val walletDid = "did:key:wallet"
            override val holderDid = "did:key:holder"
            private val storage = mutableMapOf<String, VerifiableCredential>()
            
            override suspend fun store(credential: VerifiableCredential) = credential.id ?: "cred-${System.currentTimeMillis()}"
                .also { storage[it] = credential }
            
            override suspend fun get(credentialId: String) = storage[credentialId]
            override suspend fun list(filter: CredentialFilter?) = storage.values.toList()
            override suspend fun delete(credentialId: String) = storage.remove(credentialId) != null
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit) = emptyList<VerifiableCredential>()
            
            override suspend fun createDid(method: String, options: DidCreationOptions) = "did:key:new"
            override suspend fun getDids() = listOf(walletDid)
            override suspend fun getPrimaryDid() = walletDid
            override suspend fun setPrimaryDid(did: String) = true
            override suspend fun resolveDid(did: String) = mapOf("id" to did)
        }
    }

    private fun createKeyManagementWallet(): Wallet {
        return object : Wallet, KeyManagement {
            override val walletId = "wallet-kms"
            private val storage = mutableMapOf<String, VerifiableCredential>()
            
            override suspend fun store(credential: VerifiableCredential) = credential.id ?: "cred-${System.currentTimeMillis()}"
                .also { storage[it] = credential }
            
            override suspend fun get(credentialId: String) = storage[credentialId]
            override suspend fun list(filter: CredentialFilter?) = storage.values.toList()
            override suspend fun delete(credentialId: String) = storage.remove(credentialId) != null
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit) = emptyList<VerifiableCredential>()
            
            override suspend fun generateKey(algorithm: String, options: Map<String, Any?>) = "key-1"
            override suspend fun getKeys() = emptyList<KeyInfo>()
            override suspend fun getKey(keyId: String) = null
            override suspend fun deleteKey(keyId: String) = false
            override suspend fun sign(keyId: String, data: ByteArray) = ByteArray(64)
        }
    }

    private fun createIssuanceWallet(): Wallet {
        return object : Wallet, CredentialIssuance {
            override val walletId = "wallet-issuance"
            private val storage = mutableMapOf<String, VerifiableCredential>()
            
            override suspend fun store(credential: VerifiableCredential) = credential.id ?: "cred-${System.currentTimeMillis()}"
                .also { storage[it] = credential }
            
            override suspend fun get(credentialId: String) = storage[credentialId]
            override suspend fun list(filter: CredentialFilter?) = storage.values.toList()
            override suspend fun delete(credentialId: String) = storage.remove(credentialId) != null
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit) = emptyList<VerifiableCredential>()
            
            override suspend fun issueCredential(subjectDid: String, credentialType: String, claims: Map<String, Any>, options: io.geoknoesis.vericore.credential.CredentialIssuanceOptions) = TODO()
        }
    }

    private fun createTestCredential(
        id: String = "cred-${System.currentTimeMillis()}",
        expirationDate: String? = null,
        proof: io.geoknoesis.vericore.credential.models.Proof? = null,
        credentialStatus: io.geoknoesis.vericore.credential.models.CredentialStatus? = null
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = listOf("VerifiableCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject { put("id", "did:key:subject") },
            issuanceDate = Instant.now().toString(),
            expirationDate = expirationDate,
            proof = proof,
            credentialStatus = credentialStatus
        )
    }

    private fun createTestProof(): io.geoknoesis.vericore.credential.models.Proof {
        return io.geoknoesis.vericore.credential.models.Proof(
            type = "Ed25519Signature2020",
            created = Instant.now().toString(),
            verificationMethod = "did:key:issuer#key-1",
            proofPurpose = "assertionMethod"
        )
    }
}

