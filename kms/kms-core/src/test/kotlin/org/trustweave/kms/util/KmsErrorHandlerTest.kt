package org.trustweave.kms.util

import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.GenerateKeyResult
import org.slf4j.Logger
import org.slf4j.Marker
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KmsErrorHandlerTest {
    private val mockLogger = object : Logger {
        override fun getName(): String = "test"
        override fun isTraceEnabled(): Boolean = false
        override fun isTraceEnabled(marker: Marker): Boolean = false
        override fun trace(msg: String) {}
        override fun trace(format: String, arg: Any) {}
        override fun trace(format: String, arg1: Any, arg2: Any) {}
        override fun trace(format: String, vararg arguments: Any) {}
        override fun trace(msg: String, t: Throwable) {}
        override fun trace(marker: Marker, msg: String) {}
        override fun trace(marker: Marker, format: String, arg: Any) {}
        override fun trace(marker: Marker, format: String, arg1: Any, arg2: Any) {}
        override fun trace(marker: Marker, format: String, vararg arguments: Any) {}
        override fun trace(marker: Marker, msg: String, t: Throwable) {}
        override fun isDebugEnabled(): Boolean = false
        override fun isDebugEnabled(marker: Marker): Boolean = false
        override fun debug(msg: String) {}
        override fun debug(format: String, arg: Any) {}
        override fun debug(format: String, arg1: Any, arg2: Any) {}
        override fun debug(format: String, vararg arguments: Any) {}
        override fun debug(msg: String, t: Throwable) {}
        override fun debug(marker: Marker, msg: String) {}
        override fun debug(marker: Marker, format: String, arg: Any) {}
        override fun debug(marker: Marker, format: String, arg1: Any, arg2: Any) {}
        override fun debug(marker: Marker, format: String, vararg arguments: Any) {}
        override fun debug(marker: Marker, msg: String, t: Throwable) {}
        override fun isInfoEnabled(): Boolean = false
        override fun isInfoEnabled(marker: Marker): Boolean = false
        override fun info(msg: String) {}
        override fun info(format: String, arg: Any) {}
        override fun info(format: String, arg1: Any, arg2: Any) {}
        override fun info(format: String, vararg arguments: Any) {}
        override fun info(msg: String, t: Throwable) {}
        override fun info(marker: Marker, msg: String) {}
        override fun info(marker: Marker, format: String, arg: Any) {}
        override fun info(marker: Marker, format: String, arg1: Any, arg2: Any) {}
        override fun info(marker: Marker, format: String, vararg arguments: Any) {}
        override fun info(marker: Marker, msg: String, t: Throwable) {}
        override fun isWarnEnabled(): Boolean = false
        override fun isWarnEnabled(marker: Marker): Boolean = false
        override fun warn(msg: String) {}
        override fun warn(format: String, arg: Any) {}
        override fun warn(format: String, arg1: Any, arg2: Any) {}
        override fun warn(format: String, vararg arguments: Any) {}
        override fun warn(msg: String, t: Throwable) {}
        override fun warn(marker: Marker, msg: String) {}
        override fun warn(marker: Marker, format: String, arg: Any) {}
        override fun warn(marker: Marker, format: String, arg1: Any, arg2: Any) {}
        override fun warn(marker: Marker, format: String, vararg arguments: Any) {}
        override fun warn(marker: Marker, msg: String, t: Throwable) {}
        override fun isErrorEnabled(): Boolean = false
        override fun error(msg: String) {}
        override fun error(format: String, arg: Any) {}
        override fun error(format: String, arg1: Any, arg2: Any) {}
        override fun error(format: String, vararg arguments: Any) {}
        override fun error(msg: String, t: Throwable) {}
        override fun isErrorEnabled(marker: Marker): Boolean = false
        override fun error(marker: Marker, msg: String) {}
        override fun error(marker: Marker, format: String, arg: Any) {}
        override fun error(marker: Marker, format: String, arg1: Any, arg2: Any) {}
        override fun error(marker: Marker, format: String, vararg arguments: Any) {}
        override fun error(marker: Marker, msg: String, t: Throwable) {}
    }

    @Test
    fun `test handleGenericError returns null`() {
        val result = KmsErrorHandler.handleGenericError<String>(
            mockLogger,
            emptyMap(),
            "test-operation",
            Exception("test error")
        )
        assertNull(result, "handleGenericError should return null")
    }

    @Test
    fun `test createErrorContext with all parameters`() {
        val keyId = KeyId("test-key-id")
        val algorithm = Algorithm.Ed25519
        val context = KmsErrorHandler.createErrorContext(
            keyId = keyId,
            algorithm = algorithm,
            operation = "generateKey",
            additional = mapOf("requestId" to "req-123")
        )
        
        assertTrue(context.containsKey("keyId"), "Context should contain keyId")
        assertTrue(context.containsKey("algorithm"), "Context should contain algorithm")
        assertTrue(context.containsKey("operation"), "Context should contain operation")
        assertTrue(context.containsKey("requestId"), "Context should contain additional fields")
        assertTrue(context["keyId"] == keyId.value)
        assertTrue(context["algorithm"] == algorithm.name)
        assertTrue(context["operation"] == "generateKey")
    }

    @Test
    fun `test createErrorContext with minimal parameters`() {
        val context = KmsErrorHandler.createErrorContext()
        assertTrue(context.isEmpty(), "Context with no parameters should be empty")
    }

    @Test
    fun `test createErrorContext with partial parameters`() {
        val keyId = KeyId("test-key-id")
        val context = KmsErrorHandler.createErrorContext(keyId = keyId)
        assertTrue(context.containsKey("keyId"), "Context should contain keyId")
        assertTrue(context.size == 1, "Context should only contain keyId")
    }

    @Test
    fun `test handleGenericException with algorithm`() {
        val algorithm = Algorithm.Ed25519
        val exception = Exception("test error")
        val result = KmsErrorHandler.handleGenericException(
            mockLogger,
            algorithm = algorithm,
            operation = "generateKey",
            e = exception
        )
        
        assertNotNull(result, "Result should not be null when algorithm is provided")
        assertTrue(result is GenerateKeyResult.Failure.Error)
        assertTrue(result.algorithm == algorithm)
        assertTrue(result.reason.contains("generateKey"))
        assertTrue(result.cause == exception)
    }

    @Test
    fun `test handleGenericException without algorithm returns null`() {
        val result = KmsErrorHandler.handleGenericException(
            mockLogger,
            operation = "generateKey",
            e = Exception("test error")
        )
        assertNull(result, "Result should be null when algorithm is not provided")
    }

    @Test
    fun `test handleGenericException with keyId and algorithm`() {
        val keyId = KeyId("test-key-id")
        val algorithm = Algorithm.Ed25519
        val result = KmsErrorHandler.handleGenericException(
            mockLogger,
            keyId = keyId,
            algorithm = algorithm,
            operation = "sign",
            e = Exception("signing failed")
        )
        
        assertNotNull(result)
        assertTrue(result is GenerateKeyResult.Failure.Error)
    }
}

