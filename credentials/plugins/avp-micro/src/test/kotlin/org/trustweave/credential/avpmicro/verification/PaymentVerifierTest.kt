package org.trustweave.credential.avpmicro.verification

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.trustweave.credential.avpmicro.AvpMicro
import org.trustweave.credential.avpmicro.Vectors
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaymentVerifierTest {
    private val now = Instant.parse("2026-03-25T21:30:30Z")

    private fun mutate(doc: JsonObject, key: String, value: String): JsonObject =
        JsonObject(doc.toMutableMap().apply { put(key, JsonPrimitive(value)) })

    @Test
    fun `valid authorization passes`() {
        val r = AvpMicro.verifyPayment(Vectors.paymentAuthorization, now)
        assertTrue(r is PaymentVerificationResult.Valid, "expected Valid, got $r")
    }

    @Test
    fun `amount over per-transaction cap fails with AMOUNT_EXCEEDED`() {
        val r = AvpMicro.checkConstraints(Vectors.paymentAuthorization, now, amountOverride = java.math.BigDecimal("0.06"))
        assertEquals(VerificationFailure.AMOUNT_EXCEEDED, (r as PaymentVerificationResult.Invalid).reason)
    }

    @Test
    fun `expired authorization fails with QUOTE_EXPIRED`() {
        // `expires` is 21:31:02Z; default skew is 300s (window to 21:36:02Z) -> pick a later `now`.
        val late = Instant.parse("2026-03-25T21:40:00Z")
        val r = AvpMicro.checkConstraints(Vectors.paymentAuthorization, late)
        assertEquals(VerificationFailure.QUOTE_EXPIRED, (r as PaymentVerificationResult.Invalid).reason)
    }

    @Test
    fun `bad signature fails with AUTHORIZATION_PROOF_INVALID`() {
        val tampered = mutate(Vectors.paymentAuthorization, "amount", "0.002")
        val r = AvpMicro.verifyPayment(tampered, now)
        assertEquals(VerificationFailure.AUTHORIZATION_PROOF_INVALID, (r as PaymentVerificationResult.Invalid).reason)
    }

    @Test
    fun `malformed authorization fails with MALFORMED_REQUEST`() {
        val r = AvpMicro.verifyPayment(JsonObject(emptyMap()), now)
        assertEquals(VerificationFailure.MALFORMED_REQUEST, (r as PaymentVerificationResult.Invalid).reason)
    }
}
