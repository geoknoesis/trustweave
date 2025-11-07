package io.geoknoesis.vericore.testkit.kms

import io.geoknoesis.vericore.kms.KeyNotFoundException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InMemoryKeyManagementServiceTest {

    @Test
    fun `generateKey should create Ed25519 key`() = runBlocking {
        val kms = InMemoryKeyManagementService()
        
        val handle = kms.generateKey("Ed25519")

        assertNotNull(handle.id)
        assertEquals("Ed25519", handle.algorithm)
        assertNotNull(handle.publicKeyJwk)
        assertEquals("OKP", handle.publicKeyJwk?.get("kty"))
        assertEquals("Ed25519", handle.publicKeyJwk?.get("crv"))
    }

    @Test
    fun `generateKey should create secp256k1 key`() = runBlocking {
        val kms = InMemoryKeyManagementService()
        
        val handle = kms.generateKey("secp256k1")

        assertNotNull(handle.id)
        assertEquals("secp256k1", handle.algorithm)
        assertNotNull(handle.publicKeyJwk)
    }

    @Test
    fun `getPublicKey should retrieve key handle`() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val handle = kms.generateKey("Ed25519")

        val retrieved = kms.getPublicKey(handle.id)

        assertEquals(handle.id, retrieved.id)
        assertEquals(handle.algorithm, retrieved.algorithm)
    }

    @Test
    fun `getPublicKey should throw KeyNotFoundException for non-existent key`() = runBlocking {
        val kms = InMemoryKeyManagementService()

        assertThrows<KeyNotFoundException> {
            kms.getPublicKey("nonexistent")
        }
    }

    @Test
    fun `sign should produce signature`() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val handle = kms.generateKey("Ed25519")
        val data = "test data".toByteArray()

        val signature = kms.sign(handle.id, data)

        assertNotNull(signature)
        assertTrue(signature.isNotEmpty())
    }

    @Test
    fun `deleteKey should remove key`() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val handle = kms.generateKey("Ed25519")

        val deleted = kms.deleteKey(handle.id)

        assertTrue(deleted)
        assertThrows<KeyNotFoundException> {
            kms.getPublicKey(handle.id)
        }
    }

    @Test
    fun `deleteKey should return false for non-existent key`() = runBlocking {
        val kms = InMemoryKeyManagementService()

        val deleted = kms.deleteKey("nonexistent")

        assertEquals(false, deleted)
    }
}

