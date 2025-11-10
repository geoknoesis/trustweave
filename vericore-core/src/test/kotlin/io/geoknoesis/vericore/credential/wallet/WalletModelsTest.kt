package io.geoknoesis.vericore.credential.wallet

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*
import java.time.Instant

/**
 * Comprehensive tests for WalletModels (CredentialCollection, CredentialMetadata, KeyInfo, WalletStatistics).
 */
class WalletModelsTest {

    @Test
    fun `test CredentialCollection with all fields`() {
        val collection = CredentialCollection(
            id = "collection-1",
            name = "Education Credentials",
            description = "All education-related credentials",
            createdAt = Instant.parse("2024-01-01T00:00:00Z"),
            credentialCount = 5
        )
        
        assertEquals("collection-1", collection.id)
        assertEquals("Education Credentials", collection.name)
        assertEquals("All education-related credentials", collection.description)
        assertEquals(5, collection.credentialCount)
    }

    @Test
    fun `test CredentialCollection with defaults`() {
        val collection = CredentialCollection(
            id = "collection-1",
            name = "Test Collection"
        )
        
        assertNull(collection.description)
        assertEquals(0, collection.credentialCount)
        assertNotNull(collection.createdAt)
    }

    @Test
    fun `test CredentialMetadata with all fields`() {
        val metadata = CredentialMetadata(
            credentialId = "cred-123",
            notes = "Important credential",
            tags = setOf("education", "verified"),
            metadata = mapOf("source" to "university", "verified" to true),
            createdAt = Instant.parse("2024-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2024-01-02T00:00:00Z")
        )
        
        assertEquals("cred-123", metadata.credentialId)
        assertEquals("Important credential", metadata.notes)
        assertEquals(2, metadata.tags.size)
        assertTrue(metadata.tags.contains("education"))
        assertEquals(2, metadata.metadata.size)
    }

    @Test
    fun `test CredentialMetadata with defaults`() {
        val metadata = CredentialMetadata(credentialId = "cred-123")
        
        assertNull(metadata.notes)
        assertTrue(metadata.tags.isEmpty())
        assertTrue(metadata.metadata.isEmpty())
        assertNotNull(metadata.createdAt)
        assertNotNull(metadata.updatedAt)
    }

    @Test
    fun `test KeyInfo with all fields`() {
        val keyInfo = KeyInfo(
            id = "key-1",
            algorithm = "Ed25519",
            publicKeyJwk = mapOf("kty" to "OKP", "crv" to "Ed25519"),
            publicKeyMultibase = "z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
            createdAt = Instant.parse("2024-01-01T00:00:00Z")
        )
        
        assertEquals("key-1", keyInfo.id)
        assertEquals("Ed25519", keyInfo.algorithm)
        assertNotNull(keyInfo.publicKeyJwk)
        assertNotNull(keyInfo.publicKeyMultibase)
    }

    @Test
    fun `test KeyInfo with defaults`() {
        val keyInfo = KeyInfo(
            id = "key-1",
            algorithm = "Ed25519"
        )
        
        assertNull(keyInfo.publicKeyJwk)
        assertNull(keyInfo.publicKeyMultibase)
        assertNotNull(keyInfo.createdAt)
    }

    @Test
    fun `test WalletStatistics with all fields`() {
        val stats = WalletStatistics(
            totalCredentials = 100,
            validCredentials = 80,
            expiredCredentials = 15,
            revokedCredentials = 5,
            collectionsCount = 3,
            tagsCount = 10,
            archivedCount = 2
        )
        
        assertEquals(100, stats.totalCredentials)
        assertEquals(80, stats.validCredentials)
        assertEquals(15, stats.expiredCredentials)
        assertEquals(5, stats.revokedCredentials)
        assertEquals(3, stats.collectionsCount)
        assertEquals(10, stats.tagsCount)
        assertEquals(2, stats.archivedCount)
    }

    @Test
    fun `test WalletStatistics with defaults`() {
        val stats = WalletStatistics()
        
        assertEquals(0, stats.totalCredentials)
        assertEquals(0, stats.validCredentials)
        assertEquals(0, stats.expiredCredentials)
        assertEquals(0, stats.revokedCredentials)
        assertEquals(0, stats.collectionsCount)
        assertEquals(0, stats.tagsCount)
        assertEquals(0, stats.archivedCount)
    }
}



