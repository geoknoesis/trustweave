package com.trustweave.core.exception

import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Tests for TrustWeaveException types and error conversion.
 */
class TrustWeaveExceptionTest {

    @Test
    fun `test BlankPluginId exception`() {
        val error = TrustWeaveException.BlankPluginId()
        
        assertEquals("BLANK_PLUGIN_ID", error.code)
        assertEquals("Plugin ID cannot be blank", error.message)
        assertTrue(error.context.isEmpty())
    }

    @Test
    fun `test PluginAlreadyRegistered exception`() {
        val error = TrustWeaveException.PluginAlreadyRegistered(
            pluginId = "test-plugin",
            existingPlugin = "Test Plugin"
        )
        
        assertEquals("PLUGIN_ALREADY_REGISTERED", error.code)
        assertEquals("test-plugin", error.pluginId)
        assertEquals("Test Plugin", error.existingPlugin)
        assertEquals("test-plugin", error.context["pluginId"])
    }

    @Test
    fun `test NoProvidersFound exception`() {
        val error = TrustWeaveException.NoProvidersFound(
            pluginIds = listOf("plugin-1", "plugin-2"),
            availablePlugins = listOf("plugin-3", "plugin-4")
        )
        
        assertEquals("NO_PROVIDERS_FOUND", error.code)
        assertEquals(2, error.pluginIds.size)
        assertEquals(2, error.availablePlugins.size)
        assertTrue(error.context.containsKey("pluginIds"))
        assertTrue(error.context.containsKey("availablePlugins"))
    }

    @Test
    fun `test PartialProvidersFound exception`() {
        val error = TrustWeaveException.PartialProvidersFound(
            requestedIds = listOf("plugin-1", "plugin-2", "plugin-3"),
            foundIds = listOf("plugin-1"),
            missingIds = listOf("plugin-2", "plugin-3")
        )
        
        assertEquals("PARTIAL_PROVIDERS_FOUND", error.code)
        assertEquals(3, error.requestedIds.size)
        assertEquals(1, error.foundIds.size)
        assertEquals(2, error.missingIds.size)
        assertTrue(error.message.contains("1 of 3"))
    }

    @Test
    fun `test AllProvidersFailed exception`() {
        val lastException = RuntimeException("Last error")
        val error = TrustWeaveException.AllProvidersFailed(
            attemptedProviders = listOf("provider-1", "provider-2"),
            providerErrors = mapOf(
                "provider-1" to "Error 1",
                "provider-2" to "Error 2"
            ),
            lastException = lastException
        )
        
        assertEquals("ALL_PROVIDERS_FAILED", error.code)
        assertEquals(2, error.attemptedProviders.size)
        assertEquals(2, error.providerErrors.size)
        assertEquals(lastException, error.cause)
    }

    @Test
    fun `test ConfigNotFound exception`() {
        val error = TrustWeaveException.ConfigNotFound(path = "/path/to/config.json")
        
        assertEquals("CONFIG_NOT_FOUND", error.code)
        assertEquals("/path/to/config.json", error.path)
        assertEquals("/path/to/config.json", error.context["path"])
    }

    @Test
    fun `test ConfigReadFailed exception`() {
        val error = TrustWeaveException.ConfigReadFailed(
            path = "/path/to/config.json",
            reason = "Permission denied"
        )
        
        assertEquals("CONFIG_READ_FAILED", error.code)
        assertEquals("/path/to/config.json", error.path)
        assertEquals("Permission denied", error.reason)
    }

    @Test
    fun `test InvalidConfigFormat exception`() {
        val error = TrustWeaveException.InvalidConfigFormat(
            jsonString = "{ invalid }",
            parseError = "Expected ',' or '}'",
            field = "plugins"
        )
        
        assertEquals("INVALID_CONFIG_FORMAT", error.code)
        assertEquals("{ invalid }", error.jsonString)
        assertEquals("Expected ',' or '}'", error.parseError)
        assertEquals("plugins", error.field)
        assertTrue(error.message.contains("plugins"))
    }

    @Test
    fun `test InvalidJson exception`() {
        val error = TrustWeaveException.InvalidJson(
            jsonString = "{ invalid }",
            parseError = "Expected ',' or '}'",
            position = "line 1, column 10"
        )
        
        assertEquals("INVALID_JSON", error.code)
        assertEquals("{ invalid }", error.jsonString?.take(500))
        assertEquals("Expected ',' or '}'", error.parseError)
        assertEquals("line 1, column 10", error.position)
        assertTrue(error.message.contains("line 1, column 10"))
    }

    @Test
    fun `test JsonEncodeFailed exception`() {
        val error = TrustWeaveException.JsonEncodeFailed(
            element = "{ large object }",
            reason = "Circular reference"
        )
        
        assertEquals("JSON_ENCODE_FAILED", error.code)
        assertEquals("Circular reference", error.reason)
        assertEquals("{ large object }", error.element)
    }

    @Test
    fun `test DigestFailed exception`() {
        val error = TrustWeaveException.DigestFailed(
            algorithm = "SHA-256",
            reason = "Algorithm not available"
        )
        
        assertEquals("DIGEST_FAILED", error.code)
        assertEquals("SHA-256", error.algorithm)
        assertEquals("Algorithm not available", error.reason)
    }

    @Test
    fun `test EncodeFailed exception`() {
        val error = TrustWeaveException.EncodeFailed(
            operation = "base58-encoding",
            reason = "Invalid byte array"
        )
        
        assertEquals("ENCODE_FAILED", error.code)
        assertEquals("base58-encoding", error.operation)
        assertEquals("Invalid byte array", error.reason)
    }

    @Test
    fun `test toTrustWeaveException converts IllegalArgumentException`() {
        val exception = IllegalArgumentException("Test error")
        val error = exception.toTrustWeaveException()
        
        assertTrue(error is TrustWeaveException.InvalidOperation)
        assertEquals("INVALID_ARGUMENT", error.code)
        assertEquals("Test error", error.message)
        assertEquals(exception, error.cause)
    }

    @Test
    fun `test toTrustWeaveException converts IllegalStateException`() {
        val exception = IllegalStateException("Invalid state")
        val error = exception.toTrustWeaveException()
        
        assertTrue(error is TrustWeaveException.InvalidState)
        assertEquals("INVALID_STATE", error.code)
        assertEquals("Invalid state", error.message)
    }

    @Test
    fun `test toTrustWeaveException preserves TrustWeaveException`() {
        val originalError = TrustWeaveException.ValidationFailed(
            field = "test",
            reason = "Invalid",
            value = null
        )
        
        val converted = originalError.toTrustWeaveException()
        
        assertSame(originalError, converted)
    }

    @Test
    fun `test toTrustWeaveException converts unknown exception to Unknown`() {
        val exception = RuntimeException("Unknown error")
        val error = exception.toTrustWeaveException()
        
        assertTrue(error is TrustWeaveException.Unknown)
        assertEquals("UNKNOWN_ERROR", error.code)
        assertEquals("Unknown error", error.message)
    }

    @Test
    fun `test toTrustWeaveException handles null message`() {
        val exception = RuntimeException(null as String?)
        val error = exception.toTrustWeaveException()
        
        assertTrue(error is TrustWeaveException.Unknown)
        assertTrue(error.message.contains("RuntimeException"))
    }
}

