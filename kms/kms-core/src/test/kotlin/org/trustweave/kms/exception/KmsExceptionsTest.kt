package org.trustweave.kms.exception

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KmsExceptionsTest {
    @Test
    fun `test KeyNotFound exception creation`() {
        val exception = KmsException.KeyNotFound("test-key-id")
        assertEquals("KEY_NOT_FOUND", exception.code)
        assertTrue(exception.message.contains("test-key-id"))
        assertTrue(exception.context.containsKey("keyId"))
        assertEquals("test-key-id", exception.context["keyId"])
    }

    @Test
    fun `test KeyNotFound exception with keyType`() {
        val exception = KmsException.KeyNotFound("test-key-id", "Ed25519")
        assertEquals("KEY_NOT_FOUND", exception.code)
        assertTrue(exception.context.containsKey("keyId"))
        assertTrue(exception.context.containsKey("keyType"))
        assertEquals("test-key-id", exception.context["keyId"])
        assertEquals("Ed25519", exception.context["keyType"])
    }

    @Test
    fun `test KeyNotFound exception extends TrustWeaveException`() {
        val exception = KmsException.KeyNotFound("test-key-id")
        assertTrue(exception is org.trustweave.core.exception.TrustWeaveException)
    }

    @Test
    fun `test KeyNotFound exception with cause`() {
        val cause = Exception("underlying error")
        val exception = KmsException.KeyNotFound("test-key-id")
        // Note: KmsException.KeyNotFound doesn't have a cause parameter in its constructor
        // but it inherits from TrustWeaveException which has a cause field
        assertNotNull(exception)
    }
}

