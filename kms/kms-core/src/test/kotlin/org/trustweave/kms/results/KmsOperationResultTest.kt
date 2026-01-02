package org.trustweave.kms.results

import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyHandle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class KmsOperationResultTest {
    private val testKeyId = KeyId("test-key-id")
    private val testKeyHandle = KeyHandle(testKeyId, Algorithm.Ed25519.name, emptyMap())

    // GetPublicKeyResult tests
    @Test
    fun `test GetPublicKeyResult Success`() {
        val result = GetPublicKeyResult.Success(testKeyHandle)
        assertTrue(result.isSuccess)
        assertFalse(result.isFailure)
        assertEquals(testKeyHandle, result.keyHandle)
        assertEquals(testKeyHandle, result.keyHandleOrNull)
    }

    @Test
    fun `test GetPublicKeyResult KeyNotFound`() {
        val result = GetPublicKeyResult.Failure.KeyNotFound(testKeyId)
        assertFalse(result.isSuccess)
        assertTrue(result.isFailure)
        assertEquals(testKeyId, result.keyId)
        assertNull(result.reason)
        assertNull(result.keyHandleOrNull)
    }

    @Test
    fun `test GetPublicKeyResult KeyNotFound with reason`() {
        val result = GetPublicKeyResult.Failure.KeyNotFound(testKeyId, "Key was deleted")
        assertEquals("Key was deleted", result.reason)
    }

    @Test
    fun `test GetPublicKeyResult Error`() {
        val cause = Exception("test error")
        val result = GetPublicKeyResult.Failure.Error(testKeyId, "Operation failed", cause)
        assertTrue(result.isFailure)
        assertEquals(testKeyId, result.keyId)
        assertEquals("Operation failed", result.reason)
        assertEquals(cause, result.cause)
    }

    @Test
    fun `test GetPublicKeyResult onSuccess`() {
        var executed = false
        val result = GetPublicKeyResult.Success(testKeyHandle)
        result.onSuccess { executed = true }
        assertTrue(executed, "onSuccess should execute for Success")
    }

    @Test
    fun `test GetPublicKeyResult onSuccess does not execute for Failure`() {
        var executed = false
        val result = GetPublicKeyResult.Failure.KeyNotFound(testKeyId)
        result.onSuccess { executed = true }
        assertFalse(executed, "onSuccess should not execute for Failure")
    }

    @Test
    fun `test GetPublicKeyResult onFailure`() {
        var executed = false
        val result = GetPublicKeyResult.Failure.KeyNotFound(testKeyId)
        result.onFailure { executed = true }
        assertTrue(executed, "onFailure should execute for Failure")
    }

    @Test
    fun `test GetPublicKeyResult getOrThrow with Success`() {
        val result = GetPublicKeyResult.Success(testKeyHandle)
        val handle = result.getOrThrow()
        assertEquals(testKeyHandle, handle)
    }

    @Test
    fun `test GetPublicKeyResult getOrThrow with KeyNotFound throws`() {
        val result = GetPublicKeyResult.Failure.KeyNotFound(testKeyId)
        assertFailsWith<org.trustweave.kms.exception.KmsException.KeyNotFound> {
            result.getOrThrow()
        }
    }

    @Test
    fun `test GetPublicKeyResult getOrThrow with Error throws`() {
        val result = GetPublicKeyResult.Failure.Error(testKeyId, "Operation failed")
        assertFailsWith<org.trustweave.core.exception.TrustWeaveException.Unknown> {
            result.getOrThrow()
        }
    }

    @Test
    fun `test GetPublicKeyResult fold`() {
        val success = GetPublicKeyResult.Success(testKeyHandle)
        val successValue = success.fold(
            onFailure = { "failed" },
            onSuccess = { "success" }
        )
        assertEquals("success", successValue)

        val failure = GetPublicKeyResult.Failure.KeyNotFound(testKeyId)
        val failureValue = failure.fold(
            onFailure = { "failed" },
            onSuccess = { "success" }
        )
        assertEquals("failed", failureValue)
    }

    // SignResult tests
    @Test
    fun `test SignResult Success`() {
        val signature = byteArrayOf(1, 2, 3, 4)
        val result = SignResult.Success(signature)
        assertTrue(result.isSuccess)
        assertFalse(result.isFailure)
        assertTrue(signature.contentEquals(result.signature))
        assertTrue(signature.contentEquals(result.signatureOrNull!!))
    }

    @Test
    fun `test SignResult Success equality`() {
        val signature = byteArrayOf(1, 2, 3, 4)
        val result1 = SignResult.Success(signature)
        val result2 = SignResult.Success(signature)
        assertEquals(result1, result2)
        assertEquals(result1.hashCode(), result2.hashCode())
    }

    @Test
    fun `test SignResult UnsupportedAlgorithm`() {
        val result = SignResult.Failure.UnsupportedAlgorithm(
            testKeyId,
            Algorithm.Ed25519,
            Algorithm.Secp256k1,
            "Algorithm mismatch"
        )
        assertTrue(result.isFailure)
        assertEquals(testKeyId, result.keyId)
        assertEquals(Algorithm.Ed25519, result.requestedAlgorithm)
        assertEquals(Algorithm.Secp256k1, result.keyAlgorithm)
    }

    @Test
    fun `test SignResult getOrThrow with UnsupportedAlgorithm throws`() {
        val result = SignResult.Failure.UnsupportedAlgorithm(
            testKeyId,
            Algorithm.Ed25519,
            Algorithm.Secp256k1
        )
        assertFailsWith<org.trustweave.kms.UnsupportedAlgorithmException> {
            result.getOrThrow()
        }
    }

    @Test
    fun `test SignResult fold`() {
        val signature = byteArrayOf(1, 2, 3)
        val success = SignResult.Success(signature)
        val successValue = success.fold(
            onFailure = { "failed" },
            onSuccess = { "success" }
        )
        assertEquals("success", successValue)
    }

    // GenerateKeyResult tests
    @Test
    fun `test GenerateKeyResult Success`() {
        val result = GenerateKeyResult.Success(testKeyHandle)
        assertTrue(result.isSuccess)
        assertFalse(result.isFailure)
        assertEquals(testKeyHandle, result.keyHandle)
        assertEquals(testKeyHandle, result.keyHandleOrNull)
    }

    @Test
    fun `test GenerateKeyResult UnsupportedAlgorithm`() {
        val supported = setOf(Algorithm.Secp256k1)
        val result = GenerateKeyResult.Failure.UnsupportedAlgorithm(Algorithm.Ed25519, supported)
        assertTrue(result.isFailure)
        assertEquals(Algorithm.Ed25519, result.algorithm)
        assertNotNull(result.reason)
        assertTrue(result.reason!!.contains("Ed25519"))
    }

    @Test
    fun `test GenerateKeyResult InvalidOptions`() {
        val result = GenerateKeyResult.Failure.InvalidOptions(
            Algorithm.Ed25519,
            "Invalid key size",
            mapOf("keySize" to -1)
        )
        assertTrue(result.isFailure)
        assertEquals("Invalid key size", result.reason)
        assertTrue(result.invalidOptions.containsKey("keySize"))
    }

    @Test
    fun `test GenerateKeyResult getOrThrow with UnsupportedAlgorithm throws`() {
        val result = GenerateKeyResult.Failure.UnsupportedAlgorithm(
            Algorithm.Ed25519,
            setOf(Algorithm.Secp256k1)
        )
        assertFailsWith<org.trustweave.kms.UnsupportedAlgorithmException> {
            result.getOrThrow()
        }
    }

    @Test
    fun `test GenerateKeyResult getOrThrow with InvalidOptions throws`() {
        val result = GenerateKeyResult.Failure.InvalidOptions(
            Algorithm.Ed25519,
            "Invalid options"
        )
        assertFailsWith<IllegalArgumentException> {
            result.getOrThrow()
        }
    }

    // DeleteKeyResult tests
    @Test
    fun `test DeleteKeyResult Deleted`() {
        val result = DeleteKeyResult.Deleted
        assertTrue(result.isSuccess)
        assertTrue(result.wasDeleted)
        assertFalse(result.isFailure)
    }

    @Test
    fun `test DeleteKeyResult NotFound`() {
        val result = DeleteKeyResult.NotFound
        assertTrue(result.isSuccess)
        assertFalse(result.wasDeleted)
        assertFalse(result.isFailure)
    }

    @Test
    fun `test DeleteKeyResult Error`() {
        val result = DeleteKeyResult.Failure.Error(testKeyId, "Deletion failed")
        assertFalse(result.isSuccess)
        assertFalse(result.wasDeleted)
        assertTrue(result.isFailure)
    }

    @Test
    fun `test DeleteKeyResult getOrThrow with Deleted`() {
        val result = DeleteKeyResult.Deleted
        assertTrue(result.getOrThrow())
    }

    @Test
    fun `test DeleteKeyResult getOrThrow with NotFound`() {
        val result = DeleteKeyResult.NotFound
        assertFalse(result.getOrThrow())
    }

    @Test
    fun `test DeleteKeyResult getOrThrow with Error throws`() {
        val result = DeleteKeyResult.Failure.Error(testKeyId, "Deletion failed")
        assertFailsWith<org.trustweave.core.exception.TrustWeaveException.Unknown> {
            result.getOrThrow()
        }
    }

    @Test
    fun `test DeleteKeyResult fold`() {
        val deleted = DeleteKeyResult.Deleted
        val deletedValue = deleted.fold(
            onFailure = { "failed" },
            onSuccess = { if (it) "deleted" else "not found" }
        )
        assertEquals("deleted", deletedValue)

        val notFound = DeleteKeyResult.NotFound
        val notFoundValue = notFound.fold(
            onFailure = { "failed" },
            onSuccess = { if (it) "deleted" else "not found" }
        )
        assertEquals("not found", notFoundValue)
    }
}

