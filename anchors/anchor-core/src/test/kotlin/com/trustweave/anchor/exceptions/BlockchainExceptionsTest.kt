package com.trustweave.anchor.exceptions

import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for Blockchain exceptions.
 */
class BlockchainExceptionsTest {

    @Test
    fun `test BlockchainException message formatting`() {
        val exception = BlockchainException(
            message = "Test error",
            chainId = "algorand:testnet",
            operation = "writePayload"
        )
        
        assertTrue(exception.message.contains("[Chain: algorand:testnet]"))
        assertTrue(exception.message.contains("[Operation: writePayload]"))
        assertTrue(exception.message.contains("Test error"))
    }

    @Test
    fun `test BlockchainException without optional fields`() {
        val exception = BlockchainException("Simple error")
        
        assertEquals("Simple error", exception.message)
        assertNull(exception.chainId)
        assertNull(exception.operation)
    }

    @Test
    fun `test BlockchainTransactionException message formatting`() {
        val exception = BlockchainTransactionException(
            message = "Transaction failed",
            chainId = "eip155:137",
            txHash = "0x123abc",
            operation = "writePayload",
            payloadSize = 1024L,
            gasUsed = 21000L
        )
        
        assertTrue(exception.message.contains("[Chain: eip155:137]"))
        assertTrue(exception.message.contains("[Operation: writePayload]"))
        assertTrue(exception.message.contains("[TxHash: 0x123abc]"))
        assertTrue(exception.message.contains("[PayloadSize: 1024B]"))
        assertTrue(exception.message.contains("[GasUsed: 21000]"))
        assertEquals("0x123abc", exception.txHash)
        assertEquals(1024L, exception.payloadSize)
        assertEquals(21000L, exception.gasUsed)
    }

    @Test
    fun `test BlockchainTransactionException with cause`() {
        val cause = RuntimeException("Underlying error")
        val exception = BlockchainTransactionException(
            message = "Transaction failed",
            chainId = "algorand:testnet",
            cause = cause
        )
        
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `test BlockchainConnectionException message formatting`() {
        val exception = BlockchainConnectionException(
            message = "Connection failed",
            chainId = "eip155:137",
            endpoint = "https://rpc.example.com"
        )
        
        assertTrue(exception.message.contains("[Chain: eip155:137]"))
        assertTrue(exception.message.contains("[Endpoint: https://rpc.example.com]"))
        assertEquals("https://rpc.example.com", exception.endpoint)
        assertEquals("connection", exception.operation)
    }

    @Test
    fun `test BlockchainConfigurationException message formatting`() {
        val exception = BlockchainConfigurationException(
            message = "Invalid configuration",
            chainId = "algorand:testnet",
            configKey = "privateKey"
        )
        
        assertTrue(exception.message.contains("[Chain: algorand:testnet]"))
        assertTrue(exception.message.contains("[Config: privateKey]"))
        assertEquals("privateKey", exception.configKey)
        assertEquals("configuration", exception.operation)
    }

    @Test
    fun `test BlockchainUnsupportedOperationException`() {
        val exception = BlockchainUnsupportedOperationException(
            message = "Operation not supported",
            chainId = "indy:testnet:bcovrin",
            operation = "customOperation"
        )
        
        assertTrue(exception.message.contains("[Chain: indy:testnet:bcovrin]"))
        assertTrue(exception.message.contains("[Operation: customOperation]"))
        assertEquals("customOperation", exception.operation)
    }
}


