package com.trustweave.waltid

import com.trustweave.kms.KeyNotFoundException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WaltIdKeyManagementServiceTest {

    @Test
    fun `generateKey should create Ed25519 key`() = runBlocking {
        val kms = WaltIdKeyManagementService()
        
        val handle = kms.generateKey("Ed25519")

        assertNotNull(handle.id)
        assertEquals("Ed25519", handle.algorithm)
        assertNotNull(handle.publicKeyJwk)
    }

    @Test
    fun `getPublicKey should retrieve key handle`() = runBlocking {
        val kms = WaltIdKeyManagementService()
        val handle = kms.generateKey("Ed25519")

        val retrieved = kms.getPublicKey(handle.id)

        assertEquals(handle.id, retrieved.id)
        assertEquals(handle.algorithm, retrieved.algorithm)
    }

    @Test
    fun `getPublicKey should throw KeyNotFoundException for non-existent key`() = runBlocking {
        val kms = WaltIdKeyManagementService()

        try {
            kms.getPublicKey("nonexistent")
            assert(false) { "Should have thrown KeyNotFoundException" }
        } catch (e: KeyNotFoundException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun `sign should produce signature`() = runBlocking {
        val kms = WaltIdKeyManagementService()
        val handle = kms.generateKey("Ed25519")
        val data = "test data".toByteArray()

        val signature = kms.sign(handle.id, data)

        assertNotNull(signature)
        assertTrue(signature.isNotEmpty())
    }

    @Test
    fun `deleteKey should remove key`() = runBlocking {
        val kms = WaltIdKeyManagementService()
        val handle = kms.generateKey("Ed25519")

        val deleted = kms.deleteKey(handle.id)

        assertTrue(deleted)
        try {
            kms.getPublicKey(handle.id)
            assert(false) { "Should have thrown KeyNotFoundException" }
        } catch (e: KeyNotFoundException) {
            // Expected
        }
    }
}

class WaltIdKeyManagementServiceProviderTest {

    @Test
    fun `provider should create service`() {
        val provider = WaltIdKeyManagementServiceProvider()
        assertEquals("waltid", provider.name)
        
        val kms = provider.create()
        assertNotNull(kms)
    }
}

