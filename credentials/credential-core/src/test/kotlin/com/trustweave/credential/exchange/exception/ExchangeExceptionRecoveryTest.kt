package com.trustweave.credential.exchange.exception

import com.trustweave.credential.didcomm.exception.DidCommException
import com.trustweave.credential.oidc4vci.exception.Oidc4VciException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for ExchangeExceptionRecovery utilities.
 */
class ExchangeExceptionRecoveryTest {
    
    @Test
    fun `test isRetryable with non-retryable exceptions`() = runTest {
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
    fun `test isRetryable with HTTP errors`() = runTest {
        // 5xx errors are retryable
        assertTrue(ExchangeExceptionRecovery.isRetryable(
            Oidc4VciException.HttpRequestFailed(
                url = "https://example.com",
                statusCode = 500,
                reason = "Internal Server Error"
            )
        ))
        
        // 4xx errors are not retryable
        assertFalse(ExchangeExceptionRecovery.isRetryable(
            Oidc4VciException.HttpRequestFailed(
                url = "https://example.com",
                statusCode = 400,
                reason = "Bad Request"
            )
        ))
        
        // Null status code (network error) is retryable
        assertTrue(ExchangeExceptionRecovery.isRetryable(
            Oidc4VciException.HttpRequestFailed(
                url = "https://example.com",
                statusCode = null,
                reason = "Connection failed"
            )
        ))
    }
    
    @Test
    fun `test isRetryable with DIDComm errors`() = runTest {
        // Network-related errors are retryable
        assertTrue(ExchangeExceptionRecovery.isRetryable(
            DidCommException.EncryptionFailed(
                reason = "Network timeout",
                fromDid = "did:key:issuer",
                toDid = "did:key:holder"
            )
        ))
        
        assertTrue(ExchangeExceptionRecovery.isRetryable(
            DidCommException.DecryptionFailed(
                reason = "Connection unavailable",
                messageId = "msg-123"
            )
        ))
        
        // Key/format errors are not retryable
        assertFalse(ExchangeExceptionRecovery.isRetryable(
            DidCommException.EncryptionFailed(
                reason = "Invalid key format",
                fromDid = "did:key:issuer",
                toDid = "did:key:holder"
            )
        ))
    }
    
    @Test
    fun `test isTransient`() = runTest {
        // 5xx errors are transient
        assertTrue(ExchangeExceptionRecovery.isTransient(
            Oidc4VciException.HttpRequestFailed(
                url = "https://example.com",
                statusCode = 500,
                reason = "Internal Server Error"
            )
        ))
        
        // 429 (rate limit) is transient
        assertTrue(ExchangeExceptionRecovery.isTransient(
            Oidc4VciException.HttpRequestFailed(
                url = "https://example.com",
                statusCode = 429,
                reason = "Too Many Requests"
            )
        ))
        
        // 4xx errors are not transient
        assertFalse(ExchangeExceptionRecovery.isTransient(
            Oidc4VciException.HttpRequestFailed(
                url = "https://example.com",
                statusCode = 400,
                reason = "Bad Request"
            )
        ))
    }
    
    @Test
    fun `test getUserFriendlyMessage`() = runTest {
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
    fun `test retryExchangeOperation with successful operation`() = runTest {
        var attemptCount = 0
        val result = ExchangeExceptionRecovery.retryExchangeOperation {
            attemptCount++
            "success"
        }
        
        assertEquals("success", result)
        assertEquals(1, attemptCount)
    }
    
    @Test
    fun `test retryExchangeOperation with retryable error`() = runTest {
        var attemptCount = 0
        val result = ExchangeExceptionRecovery.retryExchangeOperation(maxRetries = 3) {
            attemptCount++
            if (attemptCount < 3) {
                throw Oidc4VciException.HttpRequestFailed(
                    url = "https://example.com",
                    statusCode = 500,
                    reason = "Internal Server Error"
                )
            }
            "success"
        }
        
        assertEquals("success", result)
        assertEquals(3, attemptCount)
    }
    
    @Test
    fun `test retryExchangeOperation with non-retryable error`() = runTest {
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
    fun `test tryAlternativeProtocol with ProtocolNotRegistered`() = runTest {
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
    fun `test tryAlternativeProtocol returns null for non-protocol errors`() = runTest {
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
    fun `test companion object isRetryable`() = runTest {
        assertFalse(ExchangeException.isRetryable(
            ExchangeException.MissingRequiredOption("fromKeyId")
        ))
        
        assertTrue(ExchangeException.isRetryable(
            Oidc4VciException.HttpRequestFailed(
                url = "https://example.com",
                statusCode = 500,
                reason = "Internal Server Error"
            )
        ))
    }
    
    @Test
    fun `test companion object getUserFriendlyMessage`() = runTest {
        val message = ExchangeException.getUserFriendlyMessage(
            ExchangeException.ProtocolNotRegistered(
                protocolName = "didcomm",
                availableProtocols = listOf("oidc4vci")
            )
        )
        
        assertTrue(message.contains("didcomm"))
        assertTrue(message.contains("oidc4vci"))
    }
}

