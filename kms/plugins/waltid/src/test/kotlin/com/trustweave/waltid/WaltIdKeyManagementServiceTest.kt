package com.trustweave.waltid

import com.trustweave.kms.exception.KmsException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WaltIdKeyManagementServiceTest {

    @Test
    fun `generateKey should create Ed25519 key`() = runBlocking {
        val kms = WaltIdKeyManagementService()

        val result = kms.generateKey(com.trustweave.kms.Algorithm.Ed25519)
        assertTrue(result is com.trustweave.kms.results.GenerateKeyResult.Success)
        val handle = (result as com.trustweave.kms.results.GenerateKeyResult.Success).keyHandle

        assertNotNull(handle.id)
        assertEquals("Ed25519", handle.algorithm)
        assertNotNull(handle.publicKeyJwk)
    }

    @Test
    fun `getPublicKey should retrieve key handle`() = runBlocking {
        val kms = WaltIdKeyManagementService()
        val generateResult = kms.generateKey(com.trustweave.kms.Algorithm.Ed25519)
        assertTrue(generateResult is com.trustweave.kms.results.GenerateKeyResult.Success)
        val handle = (generateResult as com.trustweave.kms.results.GenerateKeyResult.Success).keyHandle

        val getResult = kms.getPublicKey(handle.id)
        assertTrue(getResult is com.trustweave.kms.results.GetPublicKeyResult.Success)
        val retrieved = (getResult as com.trustweave.kms.results.GetPublicKeyResult.Success).keyHandle

        assertEquals(handle.id, retrieved.id)
        assertEquals(handle.algorithm, retrieved.algorithm)
    }

    @Test
    fun `getPublicKey should return KeyNotFound result for non-existent key`() = runBlocking {
        val kms = WaltIdKeyManagementService()

        val result = kms.getPublicKey(com.trustweave.core.identifiers.KeyId("nonexistent"))
        assertTrue(result is com.trustweave.kms.results.GetPublicKeyResult.Failure.KeyNotFound)
        val failure = result as com.trustweave.kms.results.GetPublicKeyResult.Failure.KeyNotFound
        assertEquals(com.trustweave.core.identifiers.KeyId("nonexistent"), failure.keyId)
    }

    @Test
    fun `sign should produce signature`() = runBlocking {
        val kms = WaltIdKeyManagementService()
        val generateResult = kms.generateKey(com.trustweave.kms.Algorithm.Ed25519)
        assertTrue(generateResult is com.trustweave.kms.results.GenerateKeyResult.Success)
        val handle = (generateResult as com.trustweave.kms.results.GenerateKeyResult.Success).keyHandle
        val data = "test data".toByteArray()

        val signResult = kms.sign(handle.id, data)
        assertTrue(signResult is com.trustweave.kms.results.SignResult.Success)
        val signature = (signResult as com.trustweave.kms.results.SignResult.Success).signature

        assertNotNull(signature)
        assertTrue(signature.isNotEmpty())
    }

    @Test
    fun `deleteKey should remove key`() = runBlocking {
        val kms = WaltIdKeyManagementService()
        val generateResult = kms.generateKey(com.trustweave.kms.Algorithm.Ed25519)
        assertTrue(generateResult is com.trustweave.kms.results.GenerateKeyResult.Success)
        val handle = (generateResult as com.trustweave.kms.results.GenerateKeyResult.Success).keyHandle

        val deleteResult = kms.deleteKey(handle.id)

        assertTrue(deleteResult is com.trustweave.kms.results.DeleteKeyResult.Deleted)
        val getResult = kms.getPublicKey(handle.id)
        assertTrue(getResult is com.trustweave.kms.results.GetPublicKeyResult.Failure.KeyNotFound)
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

