package org.trustweave.credential.avpmicro

import kotlinx.serialization.json.JsonObject
import org.trustweave.credential.avpmicro.verification.PaymentVerificationResult
import org.trustweave.credential.avpmicro.verification.PaymentVerifier
import org.trustweave.credential.avpmicro.verification.StatusResolver
import java.math.BigDecimal
import java.time.Instant

/** AVP-Micro stateless verification facade. */
object AvpMicro {
    internal const val CRYPTOSUITE = "ecdsa-jcs-2022"

    /** Verify proofs + spending constraints of a self-contained PaymentAuthorization. */
    fun verifyPayment(
        authorization: JsonObject,
        now: Instant,
        clockSkewSeconds: Long = 300,
        statusResolver: StatusResolver? = null,
        quote: JsonObject? = null,
    ): PaymentVerificationResult =
        PaymentVerifier.verify(authorization, now, clockSkewSeconds, statusResolver, quote = quote)

    /** Test/use hook: run the checklist with an injected amount (keeps the genuine signature valid). */
    fun checkConstraints(
        authorization: JsonObject,
        now: Instant,
        amountOverride: BigDecimal? = null,
    ): PaymentVerificationResult =
        PaymentVerifier.verify(authorization, now, amountOverride = amountOverride)
}
