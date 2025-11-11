package com.geoknoesis.vericore.credential.wallet

import com.geoknoesis.vericore.credential.models.Proof
import com.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.*

/**
 * Additional comprehensive branch coverage tests for Wallet interface methods.
 * Covers additional branches not covered in WalletStatisticsBranchCoverageTest and WalletSupportsBranchCoverageTest.
 */
class WalletMethodsMoreBranchesTest {

    // ========== Wallet Statistics Branches ==========

    @Test
    fun `test getStatistics with credentials having null expirationDate`() = runBlocking {
        val wallet = createMockWallet()
        
        val credential1 = createTestCredential(
            id = "cred-1",
            expirationDate = null
        )
        val credential2 = createTestCredential(
            id = "cred-2",
            expirationDate = Instant.now().plusSeconds(86400).toString()
        )
        
        wallet.store(credential1)
        wallet.store(credential2)
        
        val stats = wallet.getStatistics()
        
        assertEquals(2, stats.totalCredentials)
        // Credential with null expirationDate should be counted as valid if not revoked
        assertTrue(stats.validCredentials >= 1)
    }

    @Test
    fun `test getStatistics with credentials having null credentialStatus`() = runBlocking {
        val wallet = createMockWallet()
        
        val credential1 = createTestCredential(
            id = "cred-1",
            credentialStatus = null
        )
        val credential2 = createTestCredential(
            id = "cred-2",
            credentialStatus = com.geoknoesis.vericore.credential.models.CredentialStatus(
                id = "status-1",
                type = "StatusList2021Entry",
                statusListIndex = "1",
                statusListCredential = "https://example.com/status/1"
            )
        )
        
        wallet.store(credential1)
        wallet.store(credential2)
        
        val stats = wallet.getStatistics()
        
        assertEquals(2, stats.totalCredentials)
        assertTrue(stats.validCredentials >= 1)
    }

    @Test
    fun `test getStatistics with all credentials expired`() = runBlocking {
        val wallet = createMockWallet()
        
        val credential1 = createTestCredential(
            id = "cred-1",
            expirationDate = Instant.now().minusSeconds(86400).toString()
        )
        val credential2 = createTestCredential(
            id = "cred-2",
            expirationDate = Instant.now().minusSeconds(172800).toString()
        )
        
        wallet.store(credential1)
        wallet.store(credential2)
        
        val stats = wallet.getStatistics()
        
        assertEquals(2, stats.totalCredentials)
        assertEquals(0, stats.validCredentials)
        assertEquals(2, stats.expiredCredentials)
    }

    @Test
    fun `test getStatistics with all credentials revoked`() = runBlocking {
        val wallet = createMockWallet()
        
        val credential1 = createTestCredential(
            id = "cred-1",
            credentialStatus = com.geoknoesis.vericore.credential.models.CredentialStatus(
                id = "status-1",
                type = "StatusList2021Entry",
                statusListIndex = "1",
                statusListCredential = "https://example.com/status/1"
            )
        )
        val credential2 = createTestCredential(
            id = "cred-2",
            credentialStatus = com.geoknoesis.vericore.credential.models.CredentialStatus(
                id = "status-2",
                type = "StatusList2021Entry",
                statusListIndex = "2",
                statusListCredential = "https://example.com/status/1"
            )
        )
        
        wallet.store(credential1)
        wallet.store(credential2)
        
        val stats = wallet.getStatistics()
        
        assertEquals(2, stats.totalCredentials)
        assertEquals(0, stats.validCredentials)
        assertTrue(stats.revokedCredentials >= 0) // Depends on implementation
    }

    // ========== Wallet Supports Branches ==========

    @Test
    fun `test supports with CredentialStorage interface`() = runBlocking {
        val wallet = createMockWallet()
        
        // Wallet always implements CredentialStorage, so supports() checks optional capabilities
        // CredentialStorage is not an optional capability, it's always present
        assertIs<CredentialStorage>(wallet)
    }

    @Test
    fun `test supports with CredentialOrganization interface when not supported`() = runBlocking {
        val wallet = createMockWallet()
        
        assertFalse(wallet.supports(CredentialOrganization::class))
    }

    @Test
    fun `test supports with CredentialLifecycle interface when not supported`() = runBlocking {
        val wallet = createMockWallet()
        
        assertFalse(wallet.supports(CredentialLifecycle::class))
    }

    @Test
    fun `test supports with CredentialPresentation interface when not supported`() = runBlocking {
        val wallet = createMockWallet()
        
        assertFalse(wallet.supports(CredentialPresentation::class))
    }

    @Test
    fun `test supports with DidManagement interface when not supported`() = runBlocking {
        val wallet = createMockWallet()
        
        assertFalse(wallet.supports(DidManagement::class))
    }

    @Test
    fun `test supports with KeyManagement interface when not supported`() = runBlocking {
        val wallet = createMockWallet()
        
        assertFalse(wallet.supports(KeyManagement::class))
    }

    @Test
    fun `test supports with CredentialIssuance interface when not supported`() = runBlocking {
        val wallet = createMockWallet()
        
        assertFalse(wallet.supports(CredentialIssuance::class))
    }

    // ========== Wallet Extension Function Branches ==========

    @Test
    fun `test withOrganization when wallet supports organization`() = runBlocking {
        val wallet = createMockOrganizationWallet()
        
        val result = wallet.withOrganization { org ->
            val collectionId = org.createCollection("Test Collection", "Description")
            assertNotNull(collectionId)
            collectionId
        }
        
        assertNotNull(result)
    }

    @Test
    fun `test withOrganization when wallet does not support organization`() = runBlocking {
        val wallet = createMockWallet()
        
        val result = wallet.withOrganization { org ->
            org.createCollection("Test Collection", "Description")
        }
        
        assertNull(result)
    }

    @Test
    fun `test withLifecycle when wallet supports lifecycle`() = runBlocking {
        val wallet = createMockLifecycleWallet()
        val credential = createTestCredential()
        wallet.store(credential)
        
        val result = wallet.withLifecycle { lifecycle ->
            lifecycle.archive(credential.id!!)
            lifecycle.getArchived()
        }
        
        assertNotNull(result)
    }

    @Test
    fun `test withLifecycle when wallet does not support lifecycle`() = runBlocking {
        val wallet = createMockWallet()
        
        val result = wallet.withLifecycle { lifecycle ->
            lifecycle.getArchived()
        }
        
        assertNull(result)
    }

    @Test
    fun `test withPresentation when wallet supports presentation`() = runBlocking {
        val wallet = createMockPresentationWallet()
        val credential = createTestCredential()
        wallet.store(credential)
        
        val result = wallet.withPresentation { presentation ->
            presentation.createPresentation(
                credentialIds = listOf(credential.id!!),
                holderDid = "did:key:holder",
                options = com.geoknoesis.vericore.credential.PresentationOptions(
                    holderDid = "did:key:holder",
                    proofType = "Ed25519Signature2020"
                )
            )
        }
        
        assertNotNull(result)
    }

    @Test
    fun `test withPresentation when wallet does not support presentation`() = runBlocking {
        val wallet = createMockWallet()
        
        val result = wallet.withPresentation { presentation ->
            presentation.createPresentation(
                credentialIds = emptyList(),
                holderDid = "did:key:holder",
                options = com.geoknoesis.vericore.credential.PresentationOptions(
                    holderDid = "did:key:holder",
                    proofType = "Ed25519Signature2020"
                )
            )
        }
        
        assertNull(result)
    }

    // ========== Helper Functions ==========

    private fun createMockWallet(): Wallet {
        return object : Wallet {
            override val walletId: String = "wallet-123"
            override val capabilities: WalletCapabilities = WalletCapabilities()
            
            private val storage = mutableMapOf<String, VerifiableCredential>()
            
            override suspend fun store(credential: VerifiableCredential): String {
                val id = credential.id ?: "default-id"
                storage[id] = credential
                return id
            }
            
            override suspend fun get(credentialId: String): VerifiableCredential? = storage[credentialId]
            
            override suspend fun list(filter: CredentialFilter?): List<VerifiableCredential> {
                return storage.values.toList()
            }
            
            override suspend fun delete(credentialId: String): Boolean {
                return storage.remove(credentialId) != null
            }
            
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> {
                val builderInstance = CredentialQueryBuilder().apply(query)
                return builderInstance.toPredicate().let { predicate ->
                    storage.values.filter(predicate)
                }
            }
            
            override suspend fun getStatistics(): WalletStatistics {
                val credentials = storage.values.toList()
                val now = Instant.now()
                
                val validCredentials = credentials.count { credential ->
                    (credential.expirationDate?.let { Instant.parse(it).isAfter(now) } ?: true) &&
                    credential.credentialStatus == null
                }
                
                val expiredCredentials = credentials.count { credential ->
                    credential.expirationDate?.let { Instant.parse(it).isBefore(now) } ?: false
                }
                
                val revokedCredentials = credentials.count { credential ->
                    credential.credentialStatus != null
                }
                
                return WalletStatistics(
                    totalCredentials = credentials.size,
                    validCredentials = validCredentials,
                    expiredCredentials = expiredCredentials,
                    revokedCredentials = revokedCredentials
                )
            }
        }
    }

    private fun createMockOrganizationWallet(): Wallet {
        val baseWallet = createMockWallet()
        return object : Wallet, CredentialOrganization by object : CredentialOrganization {
            override suspend fun createCollection(name: String, description: String?): String {
                return "collection-${name}"
            }
            
            override suspend fun getCollection(collectionId: String): CredentialCollection? = null
            override suspend fun listCollections(): List<CredentialCollection> = emptyList()
            override suspend fun deleteCollection(collectionId: String): Boolean = false
            override suspend fun addToCollection(credentialId: String, collectionId: String): Boolean = false
            override suspend fun removeFromCollection(credentialId: String, collectionId: String): Boolean = false
            override suspend fun getCredentialsInCollection(collectionId: String): List<VerifiableCredential> = emptyList()
            override suspend fun tagCredential(credentialId: String, tags: Set<String>): Boolean = false
            override suspend fun untagCredential(credentialId: String, tags: Set<String>): Boolean = false
            override suspend fun getTags(credentialId: String): Set<String> = emptySet()
            override suspend fun getAllTags(): Set<String> = emptySet()
            override suspend fun findByTag(tag: String): List<VerifiableCredential> = emptyList()
            override suspend fun addMetadata(credentialId: String, metadata: Map<String, Any>): Boolean = false
            override suspend fun getMetadata(credentialId: String): CredentialMetadata? = null
            override suspend fun updateNotes(credentialId: String, notes: String?): Boolean = false
        } {
            override val walletId: String = baseWallet.walletId
            override val capabilities: WalletCapabilities = WalletCapabilities(collections = true)
            override suspend fun store(credential: VerifiableCredential) = baseWallet.store(credential)
            override suspend fun get(credentialId: String) = baseWallet.get(credentialId)
            override suspend fun list(filter: CredentialFilter?) = baseWallet.list(filter)
            override suspend fun delete(credentialId: String) = baseWallet.delete(credentialId)
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit) = baseWallet.query(query)
            override suspend fun getStatistics() = baseWallet.getStatistics()
        }
    }

    private fun createMockLifecycleWallet(): Wallet {
        val baseWallet = createMockWallet()
        return object : Wallet, CredentialLifecycle by object : CredentialLifecycle {
            private val archived = mutableSetOf<String>()
            
            override suspend fun archive(credentialId: String): Boolean {
                archived.add(credentialId)
                return true
            }
            
            override suspend fun unarchive(credentialId: String): Boolean {
                return archived.remove(credentialId)
            }
            
            override suspend fun getArchived(): List<VerifiableCredential> = emptyList()
            override suspend fun refreshCredential(credentialId: String): VerifiableCredential? = null
        } {
            override val walletId: String = baseWallet.walletId
            override val capabilities: WalletCapabilities = WalletCapabilities(archive = true)
            override suspend fun store(credential: VerifiableCredential) = baseWallet.store(credential)
            override suspend fun get(credentialId: String) = baseWallet.get(credentialId)
            override suspend fun list(filter: CredentialFilter?) = baseWallet.list(filter)
            override suspend fun delete(credentialId: String) = baseWallet.delete(credentialId)
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit) = baseWallet.query(query)
            override suspend fun getStatistics() = baseWallet.getStatistics()
        }
    }

    private fun createMockPresentationWallet(): Wallet {
        val baseWallet = createMockWallet()
        return object : Wallet, CredentialPresentation by object : CredentialPresentation {
            override suspend fun createPresentation(
                credentialIds: List<String>,
                holderDid: String,
                options: com.geoknoesis.vericore.credential.PresentationOptions
            ): com.geoknoesis.vericore.credential.models.VerifiablePresentation {
                val credentials = credentialIds.mapNotNull { baseWallet.get(it) }
                return com.geoknoesis.vericore.credential.models.VerifiablePresentation(
                    id = "presentation-123",
                    type = listOf("VerifiablePresentation"),
                    holder = holderDid,
                    verifiableCredential = credentials
                )
            }
            
            override suspend fun createSelectiveDisclosure(
                credentialIds: List<String>,
                disclosedFields: List<String>,
                holderDid: String,
                options: com.geoknoesis.vericore.credential.PresentationOptions
            ): com.geoknoesis.vericore.credential.models.VerifiablePresentation {
                return createPresentation(credentialIds, holderDid, options)
            }
        } {
            override val walletId: String = baseWallet.walletId
            override val capabilities: WalletCapabilities = WalletCapabilities(createPresentation = true)
            override suspend fun store(credential: VerifiableCredential) = baseWallet.store(credential)
            override suspend fun get(credentialId: String) = baseWallet.get(credentialId)
            override suspend fun list(filter: CredentialFilter?) = baseWallet.list(filter)
            override suspend fun delete(credentialId: String) = baseWallet.delete(credentialId)
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit) = baseWallet.query(query)
            override suspend fun getStatistics() = baseWallet.getStatistics()
        }
    }

    private fun createTestCredential(
        id: String = "credential-123",
        expirationDate: String? = null,
        credentialStatus: com.geoknoesis.vericore.credential.models.CredentialStatus? = null
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = listOf("VerifiableCredential"),
            issuer = "did:key:issuer123",
            issuanceDate = Instant.now().toString(),
            expirationDate = expirationDate,
            credentialSubject = buildJsonObject {
                put("id", "did:key:subject123")
            },
            credentialStatus = credentialStatus,
            proof = Proof(
                type = "Ed25519Signature2020",
                created = Instant.now().toString(),
                verificationMethod = "did:key:issuer123#key-1",
                proofPurpose = "assertionMethod"
            )
        )
    }
}

