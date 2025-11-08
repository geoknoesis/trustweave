package io.geoknoesis.vericore.credential.wallet

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.models.VerifiablePresentation
import io.geoknoesis.vericore.credential.PresentationOptions
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*
import java.time.Instant
import java.util.UUID

/**
 * Comprehensive tests for CredentialOrganization API using mock implementations.
 */
class CredentialOrganizationTest {

    @Test
    fun `test create collection`() = runBlocking {
        val wallet = createMockOrganizationWallet()
        
        val collectionId = wallet.createCollection("My Collection", "Test collection")
        
        assertNotNull(collectionId)
        assertTrue(collectionId.isNotEmpty())
    }

    @Test
    fun `test get collection`() = runBlocking {
        val wallet = createMockOrganizationWallet()
        val collectionId = wallet.createCollection("My Collection")
        
        val collection = wallet.getCollection(collectionId)
        
        assertNotNull(collection)
        assertEquals("My Collection", collection?.name)
        assertEquals(collectionId, collection?.id)
    }

    @Test
    fun `test get collection returns null when not found`() = runBlocking {
        val wallet = createMockOrganizationWallet()
        
        assertNull(wallet.getCollection("nonexistent"))
    }

    @Test
    fun `test list collections`() = runBlocking {
        val wallet = createMockOrganizationWallet()
        wallet.createCollection("Collection 1")
        wallet.createCollection("Collection 2")
        
        val collections = wallet.listCollections()
        
        assertEquals(2, collections.size)
    }

    @Test
    fun `test delete collection`() = runBlocking {
        val wallet = createMockOrganizationWallet()
        val collectionId = wallet.createCollection("My Collection")
        assertNotNull(wallet.getCollection(collectionId))
        
        val deleted = wallet.deleteCollection(collectionId)
        
        assertTrue(deleted)
        assertNull(wallet.getCollection(collectionId))
    }

    @Test
    fun `test add credential to collection`() = runBlocking {
        val wallet = createMockOrganizationWallet()
        val credential = createTestCredential()
        val credentialId = wallet.store(credential)
        val collectionId = wallet.createCollection("My Collection")
        
        val added = wallet.addToCollection(credentialId, collectionId)
        
        assertTrue(added)
    }

    @Test
    fun `test add credential to collection fails when credential not found`() = runBlocking {
        val wallet = createMockOrganizationWallet()
        val collectionId = wallet.createCollection("My Collection")
        
        val added = wallet.addToCollection("nonexistent", collectionId)
        
        assertFalse(added)
    }

    @Test
    fun `test add credential to collection fails when collection not found`() = runBlocking {
        val wallet = createMockOrganizationWallet()
        val credential = createTestCredential()
        val credentialId = wallet.store(credential)
        
        val added = wallet.addToCollection(credentialId, "nonexistent")
        
        assertFalse(added)
    }

    @Test
    fun `test remove credential from collection`() = runBlocking {
        val wallet = createMockOrganizationWallet()
        val credential = createTestCredential()
        val credentialId = wallet.store(credential)
        val collectionId = wallet.createCollection("My Collection")
        wallet.addToCollection(credentialId, collectionId)
        
        val removed = wallet.removeFromCollection(credentialId, collectionId)
        
        assertTrue(removed)
    }

    @Test
    fun `test get credentials in collection`() = runBlocking {
        val wallet = createMockOrganizationWallet()
        val credential1 = createTestCredential(id = "cred-1")
        val credential2 = createTestCredential(id = "cred-2")
        val id1 = wallet.store(credential1)
        val id2 = wallet.store(credential2)
        val collectionId = wallet.createCollection("My Collection")
        wallet.addToCollection(id1, collectionId)
        wallet.addToCollection(id2, collectionId)
        
        val credentials = wallet.getCredentialsInCollection(collectionId)
        
        assertEquals(2, credentials.size)
    }

    @Test
    fun `test tag credential`() = runBlocking {
        val wallet = createMockOrganizationWallet()
        val credential = createTestCredential()
        val credentialId = wallet.store(credential)
        
        val tagged = wallet.tagCredential(credentialId, setOf("important", "verified"))
        
        assertTrue(tagged)
    }

    @Test
    fun `test tag credential fails when credential not found`() = runBlocking {
        val wallet = createMockOrganizationWallet()
        
        val tagged = wallet.tagCredential("nonexistent", setOf("important"))
        
        assertFalse(tagged)
    }

    @Test
    fun `test untag credential`() = runBlocking {
        val wallet = createMockOrganizationWallet()
        val credential = createTestCredential()
        val credentialId = wallet.store(credential)
        wallet.tagCredential(credentialId, setOf("important", "verified"))
        
        val untagged = wallet.untagCredential(credentialId, setOf("important"))
        
        assertTrue(untagged)
    }

    @Test
    fun `test get tags`() = runBlocking {
        val wallet = createMockOrganizationWallet()
        val credential = createTestCredential()
        val credentialId = wallet.store(credential)
        wallet.tagCredential(credentialId, setOf("important", "verified"))
        
        val tags = wallet.getTags(credentialId)
        
        assertEquals(2, tags.size)
        assertTrue(tags.contains("important"))
        assertTrue(tags.contains("verified"))
    }

    @Test
    fun `test get all tags`() = runBlocking {
        val wallet = createMockOrganizationWallet()
        val credential1 = createTestCredential(id = "cred-1")
        val credential2 = createTestCredential(id = "cred-2")
        val id1 = wallet.store(credential1)
        val id2 = wallet.store(credential2)
        wallet.tagCredential(id1, setOf("important", "verified"))
        wallet.tagCredential(id2, setOf("important", "archived"))
        
        val allTags = wallet.getAllTags()
        
        assertTrue(allTags.size >= 3)
        assertTrue(allTags.contains("important"))
        assertTrue(allTags.contains("verified"))
        assertTrue(allTags.contains("archived"))
    }

    @Test
    fun `test find by tag`() = runBlocking {
        val wallet = createMockOrganizationWallet()
        val credential1 = createTestCredential(id = "cred-1")
        val credential2 = createTestCredential(id = "cred-2")
        val id1 = wallet.store(credential1)
        val id2 = wallet.store(credential2)
        wallet.tagCredential(id1, setOf("important"))
        wallet.tagCredential(id2, setOf("important"))
        
        val found = wallet.findByTag("important")
        
        assertEquals(2, found.size)
    }

    @Test
    fun `test add metadata`() = runBlocking {
        val wallet = createMockOrganizationWallet()
        val credential = createTestCredential()
        val credentialId = wallet.store(credential)
        
        val added = wallet.addMetadata(credentialId, mapOf("source" to "issuer.com", "verified" to true))
        
        assertTrue(added)
    }

    @Test
    fun `test get metadata`() = runBlocking {
        val wallet = createMockOrganizationWallet()
        val credential = createTestCredential()
        val credentialId = wallet.store(credential)
        wallet.addMetadata(credentialId, mapOf("source" to "issuer.com"))
        
        val metadata = wallet.getMetadata(credentialId)
        
        assertNotNull(metadata)
    }

    @Test
    fun `test update notes`() = runBlocking {
        val wallet = createMockOrganizationWallet()
        val credential = createTestCredential()
        val credentialId = wallet.store(credential)
        
        val updated = wallet.updateNotes(credentialId, "This is a test note")
        
        assertTrue(updated)
    }

    @Test
    fun `test update notes clears notes`() = runBlocking {
        val wallet = createMockOrganizationWallet()
        val credential = createTestCredential()
        val credentialId = wallet.store(credential)
        wallet.updateNotes(credentialId, "Test note")
        
        val cleared = wallet.updateNotes(credentialId, null)
        
        assertTrue(cleared)
    }

    private fun createMockOrganizationWallet(): MockOrganizationWallet {
        return object : MockOrganizationWallet {
            private val credentials = mutableMapOf<String, VerifiableCredential>()
            private val collections = mutableMapOf<String, CredentialCollection>()
            private val collectionCredentials = mutableMapOf<String, MutableSet<String>>()
            private val credentialTags = mutableMapOf<String, MutableSet<String>>()
            private val credentialMetadata = mutableMapOf<String, CredentialMetadata>()
            
            override val walletId = UUID.randomUUID().toString()
            override val capabilities = WalletCapabilities(
                collections = true,
                tags = true,
                metadata = true
            )
            
            override suspend fun store(credential: VerifiableCredential): String {
                val id = credential.id ?: UUID.randomUUID().toString()
                credentials[id] = credential
                return id
            }
            override suspend fun get(credentialId: String) = credentials[credentialId]
            override suspend fun list(filter: CredentialFilter?) = credentials.values.toList()
            override suspend fun delete(credentialId: String) = credentials.remove(credentialId) != null
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> {
                val builder = CredentialQueryBuilder()
                builder.query()
                val predicate = builder.createPredicate()
                return credentials.values.filter(predicate)
            }
            override suspend fun getStatistics() = WalletStatistics(credentials.size, 0, 0)
            
            override suspend fun createCollection(name: String, description: String?): String {
                val id = UUID.randomUUID().toString()
                collections[id] = CredentialCollection(id, name, description, Instant.now(), 0)
                collectionCredentials[id] = mutableSetOf()
                return id
            }
            
            override suspend fun getCollection(collectionId: String) = collections[collectionId]?.copy(
                credentialCount = collectionCredentials[collectionId]?.size ?: 0
            )
            
            override suspend fun listCollections() = collections.values.map { it.copy(credentialCount = collectionCredentials[it.id]?.size ?: 0) }
            
            override suspend fun deleteCollection(collectionId: String): Boolean {
                collections.remove(collectionId)?.let {
                    collectionCredentials.remove(collectionId)
                    return true
                }
                return false
            }
            
            override suspend fun addToCollection(credentialId: String, collectionId: String): Boolean {
                if (!credentials.containsKey(credentialId) || !collections.containsKey(collectionId)) return false
                collectionCredentials.getOrPut(collectionId) { mutableSetOf() }.add(credentialId)
                return true
            }
            
            override suspend fun removeFromCollection(credentialId: String, collectionId: String): Boolean {
                collectionCredentials[collectionId]?.remove(credentialId)
                return true
            }
            
            override suspend fun getCredentialsInCollection(collectionId: String) = 
                collectionCredentials[collectionId]?.mapNotNull { credentials[it] } ?: emptyList()
            
            override suspend fun tagCredential(credentialId: String, tags: Set<String>): Boolean {
                if (!credentials.containsKey(credentialId)) return false
                credentialTags.getOrPut(credentialId) { mutableSetOf() }.addAll(tags)
                return true
            }
            
            override suspend fun untagCredential(credentialId: String, tags: Set<String>): Boolean {
                credentialTags[credentialId]?.removeAll(tags)
                return true
            }
            
            override suspend fun getTags(credentialId: String) = credentialTags[credentialId]?.toSet() ?: emptySet()
            
            override suspend fun getAllTags() = credentialTags.values.flatten().toSet()
            
            override suspend fun findByTag(tag: String) = credentialTags.filter { tag in it.value }.keys.mapNotNull { credentials[it] }
            
            override suspend fun addMetadata(credentialId: String, metadata: Map<String, Any>): Boolean {
                if (!credentials.containsKey(credentialId)) return false
                val existing = credentialMetadata[credentialId] ?: CredentialMetadata(credentialId = credentialId, createdAt = Instant.now(), updatedAt = Instant.now())
                credentialMetadata[credentialId] = existing.copy(metadata = existing.metadata + metadata)
                return true
            }
            
            override suspend fun getMetadata(credentialId: String) = credentialMetadata[credentialId]
            
            override suspend fun updateNotes(credentialId: String, notes: String?): Boolean {
                if (!credentials.containsKey(credentialId)) return false
                val existing = credentialMetadata[credentialId] ?: CredentialMetadata(credentialId = credentialId, createdAt = Instant.now(), updatedAt = Instant.now())
                credentialMetadata[credentialId] = existing.copy(notes = notes, updatedAt = Instant.now())
                return true
            }
        }
    }
    
    private interface MockOrganizationWallet : Wallet, CredentialOrganization

    private fun createTestCredential(
        id: String? = "https://example.com/credentials/1",
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

