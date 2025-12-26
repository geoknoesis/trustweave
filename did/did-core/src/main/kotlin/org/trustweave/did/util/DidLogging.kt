package org.trustweave.did.util

/**
 * Logging utilities for DID operations.
 *
 * Provides a simple logging interface that uses SLF4J when available,
 * or falls back to silent logging when SLF4J is not present.
 *
 * This allows the did-core module to log errors without requiring
 * a logging implementation at compile time.
 */
internal object DidLogging {
    /**
     * Gets a logger instance for the given class.
     * Returns a no-op logger if SLF4J is not available.
     */
    fun getLogger(clazz: Class<*>): Logger {
        return try {
            val loggerFactory = Class.forName("org.slf4j.LoggerFactory")
            val getLoggerMethod = loggerFactory.getMethod("getLogger", Class::class.java)
            val slf4jLogger = getLoggerMethod.invoke(null, clazz)
            Slf4jLoggerAdapter(slf4jLogger)
        } catch (e: ClassNotFoundException) {
            // SLF4J not available - use no-op logger
            NoOpLogger
        } catch (e: Exception) {
            // Any other error - use no-op logger
            NoOpLogger
        }
    }

    /**
     * Simple logger interface.
     */
    interface Logger {
        fun error(message: String, throwable: Throwable? = null)
        fun warn(message: String, throwable: Throwable? = null)
        fun info(message: String)
        fun debug(message: String)
    }

    /**
     * No-op logger implementation for when SLF4J is not available.
     */
    private object NoOpLogger : Logger {
        override fun error(message: String, throwable: Throwable?) {
            // Silent - no logging implementation available
        }

        override fun warn(message: String, throwable: Throwable?) {
            // Silent
        }

        override fun info(message: String) {
            // Silent
        }

        override fun debug(message: String) {
            // Silent
        }
    }

    /**
     * SLF4J logger adapter.
     */
    private class Slf4jLoggerAdapter(private val slf4jLogger: Any) : Logger {
        private val errorMethod = slf4jLogger.javaClass.getMethod("error", String::class.java, Throwable::class.java)
        private val warnMethod = slf4jLogger.javaClass.getMethod("warn", String::class.java, Throwable::class.java)
        private val infoMethod = slf4jLogger.javaClass.getMethod("info", String::class.java)
        private val debugMethod = slf4jLogger.javaClass.getMethod("debug", String::class.java)

        override fun error(message: String, throwable: Throwable?) {
            try {
                errorMethod.invoke(slf4jLogger, message, throwable)
            } catch (e: Exception) {
                // Fallback to no-op if reflection fails
            }
        }

        override fun warn(message: String, throwable: Throwable?) {
            try {
                warnMethod.invoke(slf4jLogger, message, throwable)
            } catch (e: Exception) {
                // Fallback to no-op if reflection fails
            }
        }

        override fun info(message: String) {
            try {
                infoMethod.invoke(slf4jLogger, message)
            } catch (e: Exception) {
                // Fallback to no-op if reflection fails
            }
        }

        override fun debug(message: String) {
            try {
                debugMethod.invoke(slf4jLogger, message)
            } catch (e: Exception) {
                // Fallback to no-op if reflection fails
            }
        }
    }
}

