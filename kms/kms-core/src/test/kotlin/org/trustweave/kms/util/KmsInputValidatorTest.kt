package org.trustweave.kms.util

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class KmsInputValidatorTest {
    @Test
    fun `test validateKeyId with valid key ID`() {
        val error = KmsInputValidator.validateKeyId("valid-key-id-123")
        assertNull(error, "Valid key ID should return null error")
    }

    @Test
    fun `test validateKeyId with blank key ID`() {
        val error = KmsInputValidator.validateKeyId("")
        assertNotNull(error, "Blank key ID should return error")
        assertTrue(error!!.contains("blank"), "Error should mention blank")
    }

    @Test
    fun `test validateKeyId with whitespace only`() {
        val error = KmsInputValidator.validateKeyId("   ")
        assertNotNull(error, "Whitespace-only key ID should return error")
    }

    @Test
    fun `test validateKeyId with too long key ID`() {
        val longKeyId = "a".repeat(257) // Exceeds MAX_KEY_ID_LENGTH (256)
        val error = KmsInputValidator.validateKeyId(longKeyId)
        assertNotNull(error, "Too long key ID should return error")
        assertTrue(error!!.contains("256"), "Error should mention max length")
    }

    @Test
    fun `test validateKeyId with maximum length key ID`() {
        val maxKeyId = "a".repeat(256) // Exactly MAX_KEY_ID_LENGTH
        val error = KmsInputValidator.validateKeyId(maxKeyId)
        assertNull(error, "Maximum length key ID should be valid")
    }

    @Test
    fun `test validateKeyId with invalid characters`() {
        val invalidKeyId = "key@id#123"
        val error = KmsInputValidator.validateKeyId(invalidKeyId)
        assertNotNull(error, "Invalid characters should return error")
        assertTrue(error!!.contains("invalid characters"), "Error should mention invalid characters")
    }

    @Test
    fun `test validateKeyId with valid special characters`() {
        val validKeyIds = listOf(
            "key-id_123",
            "key/id:123",
            "key-id_123/456:test"
        )
        validKeyIds.forEach { keyId ->
            val error = KmsInputValidator.validateKeyId(keyId)
            assertNull(error, "Key ID '$keyId' should be valid")
        }
    }

    @Test
    fun `test validateSignData with valid data`() {
        val data = "test data".toByteArray()
        val error = KmsInputValidator.validateSignData(data)
        assertNull(error, "Valid data should return null error")
    }

    @Test
    fun `test validateSignData with empty data`() {
        val error = KmsInputValidator.validateSignData(ByteArray(0))
        assertNotNull(error, "Empty data should return error")
        assertTrue(error!!.contains("empty"), "Error should mention empty")
    }

    @Test
    fun `test validateSignData with too large data`() {
        val largeData = ByteArray(10 * 1024 * 1024 + 1) // Exceeds MAX_SIGN_DATA_SIZE (10 MB)
        val error = KmsInputValidator.validateSignData(largeData)
        assertNotNull(error, "Too large data should return error")
        assertTrue(error!!.contains("exceeds maximum"), "Error should mention exceeds maximum")
    }

    @Test
    fun `test validateSignData with maximum size data`() {
        val maxData = ByteArray(10 * 1024 * 1024) // Exactly MAX_SIGN_DATA_SIZE
        val error = KmsInputValidator.validateSignData(maxData)
        assertNull(error, "Maximum size data should be valid")
    }

    @Test
    fun `test validateKeyIdWithResult with valid key ID`() {
        val (isValid, error) = KmsInputValidator.validateKeyIdWithResult("valid-key-id")
        assertTrue(isValid, "Valid key ID should return true")
        assertNull(error, "Valid key ID should return null error")
    }

    @Test
    fun `test validateKeyIdWithResult with invalid key ID`() {
        val (isValid, error) = KmsInputValidator.validateKeyIdWithResult("")
        assertFalse(isValid, "Invalid key ID should return false")
        assertNotNull(error, "Invalid key ID should return error message")
    }

    @Test
    fun `test validateSignDataWithResult with valid data`() {
        val data = "test data".toByteArray()
        val (isValid, error) = KmsInputValidator.validateSignDataWithResult(data)
        assertTrue(isValid, "Valid data should return true")
        assertNull(error, "Valid data should return null error")
    }

    @Test
    fun `test validateSignDataWithResult with invalid data`() {
        val (isValid, error) = KmsInputValidator.validateSignDataWithResult(ByteArray(0))
        assertFalse(isValid, "Invalid data should return false")
        assertNotNull(error, "Invalid data should return error message")
    }
}

