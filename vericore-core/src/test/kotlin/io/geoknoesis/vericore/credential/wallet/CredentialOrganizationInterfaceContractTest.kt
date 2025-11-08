package io.geoknoesis.vericore.credential.wallet

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.*

/**
 * Comprehensive interface contract tests for CredentialOrganization.
 * Tests all methods, branches, and edge cases.
 */
class CredentialOrganizationInterfaceContractTest {

    @Test
    fun `test CredentialOrganization createCollection returns collection ID`() = runBlocking {
        val org = createMockOrganization()
        
        val id = org.createCollection("My Collection", "Description")
        
        assertNotNull(id)
        assertTrue(id.isNotBlank())
    }

    @Test
    fun `test CredentialOrganization createCollection with null description`() = runBlocking {
        val org = createMockOrganization()
        
        val id = org.createCollection("My Collection", null)
        
        assertNotNull(id)
    }

    @Test
    fun `test CredentialOrganization getCollection returns created collection`() = runBlocking {
        val org = createMockOrganization()
        val id = org.createCollection("My Collection", "Description")
        
        val collection = org.getCollection(id)
        
        assertNotNull(collection)
        assertEquals("My Collection", collection?.name)
        assertEquals("Description", collection?.description)
    }

    @Test
    fun `test CredentialOrganization getCollection returns null for non-existent`() = runBlocking {
        val org = createMockOrganization()
        
        val collection = org.getCollection("non-existent")
        
        assertNull(collection)
    }

    @Test
    fun `test CredentialOrganization listCollections returns all collections`() = runBlocking {
        val org = createMockOrganization()
        org.createCollection("Collection 1")
        org.createCollection("Collection 2")
        
        val collections = org.listCollections()
        
        assertTrue(collections.size >= 2)
    }

    @Test
    fun `test CredentialOrganization deleteCollection returns true`() = runBlocking {
        val org = createMockOrganization()
        val id = org.createCollection("My Collection")
        
        val deleted = org.deleteCollection(id)
        
        assertTrue(deleted)
        assertNull(org.getCollection(id))
    }

    @Test
    fun `test CredentialOrganization addToCollection returns true`() = runBlocking {
        val org = createMockOrganization()
        val cred = createTestCredential(id = "cred-1")
        val collectionId = org.createCollection("My Collection")
        (org as CredentialStorage).store(cred)
        
        val added = org.addToCollection("cred-1", collectionId)
        
        assertTrue(added)
    }

    @Test
    fun `test CredentialOrganization addToCollection returns false for non-existent credential`() = runBlocking {
        val org = createMockOrganization()
        val collectionId = org.createCollection("My Collection")
        
        val added = org.addToCollection("non-existent", collectionId)
        
        assertFalse(added)
    }

    @Test
    fun `test CredentialOrganization removeFromCollection returns true`() = runBlocking {
        val org = createMockOrganization()
        val cred = createTestCredential(id = "cred-1")
        val collectionId = org.createCollection("My Collection")
        (org as CredentialStorage).store(cred)
        org.addToCollection("cred-1", collectionId)
        
        val removed = org.removeFromCollection("cred-1", collectionId)
        
        assertTrue(removed)
    }

    @Test
    fun `test CredentialOrganization getCredentialsInCollection returns credentials`() = runBlocking {
        val org = createMockOrganization()
        val cred1 = createTestCredential(id = "cred-1")
        val cred2 = createTestCredential(id = "cred-2")
        val collectionId = org.createCollection("My Collection")
        (org as CredentialStorage).store(cred1)
        (org as CredentialStorage).store(cred2)
        org.addToCollection("cred-1", collectionId)
        org.addToCollection("cred-2", collectionId)
        
        val credentials = org.getCredentialsInCollection(collectionId)
        
        assertEquals(2, credentials.size)
    }

    @Test
    fun `test CredentialOrganization tagCredential returns true`() = runBlocking {
        val org = createMockOrganization()
        val cred = createTestCredential(id = "cred-1")
        (org as CredentialStorage).store(cred)
        
        val tagged = org.tagCredential("cred-1", setOf("important", "verified"))
        
        assertTrue(tagged)
        assertEquals(setOf("important", "verified"), org.getTags("cred-1"))
    }

    @Test
    fun `test CredentialOrganization untagCredential returns true`() = runBlocking {
        val org = createMockOrganization()
        val cred = createTestCredential(id = "cred-1")
        (org as CredentialStorage).store(cred)
        org.tagCredential("cred-1", setOf("important", "verified"))
        
        val untagged = org.untagCredential("cred-1", setOf("important"))
        
        assertTrue(untagged)
        assertEquals(setOf("verified"), org.getTags("cred-1"))
    }

    @Test
    fun `test CredentialOrganization getAllTags returns all tags`() = runBlocking {
        val org = createMockOrganization()
        val cred1 = createTestCredential(id = "cred-1")
        val cred2 = createTestCredential(id = "cred-2")
        (org as CredentialStorage).store(cred1)
        (org as CredentialStorage).store(cred2)
        org.tagCredential("cred-1", setOf("important"))
        org.tagCredential("cred-2", setOf("verified"))
        
        val allTags = org.getAllTags()
        
        assertTrue(allTags.contains("important"))
        assertTrue(allTags.contains("verified"))
    }

    @Test
    fun `test CredentialOrganization findByTag returns credentials`() = runBlocking {
        val org = createMockOrganization()
        val cred1 = createTestCredential(id = "cred-1")
        val cred2 = createTestCredential(id = "cred-2")
        (org as CredentialStorage).store(cred1)
        (org as CredentialStorage).store(cred2)
        org.tagCredential("cred-1", setOf("important"))
        org.tagCredential("cred-2", setOf("important"))
        
        val credentials = org.findByTag("important")
        
        assertEquals(2, credentials.size)
    }

    @Test
    fun `test CredentialOrganization addMetadata returns true`() = runBlocking {
        val org = createMockOrganization()
        val cred = createTestCredential(id = "cred-1")
        (org as CredentialStorage).store(cred)
        
        val added = org.addMetadata("cred-1", mapOf("source" to "issuer.com", "verified" to true))
        
        assertTrue(added)
        assertNotNull(org.getMetadata("cred-1"))
    }

    @Test
    fun `test CredentialOrganization updateNotes returns true`() = runBlocking {
        val org = createMockOrganization()
        val cred = createTestCredential(id = "cred-1")
        (org as CredentialStorage).store(cred)
        
        val updated = org.updateNotes("cred-1", "Important credential")
        
        assertTrue(updated)
        assertEquals("Important credential", org.getMetadata("cred-1")?.notes)
    }

    @Test
    fun `test CredentialOrganization updateNotes with null clears notes`() = runBlocking {
        val org = createMockOrganization()
        val cred = createTestCredential(id = "cred-1")
        (org as CredentialStorage).store(cred)
        org.updateNotes("cred-1", "Important credential")
        
        val cleared = org.updateNotes("cred-1", null)
        
        assertTrue(cleared)
        assertNull(org.getMetadata("cred-1")?.notes)
    }

    private fun createMockOrganization(): CredentialOrganization {
        return object : CredentialStorage, CredentialOrganization {
            private val storage = mutableMapOf<String, VerifiableCredential>()
            private val collections = mutableMapOf<String, CredentialCollection>()
            private val collectionCredentials = mutableMapOf<String, MutableSet<String>>()
            private val credentialTags = mutableMapOf<String, MutableSet<String>>()
            private val credentialMetadata = mutableMapOf<String, CredentialMetadata>()
            
            override suspend fun store(credential: VerifiableCredential): String {
                val id = credential.id ?: java.util.UUID.randomUUID().toString()
                storage[id] = credential.copy(id = id)
                return id
            }
            
            override suspend fun get(credentialId: String): VerifiableCredential? = storage[credentialId]
            
            override suspend fun list(filter: CredentialFilter?): List<VerifiableCredential> {
                return storage.values.toList()
            }
            
            override suspend fun delete(credentialId: String): Boolean = storage.remove(credentialId) != null
            
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> {
                val builder = CredentialQueryBuilder()
                builder.query()
                return storage.values.filter(builder.toPredicate())
            }
            
            override suspend fun createCollection(name: String, description: String?): String {
                val id = "collection-${java.util.UUID.randomUUID()}"
                collections[id] = CredentialCollection(
                    id = id,
                    name = name,
                    description = description,
                    createdAt = Instant.now()
                )
                collectionCredentials[id] = mutableSetOf()
                return id
            }
            
            override suspend fun getCollection(collectionId: String): CredentialCollection? = collections[collectionId]
            
            override suspend fun listCollections(): List<CredentialCollection> = collections.values.toList()
            
            override suspend fun deleteCollection(collectionId: String): Boolean {
                collections.remove(collectionId)
                collectionCredentials.remove(collectionId)
                return true
            }
            
            override suspend fun addToCollection(credentialId: String, collectionId: String): Boolean {
                if (!storage.containsKey(credentialId) || !collections.containsKey(collectionId)) return false
                collectionCredentials.getOrPut(collectionId) { mutableSetOf() }.add(credentialId)
                return true
            }
            
            override suspend fun removeFromCollection(credentialId: String, collectionId: String): Boolean {
                return collectionCredentials[collectionId]?.remove(credentialId) ?: false
            }
            
            override suspend fun getCredentialsInCollection(collectionId: String): List<VerifiableCredential> {
                return collectionCredentials[collectionId]?.mapNotNull { storage[it] } ?: emptyList()
            }
            
            override suspend fun tagCredential(credentialId: String, tags: Set<String>): Boolean {
                if (!storage.containsKey(credentialId)) return false
                credentialTags.getOrPut(credentialId) { mutableSetOf() }.addAll(tags)
                return true
            }
            
            override suspend fun untagCredential(credentialId: String, tags: Set<String>): Boolean {
                return credentialTags[credentialId]?.removeAll(tags) ?: false
            }
            
            override suspend fun getTags(credentialId: String): Set<String> = credentialTags[credentialId] ?: emptySet()
            
            override suspend fun getAllTags(): Set<String> = credentialTags.values.flatten().toSet()
            
            override suspend fun findByTag(tag: String): List<VerifiableCredential> {
                return credentialTags.filter { tag in it.value }.keys.mapNotNull { storage[it] }
            }
            
            override suspend fun addMetadata(credentialId: String, metadata: Map<String, Any>): Boolean {
                if (!storage.containsKey(credentialId)) return false
                val existing = credentialMetadata[credentialId]
                credentialMetadata[credentialId] = CredentialMetadata(
                    credentialId = credentialId,
                    metadata = (existing?.metadata ?: emptyMap()) + metadata,
                    notes = existing?.notes,
                    createdAt = existing?.createdAt ?: Instant.now(),
                    updatedAt = Instant.now()
                )
                return true
            }
            
            override suspend fun getMetadata(credentialId: String): CredentialMetadata? = credentialMetadata[credentialId]
            
            override suspend fun updateNotes(credentialId: String, notes: String?): Boolean {
                if (!storage.containsKey(credentialId)) return false
                val existing = credentialMetadata[credentialId]
                credentialMetadata[credentialId] = CredentialMetadata(
                    credentialId = credentialId,
                    metadata = existing?.metadata ?: emptyMap(),
                    notes = notes,
                    createdAt = existing?.createdAt ?: Instant.now(),
                    updatedAt = Instant.now()
                )
                return true
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

