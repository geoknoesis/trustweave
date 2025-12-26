package org.trustweave.core.exception

import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Tests for TrustWeaveException types and error conversion.
 */
class TrustWeaveExceptionTest {

    @Test
    fun `test BlankPluginId exception as object`() {
        val error = TrustWeaveException.BlankPluginId

        assertEquals("BLANK_PLUGIN_ID", error.code)
        assertEquals("Plugin ID cannot be blank", error.message)
        assertTrue(error.context.isEmpty())
        
        // Verify it's a singleton object
        val error2 = TrustWeaveException.BlankPluginId
        assertSame(error, error2)
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

    @Test
    fun `test TrustWeaveException Unknown with message`() {
        val exception = TrustWeaveException.Unknown(
            message = "Test error"
        )

        assertEquals("Test error", exception.message)
        assertNull(exception.cause)
        assertEquals("UNKNOWN_ERROR", exception.code)
    }

    @Test
    fun `test TrustWeaveException Unknown with message and cause`() {
        val cause = RuntimeException("Underlying error")
        val exception = TrustWeaveException.Unknown(
            message = "Test error",
            cause = cause
        )

        assertEquals("Test error", exception.message)
        assertEquals(cause, exception.cause)
        assertEquals("UNKNOWN_ERROR", exception.code)
    }

    @Test
    fun `test TrustWeaveException NotFound`() {
        val exception = TrustWeaveException.NotFound(
            message = "Resource not found"
        )

        assertEquals("Resource not found", exception.message)
        assertEquals("NOT_FOUND", exception.code)
    }

    @Test
    fun `test TrustWeaveException NotFound with cause`() {
        val cause = RuntimeException("Underlying error")
        val exception = TrustWeaveException.NotFound(
            message = "Resource not found",
            cause = cause
        )

        assertEquals("Resource not found", exception.message)
        assertEquals(cause, exception.cause)
        assertEquals("NOT_FOUND", exception.code)
    }

    @Test
    fun `test TrustWeaveException InvalidOperation`() {
        val exception = TrustWeaveException.InvalidOperation(
            message = "Invalid operation"
        )

        assertEquals("Invalid operation", exception.message)
        assertEquals("INVALID_OPERATION", exception.code)
    }

    @Test
    fun `test TrustWeaveException InvalidOperation with cause`() {
        val cause = IllegalArgumentException("Invalid argument")
        val exception = TrustWeaveException.InvalidOperation(
            message = "Invalid operation",
            cause = cause
        )

        assertEquals("Invalid operation", exception.message)
        assertEquals(cause, exception.cause)
        assertEquals("INVALID_OPERATION", exception.code)
    }

    // ============================================================================
    // Additional Plugin Exception Tests
    // ============================================================================

    @Test
    fun `test PluginNotFound exception with pluginType`() {
        val error = TrustWeaveException.PluginNotFound(
            pluginId = "test-plugin",
            pluginType = "KMS"
        )

        assertEquals("PLUGIN_NOT_FOUND", error.code)
        assertEquals("test-plugin", error.pluginId)
        assertEquals("KMS", error.pluginType)
        assertTrue(error.message.contains("test-plugin"))
        assertTrue(error.message.contains("KMS"))
        assertEquals("test-plugin", error.context["pluginId"])
        assertEquals("KMS", error.context["pluginType"])
    }

    @Test
    fun `test PluginNotFound exception without pluginType`() {
        val error = TrustWeaveException.PluginNotFound(
            pluginId = "test-plugin"
        )

        assertEquals("PLUGIN_NOT_FOUND", error.code)
        assertEquals("test-plugin", error.pluginId)
        assertNull(error.pluginType)
        assertTrue(error.message.contains("test-plugin"))
        assertFalse(error.message.contains("type"))
        // Null pluginType should be filtered from context
        assertFalse(error.context.containsKey("pluginType"))
    }

    @Test
    fun `test PluginInitializationFailed exception`() {
        val error = TrustWeaveException.PluginInitializationFailed(
            pluginId = "test-plugin",
            reason = "Missing dependency"
        )

        assertEquals("PLUGIN_INITIALIZATION_FAILED", error.code)
        assertEquals("test-plugin", error.pluginId)
        assertEquals("Missing dependency", error.reason)
        assertTrue(error.message.contains("test-plugin"))
        assertTrue(error.message.contains("Missing dependency"))
        assertEquals("test-plugin", error.context["pluginId"])
        assertEquals("Missing dependency", error.context["reason"])
    }

    @Test
    fun `test PluginAlreadyRegistered exception without existingPlugin`() {
        val error = TrustWeaveException.PluginAlreadyRegistered(
            pluginId = "test-plugin"
        )

        assertEquals("PLUGIN_ALREADY_REGISTERED", error.code)
        assertEquals("test-plugin", error.pluginId)
        assertNull(error.existingPlugin)
        // Null existingPlugin should be filtered from context
        assertFalse(error.context.containsKey("existingPlugin"))
    }

    // ============================================================================
    // Additional Provider Exception Tests
    // ============================================================================

    @Test
    fun `test NoProvidersFound exception with empty availablePlugins`() {
        val error = TrustWeaveException.NoProvidersFound(
            pluginIds = listOf("plugin-1", "plugin-2"),
            availablePlugins = emptyList()
        )

        assertEquals("NO_PROVIDERS_FOUND", error.code)
        assertTrue(error.message.contains("No plugins are registered"))
        assertTrue(error.message.contains("Register plugins"))
    }

    @Test
    fun `test PartialProvidersFound exception with all found`() {
        val error = TrustWeaveException.PartialProvidersFound(
            requestedIds = listOf("plugin-1", "plugin-2"),
            foundIds = listOf("plugin-1", "plugin-2"),
            missingIds = emptyList()
        )

        assertEquals("PARTIAL_PROVIDERS_FOUND", error.code)
        assertEquals(2, error.foundIds.size)
        assertEquals(0, error.missingIds.size)
        assertTrue(error.message.contains("2 of 2"))
    }

    @Test
    fun `test AllProvidersFailed exception without providerErrors`() {
        val error = TrustWeaveException.AllProvidersFailed(
            attemptedProviders = listOf("provider-1", "provider-2")
        )

        assertEquals("ALL_PROVIDERS_FAILED", error.code)
        assertEquals(2, error.attemptedProviders.size)
        assertTrue(error.providerErrors.isEmpty())
        assertTrue(error.message.contains("Check provider configurations"))
    }

    @Test
    fun `test AllProvidersFailed exception without lastException`() {
        val error = TrustWeaveException.AllProvidersFailed(
            attemptedProviders = listOf("provider-1"),
            providerErrors = mapOf("provider-1" to "Error")
        )

        assertEquals("ALL_PROVIDERS_FAILED", error.code)
        assertNull(error.cause)
    }

    // ============================================================================
    // Additional Config Exception Tests
    // ============================================================================

    @Test
    fun `test InvalidConfigFormat exception without jsonString and field`() {
        val error = TrustWeaveException.InvalidConfigFormat(
            parseError = "Expected ',' or '}'"
        )

        assertEquals("INVALID_CONFIG_FORMAT", error.code)
        assertNull(error.jsonString)
        assertNull(error.field)
        assertTrue(error.message.contains("Expected ',' or '}'"))
        assertFalse(error.context.containsKey("jsonString"))
        assertFalse(error.context.containsKey("field"))
    }

    @Test
    fun `test InvalidConfigFormat exception with jsonString only`() {
        val error = TrustWeaveException.InvalidConfigFormat(
            jsonString = "{ invalid }",
            parseError = "Expected ',' or '}'"
        )

        assertEquals("INVALID_CONFIG_FORMAT", error.code)
        assertEquals("{ invalid }", error.jsonString)
        assertTrue(error.context.containsKey("jsonString"))
    }

    // ============================================================================
    // Additional JSON Exception Tests
    // ============================================================================

    @Test
    fun `test InvalidJson exception without jsonString and position`() {
        val error = TrustWeaveException.InvalidJson(
            parseError = "Expected ',' or '}'"
        )

        assertEquals("INVALID_JSON", error.code)
        assertNull(error.jsonString)
        assertNull(error.position)
        assertTrue(error.message.contains("Expected ',' or '}'"))
        assertFalse(error.message.contains(" at "))
    }

    @Test
    fun `test InvalidJson exception with long jsonString`() {
        val longJson = "a".repeat(1000)
        val error = TrustWeaveException.InvalidJson(
            jsonString = longJson,
            parseError = "Error"
        )

        assertEquals("INVALID_JSON", error.code)
        // jsonString in context should be truncated to 500 characters
        val contextJson = error.context["jsonString"] as? String
        assertNotNull(contextJson)
        assertEquals(500, contextJson.length)
        // But the property itself keeps the full value
        assertEquals(1000, error.jsonString?.length)
    }

    @Test
    fun `test JsonEncodeFailed exception without element`() {
        val error = TrustWeaveException.JsonEncodeFailed(
            reason = "Circular reference"
        )

        assertEquals("JSON_ENCODE_FAILED", error.code)
        assertEquals("Circular reference", error.reason)
        assertNull(error.element)
        assertFalse(error.context.containsKey("element"))
    }

    // ============================================================================
    // Validation Exception Tests
    // ============================================================================

    @Test
    fun `test ValidationFailed exception with value`() {
        val error = TrustWeaveException.ValidationFailed(
            field = "email",
            reason = "Invalid format",
            value = "not-an-email"
        )

        assertEquals("VALIDATION_FAILED", error.code)
        assertEquals("email", error.field)
        assertEquals("Invalid format", error.reason)
        assertEquals("not-an-email", error.value)
        assertEquals("not-an-email", error.context["value"])
    }

    @Test
    fun `test ValidationFailed exception without value`() {
        val error = TrustWeaveException.ValidationFailed(
            field = "email",
            reason = "Required field"
        )

        assertEquals("VALIDATION_FAILED", error.code)
        assertEquals("email", error.field)
        assertEquals("Required field", error.reason)
        assertNull(error.value)
        assertNull(error.context["value"])
    }

    // ============================================================================
    // Generic Exception Tests
    // ============================================================================

    @Test
    fun `test InvalidState exception`() {
        val exception = TrustWeaveException.InvalidState(
            message = "Invalid state"
        )

        assertEquals("Invalid state", exception.message)
        assertEquals("INVALID_STATE", exception.code)
        assertTrue(exception.context.isEmpty())
        assertNull(exception.cause)
    }

    @Test
    fun `test InvalidState exception with context and cause`() {
        val cause = IllegalStateException("Underlying")
        val exception = TrustWeaveException.InvalidState(
            message = "Invalid state",
            context = mapOf("key" to "value"),
            cause = cause
        )

        assertEquals("Invalid state", exception.message)
        assertEquals("INVALID_STATE", exception.code)
        assertEquals("value", exception.context["key"])
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `test InvalidOperation exception with custom code`() {
        val exception = TrustWeaveException.InvalidOperation(
            code = "CUSTOM_ERROR",
            message = "Custom error",
            context = mapOf("key" to "value")
        )

        assertEquals("CUSTOM_ERROR", exception.code)
        assertEquals("Custom error", exception.message)
        assertEquals("value", exception.context["key"])
    }

    @Test
    fun `test NotFound exception with resource`() {
        val exception = TrustWeaveException.NotFound(
            resource = "user-123"
        )

        assertEquals("NOT_FOUND", exception.code)
        assertEquals("Resource not found: user-123", exception.message)
        assertEquals("user-123", exception.resource)
        // Context should now contain the resource (fixed with buildMap)
        assertTrue(exception.context.containsKey("resource"))
        assertEquals("user-123", exception.context["resource"])
    }

    @Test
    fun `test NotFound exception with custom context`() {
        val exception = TrustWeaveException.NotFound(
            resource = "user-123",
            baseContext = mapOf("custom" to "value", "another" to 42)
        )

        assertEquals("NOT_FOUND", exception.code)
        assertEquals("user-123", exception.resource)
        // Custom context should be preserved
        assertEquals("value", exception.context["custom"])
        assertEquals(42, exception.context["another"])
        // Resource should also be in context
        assertEquals("user-123", exception.context["resource"])
        assertEquals(3, exception.context.size)
    }

    @Test
    fun `test NotFound exception without resource`() {
        val exception = TrustWeaveException.NotFound()

        assertEquals("NOT_FOUND", exception.code)
        assertEquals("Resource not found", exception.message)
        assertNull(exception.resource)
        assertFalse(exception.context.containsKey("resource"))
    }

    @Test
    fun `test NotFound exception with custom message`() {
        val exception = TrustWeaveException.NotFound(
            resource = "user-123",
            message = "Custom not found message"
        )

        assertEquals("NOT_FOUND", exception.code)
        assertEquals("Custom not found message", exception.message)
        assertEquals("user-123", exception.resource)
    }

    @Test
    fun `test UnsupportedAlgorithm exception`() {
        val exception = TrustWeaveException.UnsupportedAlgorithm(
            algorithm = "RSA-1024",
            supportedAlgorithms = listOf("Ed25519", "ES256")
        )

        assertEquals("UNSUPPORTED_ALGORITHM", exception.code)
        assertEquals("RSA-1024", exception.algorithm)
        assertEquals(listOf("Ed25519", "ES256"), exception.supportedAlgorithms)
        assertTrue(exception.message.contains("RSA-1024"))
        assertTrue(exception.message.contains("Ed25519"))
        assertTrue(exception.message.contains("ES256"))
    }

    @Test
    fun `test Unknown exception with custom code`() {
        val exception = TrustWeaveException.Unknown(
            code = "CUSTOM_UNKNOWN",
            message = "Custom unknown error",
            context = mapOf("key" to "value")
        )

        assertEquals("CUSTOM_UNKNOWN", exception.code)
        assertEquals("Custom unknown error", exception.message)
        assertEquals("value", exception.context["key"])
    }

    // ============================================================================
    // Extension Function Tests
    // ============================================================================

    @Test
    fun `test trustWeaveException helper function`() {
        val exception = trustWeaveException("Test error")

        assertTrue(exception is TrustWeaveException.Unknown)
        assertEquals("UNKNOWN_ERROR", exception.code)
        assertEquals("Test error", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun `test trustWeaveException helper function with cause`() {
        val cause = RuntimeException("Underlying")
        val exception = trustWeaveException("Test error", cause)

        assertTrue(exception is TrustWeaveException.Unknown)
        assertEquals("UNKNOWN_ERROR", exception.code)
        assertEquals("Test error", exception.message)
        assertEquals(cause, exception.cause)
    }

    // ============================================================================
    // Helper Function Tests (isPluginException, etc.)
    // ============================================================================

    @Test
    fun `test isPluginException returns true for plugin exceptions`() {
        assertTrue(TrustWeaveException.PluginNotFound("test").isPluginException())
        assertTrue(TrustWeaveException.PluginInitializationFailed("test", "reason").isPluginException())
        assertTrue(TrustWeaveException.BlankPluginId.isPluginException())
        assertTrue(TrustWeaveException.PluginAlreadyRegistered("test").isPluginException())
    }

    @Test
    fun `test isPluginException returns false for non-plugin exceptions`() {
        assertFalse(TrustWeaveException.ConfigNotFound("path").isPluginException())
        assertFalse(TrustWeaveException.ValidationFailed("field", "reason").isPluginException())
        assertFalse(TrustWeaveException.Unknown(message = "error").isPluginException())
    }

    @Test
    fun `test isProviderException returns true for provider exceptions`() {
        assertTrue(TrustWeaveException.NoProvidersFound(listOf("test")).isProviderException())
        assertTrue(TrustWeaveException.PartialProvidersFound(
            listOf("1"), listOf("1"), emptyList()
        ).isProviderException())
        assertTrue(TrustWeaveException.AllProvidersFailed(listOf("test")).isProviderException())
    }

    @Test
    fun `test isProviderException returns false for non-provider exceptions`() {
        assertFalse(TrustWeaveException.PluginNotFound("test").isProviderException())
        assertFalse(TrustWeaveException.ValidationFailed("field", "reason").isProviderException())
    }

    @Test
    fun `test isConfigException returns true for config exceptions`() {
        assertTrue(TrustWeaveException.ConfigNotFound("path").isConfigException())
        assertTrue(TrustWeaveException.ConfigReadFailed("path", "reason").isConfigException())
        assertTrue(TrustWeaveException.InvalidConfigFormat(parseError = "error").isConfigException())
    }

    @Test
    fun `test isConfigException returns false for non-config exceptions`() {
        assertFalse(TrustWeaveException.PluginNotFound("test").isConfigException())
        assertFalse(TrustWeaveException.ValidationFailed("field", "reason").isConfigException())
    }

    @Test
    fun `test isJsonException returns true for JSON exceptions`() {
        assertTrue(TrustWeaveException.InvalidJson(parseError = "error").isJsonException())
        assertTrue(TrustWeaveException.JsonEncodeFailed(reason = "error").isJsonException())
    }

    @Test
    fun `test isJsonException returns false for non-JSON exceptions`() {
        assertFalse(TrustWeaveException.PluginNotFound("test").isJsonException())
        assertFalse(TrustWeaveException.ValidationFailed("field", "reason").isJsonException())
    }

    @Test
    fun `test isValidationException returns true for validation exceptions`() {
        assertTrue(TrustWeaveException.ValidationFailed("field", "reason").isValidationException())
    }

    @Test
    fun `test isValidationException returns false for non-validation exceptions`() {
        assertFalse(TrustWeaveException.PluginNotFound("test").isValidationException())
        assertFalse(TrustWeaveException.ConfigNotFound("path").isValidationException())
    }

    @Test
    fun `test isEncodingException returns true for encoding exceptions`() {
        assertTrue(TrustWeaveException.DigestFailed("SHA-256", "reason").isEncodingException())
        assertTrue(TrustWeaveException.EncodeFailed("op", "reason").isEncodingException())
    }

    @Test
    fun `test isEncodingException returns false for non-encoding exceptions`() {
        assertFalse(TrustWeaveException.PluginNotFound("test").isEncodingException())
        assertFalse(TrustWeaveException.ValidationFailed("field", "reason").isEncodingException())
    }

    // ============================================================================
    // Edge Case Tests
    // ============================================================================

    @Test
    fun `test toTrustWeaveException with empty message`() {
        val exception = IllegalArgumentException("")
        val error = exception.toTrustWeaveException()

        assertTrue(error is TrustWeaveException.InvalidOperation)
        assertEquals("INVALID_ARGUMENT", error.code)
        // Empty string is not null, so it uses the empty string (not the default)
        assertEquals("", error.message)
    }

    @Test
    fun `test toTrustWeaveException with Exception subclass`() {
        val exception = Exception("Generic exception")
        val error = exception.toTrustWeaveException()

        assertTrue(error is TrustWeaveException.Unknown)
        assertEquals("UNKNOWN_ERROR", error.code)
        // Message should be "Generic exception" (the original message)
        assertEquals("Generic exception", error.message)
    }

    @Test
    fun `test exception equality and hashCode`() {
        val error1 = TrustWeaveException.PluginNotFound("test", "KMS")
        val error2 = TrustWeaveException.PluginNotFound("test", "KMS")
        val error3 = TrustWeaveException.PluginNotFound("test", "DID")

        assertEquals(error1, error2)
        assertEquals(error1.hashCode(), error2.hashCode())
        assertNotEquals(error1, error3)
    }

    @Test
    fun `test exception toString`() {
        val error = TrustWeaveException.PluginNotFound("test-plugin")
        
        val toString = error.toString()
        assertTrue(toString.contains("PluginNotFound"))
        assertTrue(toString.contains("test-plugin"))
    }

    @Test
    fun `test exception with nested cause chain`() {
        val rootCause = IllegalArgumentException("Root")
        val middleCause = IllegalStateException("Middle", rootCause)
        val topException = RuntimeException("Top", middleCause)
        
        val error = topException.toTrustWeaveException()
        
        assertTrue(error is TrustWeaveException.Unknown)
        assertEquals(topException, error.cause)
        assertEquals(middleCause, error.cause?.cause)
        assertEquals(rootCause, error.cause?.cause?.cause)
    }

    @Test
    fun `test context map with various value types`() {
        val error = TrustWeaveException.Unknown(
            message = "Test",
            context = mapOf(
                "string" to "value",
                "number" to 42,
                "boolean" to true,
                "list" to listOf(1, 2, 3),
                "null" to null
            )
        )

        assertEquals("value", error.context["string"])
        assertEquals(42, error.context["number"])
        assertEquals(true, error.context["boolean"])
        assertEquals(listOf(1, 2, 3), error.context["list"])
        assertNull(error.context["null"])
    }

    @Test
    fun `test InvalidConfigFormat with all optional fields`() {
        val error = TrustWeaveException.InvalidConfigFormat(
            jsonString = "{ test }",
            parseError = "Error",
            field = "plugins"
        )

        assertEquals("{ test }", error.jsonString)
        assertEquals("Error", error.parseError)
        assertEquals("plugins", error.field)
        assertTrue(error.message.contains("plugins"))
        assertTrue(error.message.contains("Error"))
    }
}


