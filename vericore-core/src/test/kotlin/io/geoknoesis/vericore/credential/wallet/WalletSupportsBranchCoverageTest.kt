package io.geoknoesis.vericore.credential.wallet

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.did.DidCreationOptions
import kotlin.reflect.KClass
import kotlinx.serialization.json.*
import kotlin.test.*

/**
 * Comprehensive branch coverage tests for Wallet.supports() method.
 * Tests all branches in the when expression.
 */
class WalletSupportsBranchCoverageTest {

    @Test
    fun `test branch supports CredentialOrganization`() {
        val wallet = createMockOrganizationWallet()
        
        assertTrue(wallet.supports(CredentialOrganization::class))
    }

    @Test
    fun `test branch supports CredentialOrganization returns false`() {
        val wallet = createBasicWallet()
        
        assertFalse(wallet.supports(CredentialOrganization::class))
    }

    @Test
    fun `test branch supports CredentialLifecycle`() {
        val wallet = createMockLifecycleWallet()
        
        assertTrue(wallet.supports(CredentialLifecycle::class))
    }

    @Test
    fun `test branch supports CredentialLifecycle returns false`() {
        val wallet = createBasicWallet()
        
        assertFalse(wallet.supports(CredentialLifecycle::class))
    }

    @Test
    fun `test branch supports CredentialPresentation`() {
        val wallet = createMockPresentationWallet()
        
        assertTrue(wallet.supports(CredentialPresentation::class))
    }

    @Test
    fun `test branch supports CredentialPresentation returns false`() {
        val wallet = createBasicWallet()
        
        assertFalse(wallet.supports(CredentialPresentation::class))
    }

    @Test
    fun `test branch supports DidManagement`() {
        val wallet = createMockDidManagementWallet()
        
        assertTrue(wallet.supports(DidManagement::class))
    }

    @Test
    fun `test branch supports DidManagement returns false`() {
        val wallet = createBasicWallet()
        
        assertFalse(wallet.supports(DidManagement::class))
    }

    @Test
    fun `test branch supports KeyManagement`() {
        val wallet = createMockKeyManagementWallet()
        
        assertTrue(wallet.supports(KeyManagement::class))
    }

    @Test
    fun `test branch supports KeyManagement returns false`() {
        val wallet = createBasicWallet()
        
        assertFalse(wallet.supports(KeyManagement::class))
    }

    @Test
    fun `test branch supports CredentialIssuance`() {
        val wallet = createMockIssuanceWallet()
        
        assertTrue(wallet.supports(CredentialIssuance::class))
    }

    @Test
    fun `test branch supports CredentialIssuance returns false`() {
        val wallet = createBasicWallet()
        
        assertFalse(wallet.supports(CredentialIssuance::class))
    }

    @Test
    fun `test branch supports unknown capability returns false`() {
        val wallet = createBasicWallet()
        
        assertFalse(wallet.supports(String::class))
        assertFalse(wallet.supports(Int::class))
    }

    // ========== Helper Methods ==========

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
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> = storage.values.toList()
        }
    }

    private fun createMockOrganizationWallet(): Wallet {
        return object : Wallet, CredentialOrganization {
            override val walletId = "org-wallet-1"
            private val storage = mutableMapOf<String, VerifiableCredential>()

            override suspend fun store(credential: VerifiableCredential): String {
                val id = credential.id ?: "cred-${System.currentTimeMillis()}"
                storage[id] = credential
                return id
            }

            override suspend fun get(credentialId: String) = storage[credentialId]
            override suspend fun list(filter: CredentialFilter?) = storage.values.toList()
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
            override val walletId = "lifecycle-wallet-1"
            private val storage = mutableMapOf<String, VerifiableCredential>()

            override suspend fun store(credential: VerifiableCredential): String {
                val id = credential.id ?: "cred-${System.currentTimeMillis()}"
                storage[id] = credential
                return id
            }

            override suspend fun get(credentialId: String) = storage[credentialId]
            override suspend fun list(filter: CredentialFilter?) = storage.values.toList()
            override suspend fun delete(credentialId: String) = storage.remove(credentialId) != null
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> = storage.values.toList()

            override suspend fun archive(credentialId: String) = false
            override suspend fun unarchive(credentialId: String) = false
            override suspend fun getArchived() = emptyList<VerifiableCredential>()
            override suspend fun refreshCredential(credentialId: String) = null
        }
    }

    private fun createMockPresentationWallet(): Wallet {
        return object : Wallet, CredentialPresentation {
            override val walletId = "presentation-wallet-1"
            private val storage = mutableMapOf<String, VerifiableCredential>()

            override suspend fun store(credential: VerifiableCredential): String {
                val id = credential.id ?: "cred-${System.currentTimeMillis()}"
                storage[id] = credential
                return id
            }

            override suspend fun get(credentialId: String) = storage[credentialId]
            override suspend fun list(filter: CredentialFilter?) = storage.values.toList()
            override suspend fun delete(credentialId: String) = storage.remove(credentialId) != null
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> = storage.values.toList()

            override suspend fun createPresentation(
                credentialIds: List<String>,
                holderDid: String,
                options: io.geoknoesis.vericore.credential.PresentationOptions
            ) = io.geoknoesis.vericore.credential.models.VerifiablePresentation(
                type = listOf("VerifiablePresentation"),
                verifiableCredential = emptyList(),
                holder = holderDid
            )

            override suspend fun createSelectiveDisclosure(
                credentialIds: List<String>,
                disclosedFields: List<String>,
                holderDid: String,
                options: io.geoknoesis.vericore.credential.PresentationOptions
            ) = createPresentation(credentialIds, holderDid, options)
        }
    }

    private fun createMockDidManagementWallet(): Wallet {
        return object : Wallet, DidManagement {
            override val walletId = "did-wallet-1"
            override val walletDid = "did:key:wallet123"
            override val holderDid = "did:key:holder123"
            private val storage = mutableMapOf<String, VerifiableCredential>()

            override suspend fun store(credential: VerifiableCredential): String {
                val id = credential.id ?: "cred-${System.currentTimeMillis()}"
                storage[id] = credential
                return id
            }

            override suspend fun get(credentialId: String) = storage[credentialId]
            override suspend fun list(filter: CredentialFilter?) = storage.values.toList()
            override suspend fun delete(credentialId: String) = storage.remove(credentialId) != null
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> = storage.values.toList()

            override suspend fun createDid(method: String, options: DidCreationOptions) = "did:$method:123"
            override suspend fun getDids() = listOf(walletDid)
            override suspend fun getPrimaryDid() = walletDid
            override suspend fun setPrimaryDid(did: String) = false
            override suspend fun resolveDid(did: String) = null
        }
    }

    private fun createMockKeyManagementWallet(): Wallet {
        return object : Wallet, KeyManagement {
            override val walletId = "kms-wallet-1"
            private val storage = mutableMapOf<String, VerifiableCredential>()

            override suspend fun store(credential: VerifiableCredential): String {
                val id = credential.id ?: "cred-${System.currentTimeMillis()}"
                storage[id] = credential
                return id
            }

            override suspend fun get(credentialId: String) = storage[credentialId]
            override suspend fun list(filter: CredentialFilter?) = storage.values.toList()
            override suspend fun delete(credentialId: String) = storage.remove(credentialId) != null
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> = storage.values.toList()

            override suspend fun generateKey(algorithm: String, options: Map<String, Any?>) = "key-1"
            override suspend fun getKeys() = emptyList<KeyInfo>()
            override suspend fun getKey(keyId: String) = null
            override suspend fun deleteKey(keyId: String) = false
            override suspend fun sign(keyId: String, data: ByteArray) = ByteArray(0)
        }
    }

    private fun createMockIssuanceWallet(): Wallet {
        return object : Wallet, CredentialIssuance {
            override val walletId = "issuance-wallet-1"
            private val storage = mutableMapOf<String, VerifiableCredential>()

            override suspend fun store(credential: VerifiableCredential): String {
                val id = credential.id ?: "cred-${System.currentTimeMillis()}"
                storage[id] = credential
                return id
            }

            override suspend fun get(credentialId: String) = storage[credentialId]
            override suspend fun list(filter: CredentialFilter?) = storage.values.toList()
            override suspend fun delete(credentialId: String) = storage.remove(credentialId) != null
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> = storage.values.toList()

            override suspend fun issueCredential(
                subjectDid: String,
                credentialType: String,
                claims: Map<String, Any>,
                options: io.geoknoesis.vericore.credential.CredentialIssuanceOptions
            ) = VerifiableCredential(
                type = listOf("VerifiableCredential", credentialType),
                issuer = "did:key:issuer",
                issuanceDate = java.time.Instant.now().toString(),
                credentialSubject = buildJsonObject {
                    put("id", subjectDid)
                }
            )
        }
    }
}

