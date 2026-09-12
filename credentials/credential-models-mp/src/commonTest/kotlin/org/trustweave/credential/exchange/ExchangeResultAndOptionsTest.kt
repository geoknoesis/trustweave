package org.trustweave.credential.exchange

import kotlinx.serialization.json.JsonPrimitive
import org.trustweave.credential.exchange.options.ExchangeOptions
import org.trustweave.credential.exchange.result.ExchangeResult
import org.trustweave.credential.exchange.result.flatMap
import org.trustweave.credential.exchange.result.fold
import org.trustweave.credential.exchange.result.getOrNull
import org.trustweave.credential.exchange.result.getOrThrow
import org.trustweave.credential.exchange.result.map
import org.trustweave.credential.exchange.result.onFailure
import org.trustweave.credential.exchange.result.onSuccess
import org.trustweave.credential.exchange.result.recover
import org.trustweave.credential.exchange.result.recoverCatching
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [ExchangeResult]'s combinators decide whether a protocol exchange is treated as having produced
 * a value, so one that maps over a failure — or that lets an exception escape a recovery — turns a
 * handled protocol error into an unhandled one.
 */
class ExchangeResultAndOptionsTest {
    private val success: ExchangeResult<String> = ExchangeResult.Success("value")

    private fun failure(): ExchangeResult.Failure =
        ExchangeResult.Failure.NetworkError(reason = "connection refused", errors = listOf("connection refused"))

    @Test
    fun `onSuccess and onFailure each fire for exactly one side and return the receiver`() {
        var seen: String? = null
        assertSame(success, success.onSuccess { seen = it })
        assertEquals("value", seen)

        var caught: ExchangeResult.Failure? = null
        val failed = failure()
        assertSame(failed, failed.onFailure { caught = it })
        assertSame(failed, caught)

        seen = null
        caught = null
        failed.onSuccess { seen = "wrong" }
        success.onFailure { caught = it }
        assertNull(seen)
        assertNull(caught)
    }

    @Test
    fun `getOrThrow returns the value and otherwise reports every recorded error`() {
        assertEquals("value", success.getOrThrow())
        val thrown =
            assertFailsWith<IllegalStateException> {
                ExchangeResult.Failure
                    .InvalidRequest(field = "id", reason = "bad", errors = listOf("first", "second"))
                    .getOrThrow()
            }
        assertTrue("first; second" in (thrown.message ?: ""), thrown.message ?: "")
    }

    @Test
    fun `getOrNull never throws`() {
        assertEquals("value", success.getOrNull())
        assertNull(failure().getOrNull())
    }

    @Test
    fun `map changes the value type on success and passes a failure through untouched`() {
        assertEquals(ExchangeResult.Success(5), success.map { value -> value.length })
        val failed: ExchangeResult<String> = failure()
        assertSame<ExchangeResult<*>>(failed, failed.map<String, Int> { value -> value.length })
    }

    @Test
    fun `flatMap chains and short-circuits on the first failure`() {
        assertEquals(
            ExchangeResult.Success(5),
            success.flatMap { value -> ExchangeResult.Success(value.length) },
        )

        val failed: ExchangeResult<String> = failure()
        assertSame<ExchangeResult<*>>(
            failed,
            failed.flatMap<String, Int> { value -> ExchangeResult.Success(value.length) },
        )

        // A failure produced by the transform is the result, not a success wrapping it.
        val inner = ExchangeResult.Failure.MessageNotFound(messageId = "m-1")
        assertSame<ExchangeResult<*>>(inner, success.flatMap<String, Int> { inner })
    }

    @Test
    fun `fold picks exactly one branch`() {
        assertEquals("ok", success.fold(onFailure = { "failed" }, onSuccess = { "ok" }))
        assertEquals("failed", failure().fold(onFailure = { "failed" }, onSuccess = { "ok" }))
    }

    @Test
    fun `recover only rewrites the failures its predicate selects`() {
        assertEquals(ExchangeResult.Success("recovered"), failure().recover({ true }, { "recovered" }))

        val declined = failure()
        assertSame(declined, declined.recover({ false }, { "recovered" }))

        // A success is never offered to the predicate.
        assertSame(success, success.recover({ error("must not be consulted") }, { _ -> "recovered" }))
    }

    @Test
    fun `recoverCatching turns an exception in the recovery into a failure rather than letting it escape`() {
        val recovered = failure().recoverCatching { throw IllegalStateException("recovery blew up") }
        val unknown = recovered as ExchangeResult.Failure.Unknown
        assertTrue("recovery blew up" in (unknown.reason ?: ""), unknown.reason ?: "")

        assertEquals(ExchangeResult.Success("alt"), failure().recoverCatching { ExchangeResult.Success("alt") })
        assertSame(success, success.recoverCatching { ExchangeResult.Success("alt") })
    }

    // -- ExchangeOptions -------------------------------------------------------------------

    @Test
    fun `the builder accumulates every field it is given`() {
        val options =
            ExchangeOptions
                .Builder()
                .timeoutMillis(1_500)
                .requiresAck(true)
                .threadId("thread-1")
                .addMetadata("k", JsonPrimitive("v"))
                .build()
        assertEquals(1_500, options.timeoutMillis)
        assertTrue(options.requiresAck)
        assertEquals("thread-1", options.threadId)
        assertEquals(JsonPrimitive("v"), options.metadata["k"])
    }

    @Test
    fun `timeoutSeconds is expressed in the same units as timeoutMillis`() {
        val seconds = ExchangeOptions.Builder().timeoutSeconds(2).build()
        val millis = ExchangeOptions.Builder().timeoutMillis(2_000).build()
        assertEquals(millis.timeoutMillis, seconds.timeoutMillis)
    }

    @Test
    fun `an unconfigured builder produces the documented defaults`() {
        val options = ExchangeOptions.Builder().build()
        assertNull(options.timeoutMillis)
        assertTrue(!options.requiresAck)
        assertNull(options.threadId)
        assertTrue(options.metadata.isEmpty())
    }

    @Test
    fun `the builder does not alias its metadata map into the built options`() {
        val builder = ExchangeOptions.Builder().addMetadata("first", JsonPrimitive(1))
        val built = builder.build()
        builder.addMetadata("second", JsonPrimitive(2))
        // A later builder call must not retroactively change something already handed out.
        assertEquals(setOf("first"), built.metadata.keys)
    }
}
