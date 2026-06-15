package org.trustweave.credential.avpauth

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.trustweave.credential.avpauth.engine.AuthorizationEngine
import org.trustweave.credential.avpauth.engine.AuthorizationVerdict
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParityTest {
    private val now = Instant.parse("2026-03-25T21:30:30Z")
    private fun vector() =
        Json.parseToJsonElement(
            this::class.java.getResource("/vectors/02-payment-authorization.json")!!.readText()
        ).jsonObject

    @Test fun `genuine vector allowed once then single-use rejected`() = runTest {
        val e = AuthorizationEngine(clock = { now })
        assertTrue(e.decide(vector()) is AuthorizationVerdict.Allow)
        val again = e.decide(vector())
        assertEquals("NONCE_REUSE", (again as AuthorizationVerdict.Reject).reason)
    }
}
