package org.trustweave.credential.avpmicro.verification

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.trustweave.credential.avpmicro.crypto.Digests
import org.trustweave.credential.avpmicro.crypto.EcdsaJcs2022
import org.trustweave.credential.avpmicro.model.AuthorizationView
import org.trustweave.credential.avpmicro.model.QuoteView
import java.math.BigDecimal
import java.time.Instant

enum class VerificationFailure {
    MALFORMED_REQUEST,
    CREDENTIAL_PROOF_INVALID, AUTHORIZATION_PROOF_INVALID,
    CREDENTIAL_EXPIRED, CREDENTIAL_REVOKED,
    SUBJECT_MISMATCH, AMOUNT_EXCEEDED, CURRENCY_NOT_ALLOWED, PAYEE_NOT_ALLOWED, QUOTE_EXPIRED,
    QUOTE_PROOF_INVALID, QUOTE_MISMATCH, AMOUNT_MISMATCH,
}

sealed class PaymentVerificationResult {
    data class Valid(val view: AuthorizationView) : PaymentVerificationResult()
    data class Invalid(val reason: VerificationFailure, val detail: String) : PaymentVerificationResult()
}

/** Optional hook to resolve revocation; returns true if revoked. */
fun interface StatusResolver { fun isRevoked(credentialStatus: JsonObject): Boolean }

object PaymentVerifier {
    fun verify(
        authorization: JsonObject,
        now: Instant,
        clockSkewSeconds: Long = 300,
        statusResolver: StatusResolver? = null,
        quote: JsonObject? = null,
        amountOverride: BigDecimal? = null,
    ): PaymentVerificationResult {
        // Parsing is eager; a structurally-incomplete request must fail closed, never crash.
        val view = try {
            AuthorizationView.from(authorization)
        } catch (e: Exception) {
            return PaymentVerificationResult.Invalid(
                VerificationFailure.MALFORMED_REQUEST,
                "malformed payment authorization: ${e.message}",
            )
        }
        val cred = view.embeddedCredential
        val sa = view.spendingAuthority

        if (!EcdsaJcs2022.verify(cred))
            return fail(VerificationFailure.CREDENTIAL_PROOF_INVALID, "credential signature invalid")
        if (!EcdsaJcs2022.verify(authorization))
            return fail(VerificationFailure.AUTHORIZATION_PROOF_INVALID, "authorization signature invalid")
        // clockSkewSeconds default (300s / 5 min) is the conventional JWT clock-skew tolerance.
        sa.validFrom?.let { if (now.isBefore(it.minusSeconds(clockSkewSeconds))) return fail(VerificationFailure.CREDENTIAL_EXPIRED, "not yet valid") }
        sa.validUntil?.let { if (now.isAfter(it.plusSeconds(clockSkewSeconds))) return fail(VerificationFailure.CREDENTIAL_EXPIRED, "credential expired") }
        val status = sa.credentialStatus
        if (statusResolver != null && status != null && statusResolver.isRevoked(status))
            return fail(VerificationFailure.CREDENTIAL_REVOKED, "credential revoked")
        if (view.payer != sa.subjectId)
            return fail(VerificationFailure.SUBJECT_MISMATCH, "payer != credential subject")
        val amount = amountOverride ?: view.amount
        if (amount > sa.maxPerTransaction)
            return fail(VerificationFailure.AMOUNT_EXCEEDED, "amount $amount > cap ${sa.maxPerTransaction}")
        if (view.currency != sa.currency)
            return fail(VerificationFailure.CURRENCY_NOT_ALLOWED, "currency ${view.currency} != ${sa.currency}")
        sa.allowedPayees?.let { if (view.payee !in it) return fail(VerificationFailure.PAYEE_NOT_ALLOWED, "payee not allowed") }
        view.expires?.let { if (now.isAfter(it.plusSeconds(clockSkewSeconds))) return fail(VerificationFailure.QUOTE_EXPIRED, "authorization expired") }

        if (quote != null) {
            val bindingFailure = verifyQuoteBinding(view, quote, now, clockSkewSeconds)
            if (bindingFailure != null) return bindingFailure
        }

        return PaymentVerificationResult.Valid(view)
    }

    private fun verifyQuoteBinding(
        view: AuthorizationView,
        quote: JsonObject,
        now: Instant,
        clockSkewSeconds: Long,
    ): PaymentVerificationResult? {
        val q = try { QuoteView(quote) } catch (e: Exception) {
            return fail(VerificationFailure.QUOTE_MISMATCH, "malformed quote: ${e.message}")
        }
        if (!EcdsaJcs2022.verify(quote))
            return fail(VerificationFailure.QUOTE_PROOF_INVALID, "quote signature invalid")
        val signer = quote["proof"]?.jsonObject?.get("verificationMethod")?.jsonPrimitive?.content?.substringBefore('#')
        if (signer != q.payee)
            return fail(VerificationFailure.QUOTE_PROOF_INVALID, "quote not signed by its payee")
        q.expires?.let {
            if (now.isAfter(it.plusSeconds(clockSkewSeconds)))
                return fail(VerificationFailure.QUOTE_EXPIRED, "quote expired")
        }
        if (view.quoteDigest != Digests.jcsDigest(quote))
            return fail(VerificationFailure.QUOTE_MISMATCH, "quoteDigest does not match the supplied quote")
        if (view.payee != q.payee || view.requestHash != q.requestHash)
            return fail(VerificationFailure.QUOTE_MISMATCH, "payee/requestHash differ from quote")
        if (view.amount.compareTo(q.amount) != 0 || view.currency != q.currency)
            return fail(VerificationFailure.AMOUNT_MISMATCH, "amount/currency differ from quote")
        if (view.settlementMethod != q.settlementMethod || view.settlementTarget != q.settlementTarget)
            return fail(VerificationFailure.QUOTE_MISMATCH, "settlement routing differs from quote")
        return null
    }

    private fun fail(r: VerificationFailure, d: String) = PaymentVerificationResult.Invalid(r, d)
}
