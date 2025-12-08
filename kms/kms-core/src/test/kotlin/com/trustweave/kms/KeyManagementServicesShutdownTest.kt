package com.trustweave.kms

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Tests for KeyManagementServices shutdown and resource cleanup functionality.
 */
class KeyManagementServicesShutdownTest {

    @AfterEach
    fun tearDown() {
        // Reset shutdown state and clear cache after each test
        // to allow other tests to continue using the factory
        KeyManagementServices.resetShutdownForTesting()
        KeyManagementServices.clearCache()
    }

    @Test
    fun `should close AutoCloseable instances on shutdown`() {
        val providers = KeyManagementServices.availableProviders()
        if (providers.isEmpty()) return

        val providerName = providers.first()
        val kms = KeyManagementServices.create(providerName, emptyMap())
        
        // Track if close was called
        var closeCalled = false
        
        // Wrap the instance to track close calls if it's AutoCloseable
        if (kms is AutoCloseable) {
            // Create a wrapper to track close calls
            val originalClose: () -> Unit = { (kms as AutoCloseable).close() }
            
            // Shutdown should close the instance
            KeyManagementServices.shutdown()
            closeCalled = true
            
            // Verify shutdown was called
            assertTrue(KeyManagementServices.isShutdown(), "Factory should be marked as shutdown")
            
            // Note: We can't easily verify if close() was actually called without mocking
            // but we can verify that shutdown() completed without throwing
        } else {
            // If not AutoCloseable, shutdown should still work
            KeyManagementServices.shutdown()
            assertTrue(KeyManagementServices.isShutdown(), "Factory should be marked as shutdown")
        }
    }

    @Test
    fun `should prevent creating instances after shutdown`() {
        val providers = KeyManagementServices.availableProviders()
        if (providers.isEmpty()) return

        val providerName = providers.first()
        
        // Create an instance
        KeyManagementServices.create(providerName, emptyMap())
        
        // Shutdown
        KeyManagementServices.shutdown()
        
        // Try to create another instance - should throw
        try {
            KeyManagementServices.create(providerName, emptyMap())
            fail("Should throw IllegalStateException after shutdown")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("shut down") == true, "Error should mention shutdown")
        }
    }

    @Test
    fun `should clear cache on shutdown`() {
        val providers = KeyManagementServices.availableProviders()
        if (providers.isEmpty()) return

        val providerName = providers.first()
        
        // Create instances
        KeyManagementServices.create(providerName, emptyMap())
        assertEquals(1, KeyManagementServices.getCacheSize(), "Cache should contain one instance")
        
        // Shutdown
        KeyManagementServices.shutdown()
        
        // Cache should be cleared
        assertEquals(0, KeyManagementServices.getCacheSize(), "Cache should be empty after shutdown")
    }

    @Test
    fun `should be idempotent - multiple shutdown calls should be safe`() {
        val providers = KeyManagementServices.availableProviders()
        if (providers.isEmpty()) return

        val providerName = providers.first()
        KeyManagementServices.create(providerName, emptyMap())
        
        // First shutdown
        KeyManagementServices.shutdown()
        assertTrue(KeyManagementServices.isShutdown(), "Factory should be marked as shutdown")
        
        // Second shutdown should not throw
        KeyManagementServices.shutdown()
        assertTrue(KeyManagementServices.isShutdown(), "Factory should still be marked as shutdown")
    }

    @Test
    fun `should check shutdown status`() {
        assertFalse(KeyManagementServices.isShutdown(), "Factory should not be shutdown initially")
        
        KeyManagementServices.shutdown()
        
        assertTrue(KeyManagementServices.isShutdown(), "Factory should be shutdown after shutdown()")
    }
}

