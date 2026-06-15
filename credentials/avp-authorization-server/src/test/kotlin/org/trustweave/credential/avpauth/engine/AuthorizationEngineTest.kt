package org.trustweave.credential.avpauth.engine

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthorizationEngineTest {
    private val now = Instant.parse("2026-03-25T21:30:30Z")
    private fun vector(): JsonObject =
        Json.parseToJsonElement(
            this::class.java.getResource("/vectors/02-payment-authorization.json")!!.readText()
        ).jsonObject

    private fun engine() = AuthorizationEngine(clock = { now })

    @Test fun `first presentation is allowed`() = runTest {
        val v = engine().decide(vector())
        assertTrue(v is AuthorizationVerdict.Allow, "expected Allow, got $v")
    }

    @Test fun `replay of the same nonce is rejected`() = runTest {
        val e = engine()
        assertTrue(e.decide(vector()) is AuthorizationVerdict.Allow)
        val second = e.decide(vector())
        assertEquals("NONCE_REUSE", (second as AuthorizationVerdict.Reject).reason)
    }

    @Test fun `concurrent identical requests yield exactly one allow`() = runTest {
        val e = engine()
        val results = coroutineScope {
            (1..8).map { async { e.decide(vector()) } }.awaitAll()
        }
        assertEquals(1, results.count { it is AuthorizationVerdict.Allow })
        assertEquals(7, results.count { it is AuthorizationVerdict.Reject })
    }
}
