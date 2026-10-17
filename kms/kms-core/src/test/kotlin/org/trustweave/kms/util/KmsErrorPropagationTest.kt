package org.trustweave.kms.util

import org.slf4j.Logger
import org.slf4j.helpers.MessageFormatter
import org.slf4j.helpers.NOPLogger
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.GenerateKeyResult
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KmsErrorPropagationTest {
    private val messages = mutableListOf<String>()
    private val causes = mutableListOf<Throwable?>()
    private val logger =
        object : Logger by NOPLogger.NOP_LOGGER {
            override fun error(
                format: String,
                vararg arguments: Any?,
            ) {
                val rendered = MessageFormatter.arrayFormat(format, arguments)
                messages.add(rendered.message)
                causes.add(rendered.throwable)
            }
        }

    private fun handlers(): List<(Exception) -> Any?> =
        listOf(
            { error -> KmsErrorHandler.handleGenericError<Any>(logger, emptyMap(), "sign", error) },
            { error ->
                KmsErrorHandler.handleGenericException(
                    logger,
                    algorithm = Algorithm.Ed25519,
                    operation = "generateKey",
                    e = error,
                )
            },
            { error -> KmsErrorHandler.handleGenericException(logger, operation = "generateKey", e = error) },
        )

    @Test
    fun `cancellation propagates unchanged without logging`() {
        handlers().forEach { handle ->
            val cancellation = CancellationException("cancelled")
            assertSame(cancellation, assertFailsWith<CancellationException> { handle(cancellation) })
        }
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `interruption propagates with the interrupt flag restored without logging`() {
        val previouslyInterrupted = Thread.currentThread().isInterrupted
        try {
            handlers().forEach { handle ->
                Thread.interrupted()
                val interruption = InterruptedException("interrupted")
                assertSame(interruption, assertFailsWith<InterruptedException> { handle(interruption) })
                assertTrue(Thread.currentThread().isInterrupted)
            }
            assertTrue(messages.isEmpty())
        } finally {
            Thread.interrupted()
            if (previouslyInterrupted) Thread.currentThread().interrupt()
        }
    }

    @Test
    fun `generic logging retains supplied context and the original cause`() {
        val error = IllegalStateException("provider unavailable")
        val context = mapOf("requestId" to "request-42")
        assertNull(KmsErrorHandler.handleGenericError<Any>(logger, context, "sign", error))
        assertEquals(listOf("Unexpected error during sign: {requestId=request-42}"), messages)
        assertSame(error, causes.single())
    }

    @Test
    fun `failure conversion retains context and cause when the exception has no message`() {
        val error = Exception()
        val result =
            KmsErrorHandler.handleGenericException(
                logger,
                keyId = KeyId("key-42"),
                algorithm = Algorithm.Ed25519,
                operation = "generateKey",
                e = error,
            )
        val failure = assertIs<GenerateKeyResult.Failure.Error>(result)
        assertEquals("Failed to generateKey: Unknown error", failure.reason)
        assertSame(error, failure.cause)
        assertSame(error, causes.single())
        val message = messages.single()
        assertTrue(message.contains("keyId=key-42"))
        assertTrue(message.contains("algorithm=Ed25519"))
        assertTrue(message.contains("operation=generateKey"))
    }
}
