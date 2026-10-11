package org.trustweave.credential.avpmicro.verification

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.trustweave.credential.avpmicro.crypto.Digests
import org.trustweave.credential.avpmicro.crypto.EcdsaJcs2022
import org.trustweave.credential.avpmicro.model.AuthorizationView
import org.trustweave.credential.avpmicro.model.NonPositiveAmountException
import org.trustweave.credential.avpmicro.model.QuoteView
import java.math.BigDecimal
import java.time.Instant

enum class VerificationFailure {
    MALFORMED_REQUEST,
    CREDENTIAL_PROOF_INVALID,
    AUTHORIZATION_PROOF_INVALID,
    CREDENTIAL_EXPIRED,
    CREDENTIAL_REVOKED,
    SUBJECT_MISMATCH,
    AMOUNT_EXCEEDED,
    CURRENCY_NOT_ALLOWED,
    PAYEE_NOT_ALLOWED,
    QUOTE_EXPIRED,
    QUOTE_PROOF_INVALID,
    QUOTE_MISMATCH,
    AMOUNT_MISMATCH,

    /** No trust policy was configured, or it does not vouch for the credential issuer. */
    ISSUER_NOT_TRUSTED,

    /** The credential's `issuer` is not the DID that signed its proof. */
    ISSUER_MISMATCH,

    /** The authorization was not signed by the payer (and credential subject). */
    AUTHORIZATION_SIGNER_MISMATCH,

    /** A proof is not an `assertionMethod` proof. */
    PROOF_PURPOSE_INVALID,

    /** The credential has a `credentialStatus` that could not be checked (no resolver, or it failed). */
    STATUS_UNVERIFIABLE,

    /** The amount is zero or negative. */
    INVALID_AMOUNT,
}

sealed class PaymentVerificationResult {
    data class Valid(
        val view: AuthorizationView,
    ) : PaymentVerificationResult()

    data class Invalid(
        val reason: VerificationFailure,
        val detail: String,
    ) : PaymentVerificationResult()
}

/**
 * Resolves revocation; returns true if revoked. Throwing means "could not tell", which the
 * verifier treats as a refusal ([VerificationFailure.STATUS_UNVERIFIABLE]), never as "not revoked".
 */
fun interface StatusResolver {
    fun isRevoked(credentialStatus: JsonObject): Boolean
}

/**
 * Decides whether a credential issuer is trusted to issue spending authority.
 *
 * A signature only proves the holder of the named key signed; it says nothing about whether that
 * key may grant spending authority. The verifier therefore has no default: with no policy every
 * credential is refused.
 */
fun interface IssuerTrustPolicy {
    fun isTrusted(
        issuerDid: String,
        credentialTypes: List<String>,
    ): Boolean
}

object PaymentVerifier {
    fun verify(
        authorization: JsonObject,
        now: Instant,
        clockSkewSeconds: Long = 300,
        statusResolver: StatusResolver? = null,
        quote: JsonObject? = null,
        amountOverride: BigDecimal? = null,
        issuerTrust: IssuerTrustPolicy? = null,
    ): PaymentVerificationResult {
        // Parsing is eager; a structurally-incomplete request must fail closed, never crash.
        val view =
            try {
                AuthorizationView.from(authorization)
            } catch (e: NonPositiveAmountException) {
                return fail(VerificationFailure.INVALID_AMOUNT, e.message ?: "amount must be greater than zero")
            } catch (e: Exception) {
                return PaymentVerificationResult.Invalid(
                    VerificationFailure.MALFORMED_REQUEST,
                    "malformed payment authorization: ${e.message}",
                )
            }
        val cred = view.embeddedCredential
        val sa = view.spendingAuthority

        if (!EcdsaJcs2022.verify(cred)) {
            return fail(VerificationFailure.CREDENTIAL_PROOF_INVALID, "credential signature invalid")
        }
        if (!EcdsaJcs2022.verify(authorization)) {
            return fail(VerificationFailure.AUTHORIZATION_PROOF_INVALID, "authorization signature invalid")
        }
        if (!isAssertionMethod(cred) || !isAssertionMethod(authorization)) {
            return fail(VerificationFailure.PROOF_PURPOSE_INVALID, "proofPurpose must be assertionMethod")
        }

        // Trust: a valid signature only proves possession of the key named in the proof itself.
        val credentialSigner = proofSigner(cred)
        if (credentialSigner == null || credentialSigner != sa.issuer) {
            return fail(VerificationFailure.ISSUER_MISMATCH, "credential issuer is not the signer of its proof")
        }
        if (issuerTrust == null || !issuerTrust.isTrusted(sa.issuer, sa.types)) {
            return fail(VerificationFailure.ISSUER_NOT_TRUSTED, "credential issuer is not trusted")
        }

        // clockSkewSeconds default (300s / 5 min) is the conventional JWT clock-skew tolerance.
        sa.validFrom?.let {
            if (now.isBefore(it.minusSeconds(clockSkewSeconds))) return fail(VerificationFailure.CREDENTIAL_EXPIRED, "not yet valid")
        }
        sa.validUntil?.let {
            if (now.isAfter(it.plusSeconds(clockSkewSeconds))) return fail(VerificationFailure.CREDENTIAL_EXPIRED, "credential expired")
        }
        val status = sa.credentialStatus
        if (status != null) {
            if (statusResolver == null) {
                return fail(VerificationFailure.STATUS_UNVERIFIABLE, "credential has credentialStatus but no status resolver is configured")
            }
            val revoked =
                try {
                    statusResolver.isRevoked(status)
                } catch (e: Exception) {
                    return fail(VerificationFailure.STATUS_UNVERIFIABLE, "credential status could not be resolved: ${e.message}")
                }
            if (revoked) return fail(VerificationFailure.CREDENTIAL_REVOKED, "credential revoked")
        }
        if (view.payer != sa.subjectId) {
            return fail(VerificationFailure.SUBJECT_MISMATCH, "payer != credential subject")
        }
        // Holder binding: the authorization must be signed by the payer, who is the subject.
        if (proofSigner(authorization) != view.payer) {
            return fail(VerificationFailure.AUTHORIZATION_SIGNER_MISMATCH, "authorization not signed by the payer")
        }
        val amount = amountOverride ?: view.amount
        if (view.amount.signum() <= 0 || amount.signum() <= 0) {
            return fail(VerificationFailure.INVALID_AMOUNT, "amount must be greater than zero")
        }
        if (amount > sa.maxPerTransaction) {
            return fail(VerificationFailure.AMOUNT_EXCEEDED, "amount $amount > cap ${sa.maxPerTransaction}")
        }
        if (view.currency != sa.currency) {
            return fail(VerificationFailure.CURRENCY_NOT_ALLOWED, "currency ${view.currency} != ${sa.currency}")
        }
        sa.allowedPayees?.let { if (view.payee !in it) return fail(VerificationFailure.PAYEE_NOT_ALLOWED, "payee not allowed") }
        view.expires?.let {
            if (now.isAfter(it.plusSeconds(clockSkewSeconds))) return fail(VerificationFailure.QUOTE_EXPIRED, "authorization expired")
        }

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
        val q =
            try {
                QuoteView(quote)
            } catch (e: Exception) {
                return fail(VerificationFailure.QUOTE_MISMATCH, "malformed quote: ${e.message}")
            }
        if (!EcdsaJcs2022.verify(quote)) {
            return fail(VerificationFailure.QUOTE_PROOF_INVALID, "quote signature invalid")
        }
        if (!isAssertionMethod(quote)) {
            return fail(VerificationFailure.PROOF_PURPOSE_INVALID, "quote proofPurpose must be assertionMethod")
        }
        val signer = proofSigner(quote)
        if (signer != q.payee) {
            return fail(VerificationFailure.QUOTE_PROOF_INVALID, "quote not signed by its payee")
        }
        q.expires?.let {
            if (now.isAfter(it.plusSeconds(clockSkewSeconds))) {
                return fail(VerificationFailure.QUOTE_EXPIRED, "quote expired")
            }
        }
        if (view.quoteDigest != Digests.jcsDigest(quote)) {
            return fail(VerificationFailure.QUOTE_MISMATCH, "quoteDigest does not match the supplied quote")
        }
        if (view.requestHash == null || q.requestHash == null) {
            return fail(VerificationFailure.QUOTE_MISMATCH, "missing required requestHash")
        }
        if (view.payee != q.payee || view.requestHash != q.requestHash) {
            return fail(VerificationFailure.QUOTE_MISMATCH, "payee/requestHash differ from quote")
        }
        if (view.amount.compareTo(q.amount) != 0 || view.currency != q.currency) {
            return fail(VerificationFailure.AMOUNT_MISMATCH, "amount/currency differ from quote")
        }
        if (view.settlementMethod != q.settlementMethod || view.settlementTarget != q.settlementTarget) {
            return fail(VerificationFailure.QUOTE_MISMATCH, "settlement routing differs from quote")
        }
        return null
    }

    /** The DID (verificationMethod without its fragment) that signed [document]'s proof. */
    private fun proofSigner(document: JsonObject): String? =
        (document["proof"] as? JsonObject)
            ?.get("verificationMethod")
            ?.jsonPrimitive
            ?.content
            ?.substringBefore('#')

    private fun isAssertionMethod(document: JsonObject): Boolean =
        (document["proof"] as? JsonObject)?.get("proofPurpose")?.jsonPrimitive?.content == "assertionMethod"

    private fun fail(
        r: VerificationFailure,
        d: String,
    ) = PaymentVerificationResult.Invalid(r, d)
}
