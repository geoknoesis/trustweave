package com.trustweave.core.exception

import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Tests for TrustWeaveError types and error conversion.
 */
class TrustWeaveErrorTest {

    @Test
    fun `test BlankPluginId error`() {
        val error = TrustWeaveError.BlankPluginId()
        
        assertEquals("BLANK_PLUGIN_ID", error.code)
        assertEquals("Plugin ID cannot be blank", error.message)
        assertTrue(error.context.isEmpty())
    }

    @Test
    fun `test PluginAlreadyRegistered error`() {
        val error = TrustWeaveError.PluginAlreadyRegistered(
            pluginId = "test-plugin",
            existingPlugin = "Test Plugin"
        )
        
        assertEquals("PLUGIN_ALREADY_REGISTERED", error.code)
        assertEquals("test-plugin", error.pluginId)
        assertEquals("Test Plugin", error.existingPlugin)
        assertEquals("test-plugin", error.context["pluginId"])
    }

    @Test
    fun `test NoProvidersFound error`() {
        val error = TrustWeaveError.NoProvidersFound(
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
    fun `test PartialProvidersFound error`() {
        val error = TrustWeaveError.PartialProvidersFound(
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
    fun `test AllProvidersFailed error`() {
        val lastException = RuntimeException("Last error")
        val error = TrustWeaveError.AllProvidersFailed(
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
    fun `test ConfigNotFound error`() {
        val error = TrustWeaveError.ConfigNotFound(path = "/path/to/config.json")
        
        assertEquals("CONFIG_NOT_FOUND", error.code)
        assertEquals("/path/to/config.json", error.path)
        assertEquals("/path/to/config.json", error.context["path"])
    }

    @Test
    fun `test ConfigReadFailed error`() {
        val error = TrustWeaveError.ConfigReadFailed(
            path = "/path/to/config.json",
            reason = "Permission denied"
        )
        
        assertEquals("CONFIG_READ_FAILED", error.code)
        assertEquals("/path/to/config.json", error.path)
        assertEquals("Permission denied", error.reason)
    }

    @Test
    fun `test InvalidConfigFormat error`() {
        val error = TrustWeaveError.InvalidConfigFormat(
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
    fun `test InvalidJson error`() {
        val error = TrustWeaveError.InvalidJson(
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
    fun `test JsonEncodeFailed error`() {
        val error = TrustWeaveError.JsonEncodeFailed(
            element = "{ large object }",
            reason = "Circular reference"
        )
        
        assertEquals("JSON_ENCODE_FAILED", error.code)
        assertEquals("Circular reference", error.reason)
        assertEquals("{ large object }", error.element)
    }

    @Test
    fun `test DigestFailed error`() {
        val error = TrustWeaveError.DigestFailed(
            algorithm = "SHA-256",
            reason = "Algorithm not available"
        )
        
        assertEquals("DIGEST_FAILED", error.code)
        assertEquals("SHA-256", error.algorithm)
        assertEquals("Algorithm not available", error.reason)
    }

    @Test
    fun `test EncodeFailed error`() {
        val error = TrustWeaveError.EncodeFailed(
            operation = "base58-encoding",
            reason = "Invalid byte array"
        )
        
        assertEquals("ENCODE_FAILED", error.code)
        assertEquals("base58-encoding", error.operation)
        assertEquals("Invalid byte array", error.reason)
    }

    @Test
    fun `test toTrustWeaveError converts IllegalArgumentException`() {
        val exception = IllegalArgumentException("Test error")
        val error = exception.toTrustWeaveError()
        
        assertTrue(error is TrustWeaveError.InvalidOperation)
        assertEquals("INVALID_ARGUMENT", error.code)
        assertEquals("Test error", error.message)
        assertEquals(exception, error.cause)
    }

    @Test
    fun `test toTrustWeaveError converts IllegalStateException`() {
        val exception = IllegalStateException("Invalid state")
        val error = exception.toTrustWeaveError()
        
        assertTrue(error is TrustWeaveError.InvalidState)
        assertEquals("INVALID_STATE", error.code)
        assertEquals("Invalid state", error.message)
    }

    @Test
    fun `test toTrustWeaveError converts NotFoundException`() {
        val exception = NotFoundException("Resource not found")
        val error = exception.toTrustWeaveError()
        
        assertTrue(error is TrustWeaveError.InvalidOperation)
        assertEquals("NOT_FOUND", error.code)
        assertEquals("Resource not found", error.message)
    }

    @Test
    fun `test toTrustWeaveError preserves TrustWeaveError`() {
        val originalError = TrustWeaveError.ValidationFailed(
            field = "test",
            reason = "Invalid",
            value = null
        )
        
        val converted = originalError.toTrustWeaveError()
        
        assertSame(originalError, converted)
    }

    @Test
    fun `test toTrustWeaveError converts unknown exception to Unknown`() {
        val exception = RuntimeException("Unknown error")
        val error = exception.toTrustWeaveError()
        
        assertTrue(error is TrustWeaveError.Unknown)
        assertEquals("UNKNOWN_ERROR", error.code)
        assertEquals("Unknown error", error.message)
    }

    @Test
    fun `test toTrustWeaveError handles null message`() {
        val exception = RuntimeException(null as String?)
        val error = exception.toTrustWeaveError()
        
        assertTrue(error is TrustWeaveError.Unknown)
        assertTrue(error.message.contains("RuntimeException"))
    }
}

