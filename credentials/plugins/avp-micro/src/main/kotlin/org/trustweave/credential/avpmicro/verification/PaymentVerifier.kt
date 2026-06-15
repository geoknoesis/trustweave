package org.trustweave.credential.avpmicro.verification

import kotlinx.serialization.json.JsonObject
import org.trustweave.credential.avpmicro.crypto.EcdsaJcs2022
import org.trustweave.credential.avpmicro.model.AuthorizationView
import java.math.BigDecimal
import java.time.Instant

enum class VerificationFailure {
    CREDENTIAL_PROOF_INVALID, AUTHORIZATION_PROOF_INVALID,
    CREDENTIAL_EXPIRED, CREDENTIAL_REVOKED,
    SUBJECT_MISMATCH, AMOUNT_EXCEEDED, CURRENCY_NOT_ALLOWED, PAYEE_NOT_ALLOWED, QUOTE_EXPIRED,
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
        amountOverride: BigDecimal? = null,
    ): PaymentVerificationResult {
        val view = AuthorizationView.from(authorization)
        val cred = view.embeddedCredential
        val sa = view.spendingAuthority

        if (!EcdsaJcs2022.verify(cred))
            return fail(VerificationFailure.CREDENTIAL_PROOF_INVALID, "credential signature invalid")
        if (!EcdsaJcs2022.verify(authorization))
            return fail(VerificationFailure.AUTHORIZATION_PROOF_INVALID, "authorization signature invalid")
        val skew = clockSkewSeconds
        sa.validFrom?.let { if (now.isBefore(it.minusSeconds(skew))) return fail(VerificationFailure.CREDENTIAL_EXPIRED, "not yet valid") }
        sa.validUntil?.let { if (now.isAfter(it.plusSeconds(skew))) return fail(VerificationFailure.CREDENTIAL_EXPIRED, "credential expired") }
        if (statusResolver != null && sa.credentialStatus != null && statusResolver.isRevoked(sa.credentialStatus!!))
            return fail(VerificationFailure.CREDENTIAL_REVOKED, "credential revoked")
        if (view.payer != sa.subjectId)
            return fail(VerificationFailure.SUBJECT_MISMATCH, "payer != credential subject")
        val amount = amountOverride ?: view.amount
        if (amount > sa.maxPerTransaction)
            return fail(VerificationFailure.AMOUNT_EXCEEDED, "amount $amount > cap ${sa.maxPerTransaction}")
        if (view.currency != sa.currency)
            return fail(VerificationFailure.CURRENCY_NOT_ALLOWED, "currency ${view.currency} != ${sa.currency}")
        sa.allowedPayees?.let { if (view.payee !in it) return fail(VerificationFailure.PAYEE_NOT_ALLOWED, "payee not allowed") }
        view.expires?.let { if (now.isAfter(it.plusSeconds(skew))) return fail(VerificationFailure.QUOTE_EXPIRED, "authorization expired") }

        return PaymentVerificationResult.Valid(view)
    }

    private fun fail(r: VerificationFailure, d: String) = PaymentVerificationResult.Invalid(r, d)
}
