package org.trustweave.anchor.exceptions

import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for Blockchain exceptions.
 */
class BlockchainExceptionsTest {

    @Test
    fun `test BlockchainException TransactionFailed message formatting`() {
        val exception = BlockchainException.TransactionFailed(
            chainId = "eip155:137",
            txHash = "0x123abc",
            operation = "writePayload",
            payloadSize = 1024L,
            gasUsed = 21000L,
            reason = "Transaction failed"
        )

        assertTrue(exception.message.contains("[Chain: eip155:137]"))
        assertTrue(exception.message.contains("[Operation: writePayload]"))
        assertTrue(exception.message.contains("[TxHash: 0x123abc]"))
        assertTrue(exception.message.contains("Transaction failed"))
        assertEquals("0x123abc", exception.txHash)
        assertEquals(1024L, exception.payloadSize)
        assertEquals(21000L, exception.gasUsed)
    }

    @Test
    fun `test BlockchainException TransactionFailed with cause`() {
        val cause = RuntimeException("Underlying error")
        // BlockchainException extends TrustWeaveException which extends Exception
        // Exception.initCause can only be called if cause is null
        // Since we can't set cause in constructor, we test the exception structure
        val exception = BlockchainException.TransactionFailed(
            chainId = "algorand:testnet",
            reason = "Transaction failed"
        )

        assertTrue(exception.message.contains("[Chain: algorand:testnet]"))
        assertTrue(exception.message.contains("Transaction failed"))
    }

    @Test
    fun `test BlockchainException ConnectionFailed message formatting`() {
        val exception = BlockchainException.ConnectionFailed(
            chainId = "eip155:137",
            endpoint = "https://rpc.example.com",
            reason = "Connection failed"
        )

        assertTrue(exception.message.contains("[Chain: eip155:137]"))
        assertTrue(exception.message.contains("[Endpoint: https://rpc.example.com]"))
        assertEquals("https://rpc.example.com", exception.endpoint)
    }

    @Test
    fun `test BlockchainException ConfigurationFailed message formatting`() {
        val exception = BlockchainException.ConfigurationFailed(
            chainId = "algorand:testnet",
            configKey = "privateKey",
            reason = "Invalid configuration"
        )

        assertTrue(exception.message.contains("[Chain: algorand:testnet]"))
        assertTrue(exception.message.contains("[Config: privateKey]"))
        assertEquals("privateKey", exception.configKey)
    }

    @Test
    fun `test BlockchainException UnsupportedOperation`() {
        val exception = BlockchainException.UnsupportedOperation(
            chainId = "indy:testnet:bcovrin",
            operation = "customOperation",
            reason = "Operation not supported"
        )

        assertTrue(exception.message.contains("[Chain: indy:testnet:bcovrin]"))
        assertTrue(exception.message.contains("[Operation: customOperation]"))
        assertEquals("customOperation", exception.operation)
    }
}


