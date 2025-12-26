package org.trustweave.did.exception

import org.junit.jupiter.api.Test
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.identifiers.Did
import kotlin.test.*

/**
 * Tests for DID exception types and conversion utilities.
 */
class DidExceptionTest {

    @Test
    fun `test DidNotFound exception`() {
        val did = Did("did:test:123")
        val exception = DidException.DidNotFound(
            did = did,
            availableMethods = listOf("key", "web")
        )

        assertEquals("DID_NOT_FOUND", exception.code)
        assertEquals("DID not found: ${did.value}", exception.message)
        assertEquals(did.value, exception.context["did"])
        assertEquals(listOf("key", "web"), exception.context["availableMethods"])
        assertEquals(did, exception.did)
        assertEquals(2, exception.availableMethods.size)
    }

    @Test
    fun `test DidMethodNotRegistered exception`() {
        val exception = DidException.DidMethodNotRegistered(
            method = "unknown",
            availableMethods = listOf("key", "web")
        )

        assertEquals("DID_METHOD_NOT_REGISTERED", exception.code)
        assertTrue(exception.message.contains("unknown"))
        assertTrue(exception.message.contains("key"))
        assertEquals("unknown", exception.method)
        assertEquals(2, exception.availableMethods.size)
    }

    @Test
    fun `test InvalidDidFormat exception`() {
        val exception = DidException.InvalidDidFormat(
            did = "invalid-did",
            reason = "Missing did: prefix"
        )

        assertEquals("INVALID_DID_FORMAT", exception.code)
        assertTrue(exception.message.contains("invalid-did"))
        assertTrue(exception.message.contains("Missing did: prefix"))
        assertEquals("invalid-did", exception.did)
        assertEquals("Missing did: prefix", exception.reason)
    }

    @Test
    fun `test DidResolutionFailed exception`() {
        val did = Did("did:test:123")
        val cause = RuntimeException("Network error")
        val exception = DidException.DidResolutionFailed(
            did = did,
            reason = "Connection timeout",
            cause = cause
        )

        assertEquals("DID_RESOLUTION_FAILED", exception.code)
        assertTrue(exception.message.contains(did.value))
        assertTrue(exception.message.contains("Connection timeout"))
        assertEquals(did, exception.did)
        assertEquals("Connection timeout", exception.reason)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `test DidCreationFailed exception with DID`() {
        val did = Did("did:test:123")
        val exception = DidException.DidCreationFailed(
            did = did,
            reason = "Registrar unavailable"
        )

        assertEquals("DID_CREATION_FAILED", exception.code)
        assertTrue(exception.message.contains("Registrar unavailable"))
        assertEquals(did, exception.did)
        assertEquals("Registrar unavailable", exception.reason)
        assertEquals(did.value, exception.context["did"])
    }

    @Test
    fun `test DidCreationFailed exception without DID`() {
        val exception = DidException.DidCreationFailed(
            did = null,
            reason = "Method not supported"
        )

        assertEquals("DID_CREATION_FAILED", exception.code)
        assertTrue(exception.message.contains("Method not supported"))
        assertNull(exception.did)
        assertEquals("unknown", exception.context["did"])
    }

    @Test
    fun `test DidCreationFailed exception with cause`() {
        val did = Did("did:test:123")
        val cause = IllegalStateException("Internal error")
        val exception = DidException.DidCreationFailed(
            did = did,
            reason = "Creation failed",
            cause = cause
        )

        assertEquals(cause, exception.cause)
    }

    @Test
    fun `test DidUpdateFailed exception`() {
        val did = Did("did:test:123")
        val exception = DidException.DidUpdateFailed(
            did = did,
            reason = "Update key not found"
        )

        assertEquals("DID_UPDATE_FAILED", exception.code)
        assertTrue(exception.message.contains(did.value))
        assertTrue(exception.message.contains("Update key not found"))
        assertEquals(did, exception.did)
        assertEquals("Update key not found", exception.reason)
    }

    @Test
    fun `test DidDeactivationFailed exception`() {
        val did = Did("did:test:123")
        val exception = DidException.DidDeactivationFailed(
            did = did,
            reason = "Deactivation key invalid"
        )

        assertEquals("DID_DEACTIVATION_FAILED", exception.code)
        assertTrue(exception.message.contains(did.value))
        assertTrue(exception.message.contains("Deactivation key invalid"))
        assertEquals(did, exception.did)
        assertEquals("Deactivation key invalid", exception.reason)
    }

    @Test
    fun `test toDidException with DidException`() {
        val original = DidException.DidNotFound(
            did = Did("did:test:123"),
            availableMethods = emptyList()
        )

        val converted = original.toDidException()
        assertSame(original, converted)
    }

    @Test
    fun `test toDidException with TrustWeaveException NotFound`() {
        val original = TrustWeaveException.NotFound(
            resource = "did:test:123"
        )

        val converted = original.toDidException()
        assertTrue(converted is DidException.DidNotFound)
        assertEquals("did:test:123", converted.did.value)
    }

    @Test
    fun `test toDidException with TrustWeaveException NotFound and invalid DID`() {
        val original = TrustWeaveException.NotFound(
            resource = "not-a-valid-did"
        )

        val converted = original.toDidException()
        assertTrue(converted is DidException.InvalidDidFormat)
        assertEquals("not-a-valid-did", converted.did)
    }

    @Test
    fun `test toDidException with TrustWeaveException NotFound and null resource`() {
        val original = TrustWeaveException.NotFound(
            resource = null
        )

        val converted = original.toDidException()
        assertTrue(converted is DidException.InvalidDidFormat)
        assertEquals("unknown", converted.did)
    }

    @Test
    fun `test toDidException with generic Exception`() {
        val original = RuntimeException("Something went wrong")

        val converted = original.toDidException()
        assertTrue(converted is DidException.InvalidDidFormat)
        assertEquals("unknown", converted.did)
        assertTrue(converted.reason.contains("Something went wrong") || 
                  converted.reason.contains("RuntimeException"))
    }

    @Test
    fun `test toDidException with Exception without message`() {
        val original = object : Exception() {}

        val converted = original.toDidException()
        assertTrue(converted is DidException.InvalidDidFormat)
        assertEquals("unknown", converted.did)
        assertTrue(converted.reason.contains("Unknown error"))
    }

    @Test
    fun `test exception context contains all fields`() {
        val did = Did("did:test:123")
        val exception = DidException.DidNotFound(
            did = did,
            availableMethods = listOf("key", "web", "ion")
        )

        assertEquals(2, exception.context.size)
        assertTrue(exception.context.containsKey("did"))
        assertTrue(exception.context.containsKey("availableMethods"))
    }

    @Test
    fun `test exception inheritance from TrustWeaveException`() {
        val exception = DidException.DidNotFound(
            did = Did("did:test:123")
        )

        assertTrue(exception is TrustWeaveException)
        assertTrue(exception is Exception)
    }
}

