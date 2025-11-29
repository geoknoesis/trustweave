package com.trustweave.credential.exchange.exception

import com.trustweave.core.exception.TrustWeaveException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for ExchangeException and its subtypes.
 */
class ExchangeExceptionTest {

    // ============================================================================
    // Registry-level exception tests
    // ============================================================================

    @Test
    fun `test ProtocolNotRegistered exception creation`() {
        val exception = ExchangeException.ProtocolNotRegistered(
            protocolName = "didcomm",
            availableProtocols = listOf("oidc4vci", "chapi")
        )

        assertEquals("PROTOCOL_NOT_REGISTERED", exception.code)
        assertEquals("didcomm", exception.protocolName)
        assertEquals(listOf("oidc4vci", "chapi"), exception.availableProtocols)
        assertTrue(exception.message.contains("didcomm"))
        assertTrue(exception.message.contains("oidc4vci"))
        assertTrue(exception.context.containsKey("protocolName"))
        assertTrue(exception.context.containsKey("availableProtocols"))
    }

    @Test
    fun `test ProtocolNotRegistered with empty available protocols`() {
        val exception = ExchangeException.ProtocolNotRegistered(
            protocolName = "didcomm",
            availableProtocols = emptyList()
        )

        assertEquals("PROTOCOL_NOT_REGISTERED", exception.code)
        assertTrue(exception.message.contains("No protocols available"))
    }

    @Test
    fun `test OperationNotSupported exception creation`() {
        val exception = ExchangeException.OperationNotSupported(
            protocolName = "oidc4vci",
            operation = "REQUEST_PROOF",
            supportedOperations = listOf("OFFER_CREDENTIAL", "REQUEST_CREDENTIAL", "ISSUE_CREDENTIAL")
        )

        assertEquals("OPERATION_NOT_SUPPORTED", exception.code)
        assertEquals("oidc4vci", exception.protocolName)
        assertEquals("REQUEST_PROOF", exception.operation)
        assertEquals(3, exception.supportedOperations.size)
        assertTrue(exception.message.contains("REQUEST_PROOF"))
        assertTrue(exception.message.contains("OFFER_CREDENTIAL"))
    }

    // ============================================================================
    // Request validation exception tests
    // ============================================================================

    @Test
    fun `test MissingRequiredOption exception creation`() {
        val exception = ExchangeException.MissingRequiredOption(
            optionName = "fromKeyId",
            protocolName = "didcomm"
        )

        assertEquals("MISSING_REQUIRED_OPTION", exception.code)
        assertEquals("fromKeyId", exception.optionName)
        assertEquals("didcomm", exception.protocolName)
        assertTrue(exception.message.contains("fromKeyId"))
        assertTrue(exception.message.contains("didcomm"))
    }

    @Test
    fun `test MissingRequiredOption without protocol name`() {
        val exception = ExchangeException.MissingRequiredOption(
            optionName = "fromKeyId"
        )

        assertEquals("MISSING_REQUIRED_OPTION", exception.code)
        assertEquals("fromKeyId", exception.optionName)
        assertEquals(null, exception.protocolName)
        assertTrue(exception.message.contains("fromKeyId"))
        assertTrue(!exception.context.containsKey("protocolName"))
    }

    @Test
    fun `test InvalidRequest exception creation`() {
        val exception = ExchangeException.InvalidRequest(
            field = "issuerDid",
            reason = "Invalid DID format",
            protocolName = "didcomm"
        )

        assertEquals("INVALID_REQUEST", exception.code)
        assertEquals("issuerDid", exception.field)
        assertEquals("Invalid DID format", exception.reason)
        assertEquals("didcomm", exception.protocolName)
        assertTrue(exception.message.contains("issuerDid"))
        assertTrue(exception.message.contains("Invalid DID format"))
    }

    // ============================================================================
    // Message not found exception tests
    // ============================================================================

    @Test
    fun `test OfferNotFound exception creation`() {
        val exception = ExchangeException.OfferNotFound(
            offerId = "offer-123"
        )

        assertEquals("OFFER_NOT_FOUND", exception.code)
        assertEquals("offer-123", exception.offerId)
        assertTrue(exception.message.contains("offer-123"))
        assertEquals("offer-123", exception.context["offerId"])
    }

    @Test
    fun `test RequestNotFound exception creation`() {
        val exception = ExchangeException.RequestNotFound(
            requestId = "request-456"
        )

        assertEquals("REQUEST_NOT_FOUND", exception.code)
        assertEquals("request-456", exception.requestId)
        assertTrue(exception.message.contains("request-456"))
    }

    @Test
    fun `test ProofRequestNotFound exception creation`() {
        val exception = ExchangeException.ProofRequestNotFound(
            requestId = "proof-request-789"
        )

        assertEquals("PROOF_REQUEST_NOT_FOUND", exception.code)
        assertEquals("proof-request-789", exception.requestId)
        assertTrue(exception.message.contains("proof-request-789"))
    }

    @Test
    fun `test MessageNotFound exception creation`() {
        val exception = ExchangeException.MessageNotFound(
            messageId = "msg-123",
            messageType = "offer"
        )

        assertEquals("MESSAGE_NOT_FOUND", exception.code)
        assertEquals("msg-123", exception.messageId)
        assertEquals("offer", exception.messageType)
        assertTrue(exception.message.contains("offer"))
        assertTrue(exception.message.contains("msg-123"))
    }

    // ============================================================================
    // Plugin-specific exception tests (moved to plugin modules)
    // ============================================================================
    // Note: Plugin-specific exceptions (DIDComm, OIDC4VCI, CHAPI) are now in their
    // respective plugin modules. These tests should be moved to plugin test modules.

    // ============================================================================
    // Unknown exception tests
    // ============================================================================

    @Test
    fun `test Unknown exception creation`() {
        val cause = Exception("Original error")
        val exception = ExchangeException.Unknown(
            reason = "Unexpected error occurred",
            errorType = "CustomException",
            cause = cause
        )

        assertEquals("EXCHANGE_UNKNOWN_ERROR", exception.code)
        assertEquals("Unexpected error occurred", exception.reason)
        assertEquals("CustomException", exception.errorType)
        assertEquals(cause, exception.cause)
        assertTrue(exception.message.contains("CustomException"))
        assertTrue(exception.message.contains("Unexpected error occurred"))
    }

    @Test
    fun `test Unknown exception without error type`() {
        val exception = ExchangeException.Unknown(
            reason = "Unexpected error",
            errorType = null,
            cause = null
        )

        assertEquals("EXCHANGE_UNKNOWN_ERROR", exception.code)
        assertEquals("Unexpected error", exception.reason)
        assertEquals(null, exception.errorType)
        assertTrue(!exception.message.contains("("))
    }

    // ============================================================================
    // Extension function tests
    // ============================================================================

    @Test
    fun `test toExchangeException with ExchangeException`() {
        val original = ExchangeException.ProtocolNotRegistered(
            protocolName = "didcomm",
            availableProtocols = emptyList()
        )

        val converted = original.toExchangeException()

        assertEquals(original, converted)
        assertTrue(converted is ExchangeException.ProtocolNotRegistered)
    }

    @Test
    fun `test toExchangeException with IllegalArgumentException`() {
        val original = IllegalArgumentException("Invalid argument")
        val converted = original.toExchangeException()

        assertTrue(converted is ExchangeException.InvalidRequest)
        assertEquals("argument", (converted as ExchangeException.InvalidRequest).field)
        assertEquals("Invalid argument", converted.reason)
        assertEquals(original, converted.cause)
    }

    @Test
    fun `test toExchangeException with IllegalStateException`() {
        val original = IllegalStateException("Invalid state")
        val converted = original.toExchangeException()

        assertTrue(converted is ExchangeException.InvalidRequest)
        assertEquals("state", (converted as ExchangeException.InvalidRequest).field)
        assertEquals("Invalid state", converted.reason)
    }

    @Test
    fun `test toExchangeException with TimeoutException`() {
        val original = TimeoutException("Request timed out")
        val converted = original.toExchangeException()

        assertTrue(converted is ExchangeException.Unknown)
        assertTrue(converted.reason.contains("timeout"))
    }

    @Test
    fun `test toExchangeException with UnknownHostException`() {
        val original = UnknownHostException("Host not found")
        val converted = original.toExchangeException()

        assertTrue(converted is ExchangeException.Unknown)
        assertTrue(converted.reason.contains("host"))
    }

    @Test
    fun `test toExchangeException with ConnectException`() {
        val original = ConnectException("Connection refused")
        val converted = original.toExchangeException()

        assertTrue(converted is ExchangeException.Unknown)
        assertTrue(converted.reason.contains("Connection"))
    }

    @Test
    fun `test toExchangeException with SocketTimeoutException`() {
        val original = SocketTimeoutException("Socket timeout")
        val converted = original.toExchangeException()

        assertTrue(converted is ExchangeException.Unknown)
        assertTrue(converted.reason.contains("timeout"))
    }

    @Test
    fun `test toExchangeException with IOException`() {
        val original = IOException("I/O error")
        val converted = original.toExchangeException()

        assertTrue(converted is ExchangeException.Unknown)
        assertTrue(converted.reason.contains("I/O"))
    }

    @Test
    fun `test toExchangeException with TrustWeaveException`() {
        val original = TrustWeaveException.NotFound(
            resource = "protocol"
        )
        val converted = original.toExchangeException()

        assertTrue(converted is ExchangeException.Unknown)
        assertEquals("NOT_FOUND", (converted as ExchangeException.Unknown).errorType)
        assertEquals(original, converted.cause)
    }

    @Test
    fun `test toExchangeException with unknown exception`() {
        val original = RuntimeException("Unknown error")
        val converted = original.toExchangeException()

        assertTrue(converted is ExchangeException.Unknown)
        assertEquals("RuntimeException", (converted as ExchangeException.Unknown).errorType)
        assertEquals("Unknown error", converted.reason)
        assertEquals(original, converted.cause)
    }

    @Test
    fun `test toExchangeException with exception without message`() {
        val original = Exception()
        val converted = original.toExchangeException()

        assertTrue(converted is ExchangeException.Unknown)
        assertNotNull((converted as ExchangeException.Unknown).errorType)
        assertTrue(converted.reason.contains("Exception"))
    }

    // ============================================================================
    // Context filtering tests
    // ============================================================================

    @Test
    fun `test context filtering with null values`() {
        val exception = ExchangeException.MissingRequiredOption(
            optionName = "fromKeyId",
            protocolName = null
        )

        // Protocol name should not be in context if null
        assertTrue(!exception.context.containsKey("protocolName"))
        assertTrue(exception.context.containsKey("optionName"))
    }

    @Test
    fun `test context includes all non-null values`() {
        // Note: DidCommException is in plugin module, using ExchangeException.Unknown instead
        val exception = ExchangeException.Unknown(
            reason = "Encryption failed",
            errorType = "DIDCOMM_ENCRYPTION_FAILED"
        )

        assertTrue(exception.context.containsKey("reason"))
        assertTrue(exception.context.containsKey("errorType"))
        // Note: DidCommException-specific fields not available in ExchangeException.Unknown
    }
}

