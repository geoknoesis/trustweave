package org.trustweave.credential.avpmicro.verification

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.trustweave.credential.avpmicro.AvpMicro
import org.trustweave.credential.avpmicro.Vectors
import org.trustweave.credential.avpmicro.crypto.TestSigning
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

    @Test
    fun `valid authorization with matching quote passes`() {
        val r = AvpMicro.verifyPayment(Vectors.paymentAuthorization, now, quote = Vectors.paymentQuote)
        assertTrue(r is PaymentVerificationResult.Valid, "expected Valid, got $r")
    }

    @Test
    fun `tampered quote fails with QUOTE_PROOF_INVALID`() {
        val tamperedQuote = JsonObject(
            Vectors.paymentQuote.toMutableMap().apply { put("amount", JsonPrimitive("0.002")) }
        )
        val r = AvpMicro.verifyPayment(Vectors.paymentAuthorization, now, quote = tamperedQuote)
        assertEquals(VerificationFailure.QUOTE_PROOF_INVALID, (r as PaymentVerificationResult.Invalid).reason)
    }

    @Test
    fun `no quote supplied still verifies caps only`() {
        val r = AvpMicro.verifyPayment(Vectors.paymentAuthorization, now)
        assertTrue(r is PaymentVerificationResult.Valid)
    }

    // Re-sign a genuine object after mutating one top-level field, using the harness key,
    // so the proof remains valid while the chosen field diverges.
    private fun resign(doc: JsonObject, label: String, key: String, value: String): JsonObject {
        val created = doc.getValue("proof").jsonObject.getValue("created").jsonPrimitive.content
        val mutated = JsonObject(doc.toMutableMap().apply { put(key, JsonPrimitive(value)) })
        return TestSigning.sign(mutated, label, created)
    }

    @Test
    fun `authorization amount over cap fails with AMOUNT_EXCEEDED`() {
        // 0.06 > the credential's 0.05 perTransaction cap; re-signed so the proof is valid.
        val authz = resign(Vectors.paymentAuthorization, "agent-buyer-01", "amount", "0.06")
        val r = AvpMicro.verifyPayment(authz, now)
        assertEquals(VerificationFailure.AMOUNT_EXCEEDED, (r as PaymentVerificationResult.Invalid).reason)
    }

    @Test
    fun `payer not the credential subject fails with SUBJECT_MISMATCH`() {
        val authz = resign(Vectors.paymentAuthorization, "agent-buyer-01", "payer", "did:key:zNotTheSubject")
        val r = AvpMicro.verifyPayment(authz, now)
        assertEquals(VerificationFailure.SUBJECT_MISMATCH, (r as PaymentVerificationResult.Invalid).reason)
    }

    @Test
    fun `authorization amount differs from quote fails with AMOUNT_MISMATCH`() {
        // Re-signed authz with amount 0.002 (<= 0.05 cap, so the cap check passes) but quote says 0.001.
        val authz = resign(Vectors.paymentAuthorization, "agent-buyer-01", "amount", "0.002")
        val r = AvpMicro.verifyPayment(authz, now, quote = Vectors.paymentQuote)
        assertEquals(VerificationFailure.AMOUNT_MISMATCH, (r as PaymentVerificationResult.Invalid).reason)
    }

    @Test
    fun `quote with mismatched digest fails with QUOTE_MISMATCH`() {
        // A genuinely-signed quote whose content (amount) differs -> its digest won't match authz.quoteDigest.
        val quote = resign(Vectors.paymentQuote, "service-tool-api", "amount", "0.002")
        val r = AvpMicro.verifyPayment(Vectors.paymentAuthorization, now, quote = quote)
        assertEquals(VerificationFailure.QUOTE_MISMATCH, (r as PaymentVerificationResult.Invalid).reason)
    }
}
