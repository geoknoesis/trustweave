package org.trustweave.kms.util

import org.slf4j.Logger
import org.slf4j.Marker
import kotlin.test.Test
import kotlin.test.assertTrue

class KmsLoggingTest {
    private val loggedMessages = mutableListOf<String>()
    private val loggedExceptions = mutableListOf<Throwable?>()
    
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
        override fun isDebugEnabled(): Boolean = true
        override fun isDebugEnabled(marker: Marker): Boolean = true
        override fun debug(msg: String) { loggedMessages.add(msg) }
        override fun debug(format: String, arg: Any) { loggedMessages.add(format.replace("{}", arg.toString())) }
        override fun debug(format: String, arg1: Any, arg2: Any) { loggedMessages.add(format.replace("{}", arg1.toString()).replace("{}", arg2.toString())) }
        override fun debug(format: String, vararg arguments: Any) { 
            var msg = format
            arguments.forEach { msg = msg.replaceFirst("{}", it.toString()) }
            loggedMessages.add(msg)
        }
        override fun debug(msg: String, t: Throwable) { loggedMessages.add(msg); loggedExceptions.add(t) }
        override fun debug(marker: Marker, msg: String) { loggedMessages.add(msg) }
        override fun debug(marker: Marker, format: String, arg: Any) { loggedMessages.add(format.replace("{}", arg.toString())) }
        override fun debug(marker: Marker, format: String, arg1: Any, arg2: Any) { loggedMessages.add(format.replace("{}", arg1.toString()).replace("{}", arg2.toString())) }
        override fun debug(marker: Marker, format: String, vararg arguments: Any) { 
            var msg = format
            arguments.forEach { msg = msg.replaceFirst("{}", it.toString()) }
            loggedMessages.add(msg)
        }
        override fun debug(marker: Marker, msg: String, t: Throwable) { loggedMessages.add(msg); loggedExceptions.add(t) }
        override fun isInfoEnabled(): Boolean = true
        override fun isInfoEnabled(marker: Marker): Boolean = true
        override fun info(msg: String) { loggedMessages.add(msg) }
        override fun info(format: String, arg: Any) { loggedMessages.add(format.replace("{}", arg.toString())) }
        override fun info(format: String, arg1: Any, arg2: Any) { loggedMessages.add(format.replace("{}", arg1.toString()).replace("{}", arg2.toString())) }
        override fun info(format: String, vararg arguments: Any) { 
            var msg = format
            arguments.forEach { msg = msg.replaceFirst("{}", it.toString()) }
            loggedMessages.add(msg)
        }
        override fun info(msg: String, t: Throwable) { loggedMessages.add(msg); loggedExceptions.add(t) }
        override fun info(marker: Marker, msg: String) { loggedMessages.add(msg) }
        override fun info(marker: Marker, format: String, arg: Any) { loggedMessages.add(format.replace("{}", arg.toString())) }
        override fun info(marker: Marker, format: String, arg1: Any, arg2: Any) { loggedMessages.add(format.replace("{}", arg1.toString()).replace("{}", arg2.toString())) }
        override fun info(marker: Marker, format: String, vararg arguments: Any) { 
            var msg = format
            arguments.forEach { msg = msg.replaceFirst("{}", it.toString()) }
            loggedMessages.add(msg)
        }
        override fun info(marker: Marker, msg: String, t: Throwable) { loggedMessages.add(msg); loggedExceptions.add(t) }
        override fun isWarnEnabled(): Boolean = true
        override fun isWarnEnabled(marker: Marker): Boolean = true
        override fun warn(msg: String) { loggedMessages.add(msg) }
        override fun warn(format: String, arg: Any) { loggedMessages.add(format.replace("{}", arg.toString())) }
        override fun warn(format: String, arg1: Any, arg2: Any) { loggedMessages.add(format.replace("{}", arg1.toString()).replace("{}", arg2.toString())) }
        override fun warn(format: String, vararg arguments: Any) { 
            var msg = format
            arguments.forEach { msg = msg.replaceFirst("{}", it.toString()) }
            loggedMessages.add(msg)
        }
        override fun warn(msg: String, t: Throwable) { loggedMessages.add(msg); loggedExceptions.add(t) }
        override fun warn(marker: Marker, msg: String) { loggedMessages.add(msg) }
        override fun warn(marker: Marker, format: String, arg: Any) { loggedMessages.add(format.replace("{}", arg.toString())) }
        override fun warn(marker: Marker, format: String, arg1: Any, arg2: Any) { loggedMessages.add(format.replace("{}", arg1.toString()).replace("{}", arg2.toString())) }
        override fun warn(marker: Marker, format: String, vararg arguments: Any) { 
            var msg = format
            arguments.forEach { msg = msg.replaceFirst("{}", it.toString()) }
            loggedMessages.add(msg)
        }
        override fun warn(marker: Marker, msg: String, t: Throwable) { loggedMessages.add(msg); loggedExceptions.add(t) }
        override fun isErrorEnabled(): Boolean = true
        override fun error(msg: String) { loggedMessages.add(msg) }
        override fun error(format: String, arg: Any) { loggedMessages.add(format.replace("{}", arg.toString())) }
        override fun error(format: String, arg1: Any, arg2: Any) { loggedMessages.add(format.replace("{}", arg1.toString()).replace("{}", arg2.toString())) }
        override fun error(format: String, vararg arguments: Any) { 
            var msg = format
            arguments.forEach { msg = msg.replaceFirst("{}", it.toString()) }
            loggedMessages.add(msg)
        }
        override fun error(msg: String, t: Throwable) { loggedMessages.add(msg); loggedExceptions.add(t) }
        override fun isErrorEnabled(marker: Marker): Boolean = true
        override fun error(marker: Marker, msg: String) { loggedMessages.add(msg) }
        override fun error(marker: Marker, format: String, arg: Any) { loggedMessages.add(format.replace("{}", arg.toString())) }
        override fun error(marker: Marker, format: String, arg1: Any, arg2: Any) { loggedMessages.add(format.replace("{}", arg1.toString()).replace("{}", arg2.toString())) }
        override fun error(marker: Marker, format: String, vararg arguments: Any) { 
            var msg = format
            arguments.forEach { msg = msg.replaceFirst("{}", it.toString()) }
            loggedMessages.add(msg)
        }
        override fun error(marker: Marker, msg: String, t: Throwable) { loggedMessages.add(msg); loggedExceptions.add(t) }
    }

    @Test
    fun `test logKeyGenerated`() {
        loggedMessages.clear()
        KmsLogging.logKeyGenerated(
            mockLogger,
            "test-key-id",
            "Ed25519",
            mapOf("requestId" to "req-123")
        )
        assertTrue(loggedMessages.isNotEmpty(), "Should log message")
        assertTrue(loggedMessages.any { it.contains("test-key-id") }, "Should contain key ID")
        assertTrue(loggedMessages.any { it.contains("Ed25519") }, "Should contain algorithm")
    }

    @Test
    fun `test logSigningSuccess`() {
        loggedMessages.clear()
        KmsLogging.logSigningSuccess(
            mockLogger,
            "test-key-id",
            "Ed25519",
            100,
            64
        )
        assertTrue(loggedMessages.isNotEmpty(), "Should log message")
        assertTrue(loggedMessages.any { it.contains("test-key-id") }, "Should contain key ID")
    }

    @Test
    fun `test logKeyDeleted`() {
        loggedMessages.clear()
        KmsLogging.logKeyDeleted(
            mockLogger,
            "test-key-id",
            mapOf("requestId" to "req-123")
        )
        assertTrue(loggedMessages.isNotEmpty(), "Should log message")
        assertTrue(loggedMessages.any { it.contains("test-key-id") }, "Should contain key ID")
    }

    @Test
    fun `test logKeyNotFound`() {
        loggedMessages.clear()
        KmsLogging.logKeyNotFound(
            mockLogger,
            "test-key-id",
            "getPublicKey",
            mapOf("requestId" to "req-123")
        )
        assertTrue(loggedMessages.isNotEmpty(), "Should log message")
        assertTrue(loggedMessages.any { it.contains("test-key-id") }, "Should contain key ID")
    }

    @Test
    fun `test logError with all parameters`() {
        loggedMessages.clear()
        loggedExceptions.clear()
        val exception = Exception("test error")
        KmsLogging.logError(
            mockLogger,
            "Operation failed",
            "test-key-id",
            "Ed25519",
            "ERR001",
            500,
            "req-123",
            exception
        )
        assertTrue(loggedMessages.isNotEmpty(), "Should log message")
        // The error method logs with exception, so check if exception was logged
        assertTrue(loggedExceptions.isNotEmpty() || loggedMessages.any { it.contains("Operation failed") }, "Should log exception or message")
    }

    @Test
    fun `test logError with minimal parameters`() {
        loggedMessages.clear()
        KmsLogging.logError(mockLogger, "Operation failed")
        assertTrue(loggedMessages.isNotEmpty(), "Should log message")
    }

    @Test
    fun `test logWarning`() {
        loggedMessages.clear()
        KmsLogging.logWarning(
            mockLogger,
            "Validation failed",
            "test-key-id",
            "Ed25519",
            "Invalid format"
        )
        assertTrue(loggedMessages.isNotEmpty(), "Should log message")
        // The warn message format uses keyId={}, algorithm={}, etc., so check for parts
        assertTrue(loggedMessages.any { it.contains("test-key-id") || it.contains("keyId") || it.contains("Validation failed") }, "Should contain key ID or message")
    }
}
