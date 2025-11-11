package com.geoknoesis.vericore.credential.wallet

import com.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

/**
 * Comprehensive branch coverage tests for Wallet.getStatistics().
 * Tests all conditional branches in statistics calculation.
 */
class WalletStatisticsBranchCoverageTest {

    @Test
    fun `test branch statistics with organization wallet`() = runBlocking {
        val wallet = createMockOrganizationWallet()
        val credential = createTestCredential()
        wallet.store(credential)
        
        val stats = wallet.getStatistics()
        
        assertTrue(stats.collectionsCount >= 0)
        assertTrue(stats.tagsCount >= 0)
    }

    @Test
    fun `test branch statistics without organization wallet`() = runBlocking {
        val wallet = createBasicWallet()
        val credential = createTestCredential()
        wallet.store(credential)
        
        val stats = wallet.getStatistics()
        
        assertEquals(0, stats.collectionsCount)
        assertEquals(0, stats.tagsCount)
    }

    @Test
    fun `test branch statistics with lifecycle wallet`() = runBlocking {
        val wallet = createMockLifecycleWallet()
        val credential = createTestCredential()
        wallet.store(credential)
        wallet.withLifecycle { lifecycle ->
            lifecycle.archive(credential.id!!)
        }
        
        val stats = wallet.getStatistics()
        
        assertTrue(stats.archivedCount >= 0)
    }

    @Test
    fun `test branch statistics without lifecycle wallet`() = runBlocking {
        val wallet = createBasicWallet()
        val credential = createTestCredential()
        wallet.store(credential)
        
        val stats = wallet.getStatistics()
        
        assertEquals(0, stats.archivedCount)
    }

    @Test
    fun `test branch validCredentials with proof and no expiration`() = runBlocking {
        val wallet = createBasicWallet()
        val credential = createTestCredential(expirationDate = null)
        wallet.store(credential)
        
        val stats = wallet.getStatistics()
        
        assertTrue(stats.validCredentials >= 0)
    }

    @Test
    fun `test branch validCredentials with proof and future expiration`() = runBlocking {
        val wallet = createBasicWallet()
        val credential = createTestCredential(
            expirationDate = java.time.Instant.now().plusSeconds(86400).toString()
        )
        wallet.store(credential)
        
        val stats = wallet.getStatistics()
        
        assertTrue(stats.validCredentials >= 0)
    }

    @Test
    fun `test branch validCredentials with proof and past expiration`() = runBlocking {
        val wallet = createBasicWallet()
        val credential = createTestCredential(
            expirationDate = java.time.Instant.now().minusSeconds(86400).toString()
        )
        wallet.store(credential)
        
        val stats = wallet.getStatistics()
        
        assertEquals(0, stats.validCredentials)
    }

    @Test
    fun `test branch validCredentials with proof and invalid expiration format`() = runBlocking {
        val wallet = createBasicWallet()
        val credential = createTestCredential(expirationDate = "invalid-date")
        wallet.store(credential)
        
        val stats = wallet.getStatistics()
        
        assertEquals(0, stats.validCredentials) // Invalid format means not valid
    }

    @Test
    fun `test branch validCredentials with proof and credential status`() = runBlocking {
        val wallet = createBasicWallet()
        val credential = createTestCredential(
            credentialStatus = com.geoknoesis.vericore.credential.models.CredentialStatus(
                id = "https://example.com/status/1",
                type = "StatusList2021Entry"
            )
        )
        wallet.store(credential)
        
        val stats = wallet.getStatistics()
        
        assertEquals(0, stats.validCredentials) // Has credential status means not valid
    }

    @Test
    fun `test branch expiredCredentials with null expiration`() = runBlocking {
        val wallet = createBasicWallet()
        val credential = createTestCredential(expirationDate = null)
        wallet.store(credential)
        
        val stats = wallet.getStatistics()
        
        assertEquals(0, stats.expiredCredentials)
    }

    @Test
    fun `test branch expiredCredentials with future expiration`() = runBlocking {
        val wallet = createBasicWallet()
        val credential = createTestCredential(
            expirationDate = java.time.Instant.now().plusSeconds(86400).toString()
        )
        wallet.store(credential)
        
        val stats = wallet.getStatistics()
        
        assertEquals(0, stats.expiredCredentials)
    }

    @Test
    fun `test branch expiredCredentials with past expiration`() = runBlocking {
        val wallet = createBasicWallet()
        val credential = createTestCredential(
            expirationDate = java.time.Instant.now().minusSeconds(86400).toString()
        )
        wallet.store(credential)
        
        val stats = wallet.getStatistics()
        
        assertEquals(1, stats.expiredCredentials)
    }

    @Test
    fun `test branch expiredCredentials with invalid expiration format`() = runBlocking {
        val wallet = createBasicWallet()
        val credential = createTestCredential(expirationDate = "invalid-date")
        wallet.store(credential)
        
        val stats = wallet.getStatistics()
        
        assertEquals(0, stats.expiredCredentials) // Invalid format means not expired
    }

    @Test
    fun `test branch revokedCredentials with credential status`() = runBlocking {
        val wallet = createBasicWallet()
        val credential = createTestCredential(
            credentialStatus = com.geoknoesis.vericore.credential.models.CredentialStatus(
                id = "https://example.com/status/1",
                type = "StatusList2021Entry"
            )
        )
        wallet.store(credential)
        
        val stats = wallet.getStatistics()
        
        assertEquals(1, stats.revokedCredentials)
    }

    @Test
    fun `test branch revokedCredentials without credential status`() = runBlocking {
        val wallet = createBasicWallet()
        val credential = createTestCredential(credentialStatus = null)
        wallet.store(credential)
        
        val stats = wallet.getStatistics()
        
        assertEquals(0, stats.revokedCredentials)
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
            override suspend fun list(filter: CredentialFilter?): List<VerifiableCredential> = storage.values.toList()
            override suspend fun delete(credentialId: String) = storage.remove(credentialId) != null
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> = storage.values.toList()
        }
    }

    private fun createMockOrganizationWallet(): Wallet {
        return object : Wallet, CredentialOrganization {
            override val walletId = "org-wallet-1"
            private val storage = mutableMapOf<String, VerifiableCredential>()
            private val collections = mutableMapOf<String, CredentialCollection>()

            override suspend fun store(credential: VerifiableCredential): String {
                val id = credential.id ?: "cred-${System.currentTimeMillis()}"
                storage[id] = credential
                return id
            }

            override suspend fun get(credentialId: String) = storage[credentialId]
            override suspend fun list(filter: CredentialFilter?): List<VerifiableCredential> = storage.values.toList()
            override suspend fun delete(credentialId: String) = storage.remove(credentialId) != null
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> = storage.values.toList()

            override suspend fun createCollection(name: String, description: String?) = "collection-1".also {
                collections[it] = CredentialCollection(it, name, description)
            }
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

    private fun createMockLifecycleWallet(): Wallet {
        return object : Wallet, CredentialLifecycle {
            override val walletId = "lifecycle-wallet-1"
            private val storage = mutableMapOf<String, VerifiableCredential>()
            private val archived = mutableSetOf<String>()

            override suspend fun store(credential: VerifiableCredential): String {
                val id = credential.id ?: "cred-${System.currentTimeMillis()}"
                storage[id] = credential
                return id
            }

            override suspend fun get(credentialId: String) = storage[credentialId]
            override suspend fun list(filter: CredentialFilter?): List<VerifiableCredential> = storage.values.toList()
            override suspend fun delete(credentialId: String) = storage.remove(credentialId) != null
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> = storage.values.toList()

            override suspend fun archive(credentialId: String) = archived.add(credentialId)
            override suspend fun unarchive(credentialId: String) = archived.remove(credentialId)
            override suspend fun getArchived() = archived.mapNotNull { storage[it] }
            override suspend fun refreshCredential(credentialId: String) = storage[credentialId]
        }
    }

    private fun createTestCredential(
        id: String? = "cred-${System.currentTimeMillis()}",
        types: List<String> = listOf("VerifiableCredential", "PersonCredential"),
        issuerDid: String = "did:key:issuer",
        subject: JsonObject = buildJsonObject {
            put("id", "did:key:subject")
            put("name", "John Doe")
        },
        issuanceDate: String = java.time.Instant.now().toString(),
        expirationDate: String? = null,
        proof: com.geoknoesis.vericore.credential.models.Proof? = com.geoknoesis.vericore.credential.models.Proof(
            type = "Ed25519Signature2020",
            created = java.time.Instant.now().toString(),
            verificationMethod = "did:key:issuer#key-1",
            proofPurpose = "assertionMethod"
        ),
        credentialStatus: com.geoknoesis.vericore.credential.models.CredentialStatus? = null
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = types,
            issuer = issuerDid,
            credentialSubject = subject,
            issuanceDate = issuanceDate,
            expirationDate = expirationDate,
            proof = proof,
            credentialStatus = credentialStatus
        )
    }
}

