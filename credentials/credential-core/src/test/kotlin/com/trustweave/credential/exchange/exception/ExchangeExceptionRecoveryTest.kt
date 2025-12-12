package com.trustweave.credential.exchange.exception

// Note: Plugin-specific exceptions (DidCommException, Oidc4VciException) are in plugin modules
// Tests use ExchangeException with error codes instead to avoid plugin dependencies
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for ExchangeExceptionRecovery utilities.
 * 
 * NOTE: ExchangeExceptionRecovery doesn't exist in credential-core.
 * These tests are commented out until the utility is implemented.
 */
class ExchangeExceptionRecoveryTest {

    // Note: ExchangeExceptionRecovery doesn't exist - commented out until implemented
    /*
    @Test
    fun `test isRetryable with non-retryable exceptions`() = runBlocking {
        // Validation errors are not retryable
        assertFalse(ExchangeExceptionRecovery.isRetryable(
            ExchangeException.MissingRequiredOption("fromKeyId")
        ))

        assertFalse(ExchangeExceptionRecovery.isRetryable(
            ExchangeException.InvalidRequest("field", "reason")
        ))

        assertFalse(ExchangeExceptionRecovery.isRetryable(
            ExchangeException.ProtocolNotRegistered("didcomm")
        ))

        assertFalse(ExchangeExceptionRecovery.isRetryable(
            ExchangeException.OperationNotSupported("didcomm", "OPERATION")
        ))
    }

    @Test
    fun `test isRetryable with HTTP errors`() = runBlocking {
        // 5xx errors are retryable
        assertTrue(ExchangeExceptionRecovery.isRetryable(
            ExchangeException.Unknown(
                reason = "Internal Server Error",
                errorType = "OIDC4VCI_HTTP_REQUEST_FAILED"
            )
        ))

        // 4xx errors are not retryable
        assertFalse(ExchangeExceptionRecovery.isRetryable(
            ExchangeException.Unknown(
                reason = "Bad Request",
                errorType = "OIDC4VCI_HTTP_REQUEST_FAILED"
            )
        ))

        // Null status code (network error) is retryable
        assertTrue(ExchangeExceptionRecovery.isRetryable(
            ExchangeException.Unknown(
                reason = "Connection failed",
                errorType = "OIDC4VCI_HTTP_REQUEST_FAILED"
            )
        ))
    }

    @Test
    fun `test isRetryable with DIDComm errors`() = runBlocking {
        // Network-related errors are retryable
        assertTrue(ExchangeExceptionRecovery.isRetryable(
            ExchangeException.Unknown(
                reason = "Network timeout",
                errorType = "DIDCOMM_ENCRYPTION_FAILED"
            )
        ))

        assertTrue(ExchangeExceptionRecovery.isRetryable(
            ExchangeException.Unknown(
                reason = "Connection unavailable",
                errorType = "DIDCOMM_DECRYPTION_FAILED"
            )
        ))

        // Key/format errors are not retryable
        assertFalse(ExchangeExceptionRecovery.isRetryable(
            ExchangeException.Unknown(
                reason = "Invalid key format",
                errorType = "DIDCOMM_ENCRYPTION_FAILED"
            )
        ))
    }

    @Test
    fun `test isTransient`() = runBlocking {
        // 5xx errors are transient
        assertTrue(ExchangeExceptionRecovery.isTransient(
            ExchangeException.Unknown(
                reason = "Internal Server Error",
                errorType = "OIDC4VCI_HTTP_REQUEST_FAILED"
            )
        ))

        // 429 (rate limit) is transient
        assertTrue(ExchangeExceptionRecovery.isTransient(
            ExchangeException.Unknown(
                reason = "Too Many Requests",
                errorType = "OIDC4VCI_HTTP_REQUEST_FAILED"
            )
        ))

        // 4xx errors are not transient
        assertFalse(ExchangeExceptionRecovery.isTransient(
            ExchangeException.Unknown(
                reason = "HTTP request failed",
                errorType = "OIDC4VCI_HTTP_REQUEST_FAILED"
            )
        ))
    }

    @Test
    fun `test getUserFriendlyMessage`() = runBlocking {
        val message = ExchangeExceptionRecovery.getUserFriendlyMessage(
            ExchangeException.ProtocolNotRegistered(
                protocolName = "didcomm",
                availableProtocols = listOf("oidc4vci", "chapi")
            )
        )

        assertTrue(message.contains("didcomm"))
        assertTrue(message.contains("oidc4vci"))
        assertTrue(message.contains("chapi"))
    }

    @Test
    fun `test retryExchangeOperation with successful operation`() = runBlocking {
        var attemptCount = 0
        val result = ExchangeExceptionRecovery.retryExchangeOperation {
            attemptCount++
            "success"
        }

        assertEquals("success", result)
        assertEquals(1, attemptCount)
    }

    @Test
    fun `test retryExchangeOperation with retryable error`() = runBlocking {
        var attemptCount = 0
        val result = ExchangeExceptionRecovery.retryExchangeOperation(maxRetries = 3) {
            attemptCount++
            if (attemptCount < 3) {
                throw ExchangeException.Unknown(
                    reason = "Internal Server Error",
                    errorType = "OIDC4VCI_HTTP_REQUEST_FAILED"
                )
            }
            "success"
        }

        assertEquals("success", result)
        assertEquals(3, attemptCount)
    }

    @Test
    fun `test retryExchangeOperation with non-retryable error`() = runBlocking {
        var attemptCount = 0

        val exception = assertFailsWith<ExchangeException.MissingRequiredOption> {
            ExchangeExceptionRecovery.retryExchangeOperation(maxRetries = 3) {
                attemptCount++
                throw ExchangeException.MissingRequiredOption("fromKeyId")
            }
        }

        assertEquals("fromKeyId", exception.optionName)
        assertEquals(1, attemptCount) // Should not retry
    }

    @Test
    fun `test tryAlternativeProtocol with ProtocolNotRegistered`() = runBlocking {
        val exception = ExchangeException.ProtocolNotRegistered(
            protocolName = "didcomm",
            availableProtocols = listOf("oidc4vci", "chapi")
        )

        var calledProtocol: String? = null
        val result = ExchangeExceptionRecovery.tryAlternativeProtocol(
            exception = exception,
            availableProtocols = listOf("oidc4vci", "chapi")
        ) { protocol ->
            calledProtocol = protocol
            "success"
        }

        assertNotNull(result)
        assertEquals("success", result)
        assertNotNull(calledProtocol)
        assertTrue(calledProtocol != "didcomm")
    }

    @Test
    fun `test tryAlternativeProtocol returns null for non-protocol errors`() = runBlocking {
        val exception = ExchangeException.MissingRequiredOption("fromKeyId")

        val result = ExchangeExceptionRecovery.tryAlternativeProtocol(
            exception = exception,
            availableProtocols = listOf("oidc4vci", "chapi")
        ) { protocol ->
            "success"
        }

        assertEquals(null, result)
    }

    @Test
    fun `test companion object isRetryable`() = runBlocking {
        // Note: ExchangeException.isRetryable may not exist as a companion object method
        // Commented out until confirmed
        /*
        assertFalse(ExchangeException.isRetryable(
            ExchangeException.MissingRequiredOption("fromKeyId")
        ))

        assertTrue(ExchangeException.isRetryable(
            ExchangeException.Unknown(
                reason = "Internal Server Error",
                errorType = "OIDC4VCI_HTTP_REQUEST_FAILED"
            )
        ))
        */
    }

    @Test
    fun `test companion object getUserFriendlyMessage`() = runBlocking {
        // Note: ExchangeException.getUserFriendlyMessage may not exist as a companion object method
        // Commented out until confirmed
        /*
        val message = ExchangeException.getUserFriendlyMessage(
            ExchangeException.ProtocolNotRegistered(
                protocolName = "didcomm",
                availableProtocols = listOf("oidc4vci")
            )
        )

        assertTrue(message.contains("didcomm"))
        assertTrue(message.contains("oidc4vci"))
        */
    }
    */
}

