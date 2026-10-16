package org.trustweave.kms.exception

import org.trustweave.core.exception.TrustWeaveException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class KmsFailureExceptionsTest {
    @Test
    fun `key generation failure reports algorithm reason and cause`() {
        val cause = RuntimeException("entropy")
        val error = KmsException.KeyGenerationFailed("Ed25519", "no entropy", cause)

        assertEquals("KEY_GENERATION_FAILED", error.code)
        assertEquals("Key generation failed for algorithm 'Ed25519': no entropy", error.message)
        assertEquals(mapOf<String, Any?>("algorithm" to "Ed25519", "reason" to "no entropy"), error.context)
        assertSame(cause, error.cause)
        assertIs<TrustWeaveException>(error)
    }

    @Test
    fun `signing failure identifies the key but defaults to no cause`() {
        val error = KmsException.SigningFailed("key-1", "hsm busy")

        assertEquals("SIGNING_FAILED", error.code)
        assertEquals("Signing failed for key 'key-1': hsm busy", error.message)
        assertEquals("key-1", error.context["keyId"])
        assertNull(error.cause)
    }

    @Test
    fun `key deletion failure identifies the key and reason`() {
        val cause = IllegalStateException("locked")
        val error = KmsException.KeyDeletionFailed("key-2", "in use", cause)

        assertEquals("KEY_DELETION_FAILED", error.code)
        assertEquals("Key deletion failed for key 'key-2': in use", error.message)
        assertEquals("in use", error.context["reason"])
        assertSame(cause, error.cause)
    }

    @Test
    fun `failures with the same detail are equal and different detail is not`() {
        assertEquals(KmsException.SigningFailed("k", "r"), KmsException.SigningFailed("k", "r"))
        assertEquals(false, KmsException.SigningFailed("k", "r") == KmsException.SigningFailed("k", "other"))
    }
}
