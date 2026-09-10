package org.trustweave.credential.vi

import kotlinx.serialization.json.JsonObject
import org.trustweave.credential.vi.crypto.ViSdJwt
import org.trustweave.credential.vi.verification.ChainVerificationResult
import org.trustweave.credential.vi.verification.ChainVerifier
import org.trustweave.credential.vi.verification.StrictnessMode

/**
 * Public facade for Verifiable Intent verification — the entry point a Verifier Gateway (payment
 * network or merchant) calls to validate an agent's delegation chain.
 *
 * Issuance (minting L1 and signing L2/L3 via [org.trustweave.credential.vi.crypto.KmsEs256Signer])
 * lives in `org.trustweave.credential.vi.issuance`.
 */
public object VerifiableIntent {
    /**
     * Verifies a VI delegation chain (mode inferred from the L2 mandate `vct`) from compact SD-JWT
     * strings. For immediate mode pass only [l1] + [l2]; for autonomous mode add the L3(s) and the
     * routed L2 presentation(s).
     *
     * @param issuerJwk the issuer's EC P-256 public key (JWK) used to verify L1.
     * @param l2RoutedForPayment / [l2RoutedForCheckout] the exact L2 selective presentations each L3
     *        recipient received — required to verify the corresponding L3 `sd_hash`.
     * @param now trusted verification time as epoch seconds; defaults to the host clock.
     * @param requireReplayProtection requires verifier-supplied audience and nonce expectations for L2 and each presented L3.
     * Set false only for offline auditing, never for authorizing a live request. The caller must
     * issue fresh nonces and atomically consume them; equality checking is not a replay cache.
     * @param allowMissingTemporalClaims offline compatibility policy for absent L1/L2 timestamps.
     * L3 always requires integer iat/exp and a lifetime of at most one hour.
     */
    public fun verifyChain(
        l1: String,
        l2: String,
        issuerJwk: JsonObject,
        l3Payment: String? = null,
        l3Checkout: String? = null,
        l2RoutedForPayment: String? = null,
        l2RoutedForCheckout: String? = null,
        now: Long = System.currentTimeMillis() / 1000,
        clockSkewSeconds: Long = 300,
        expectedL2Aud: String? = null,
        expectedL2Nonce: String? = null,
        strictness: StrictnessMode = StrictnessMode.PERMISSIVE,
        requireReplayProtection: Boolean = true,
        allowMissingTemporalClaims: Boolean = false,
        expectedL3PaymentAud: String? = null,
        expectedL3PaymentNonce: String? = null,
        expectedL3CheckoutAud: String? = null,
        expectedL3CheckoutNonce: String? = null,
    ): ChainVerificationResult =
        ChainVerifier.verify(
            l1 = ViSdJwt.parse(l1),
            l2 = ViSdJwt.parse(l2),
            issuerJwk = issuerJwk,
            l3Payment = l3Payment?.let { ViSdJwt.parse(it) },
            l3Checkout = l3Checkout?.let { ViSdJwt.parse(it) },
            l2RoutedForPayment = l2RoutedForPayment,
            l2RoutedForCheckout = l2RoutedForCheckout,
            now = now,
            clockSkewSeconds = clockSkewSeconds,
            expectedL2Aud = expectedL2Aud,
            expectedL2Nonce = expectedL2Nonce,
            strictness = strictness,
            requireReplayProtection = requireReplayProtection,
            allowMissingTemporalClaims = allowMissingTemporalClaims,
            expectedL3PaymentAud = expectedL3PaymentAud,
            expectedL3PaymentNonce = expectedL3PaymentNonce,
            expectedL3CheckoutAud = expectedL3CheckoutAud,
            expectedL3CheckoutNonce = expectedL3CheckoutNonce,
        )

    /**
     * Verifies with a verifier-provisioned merchant key, identity and checkout challenge.
     * The original verifyChain API remains unchanged and rejects autonomous checkout without this policy.
     * Cart constraints use the authenticated merchant JWT cart, never independent agent-supplied claims.
     */
    public fun verifyChainWithCheckout(
        checkoutTrust: org.trustweave.credential.vi.verification.CheckoutTrust,
        l1: String,
        l2: String,
        issuerJwk: JsonObject,
        l3Payment: String? = null,
        l3Checkout: String? = null,
        l2RoutedForPayment: String? = null,
        l2RoutedForCheckout: String? = null,
        now: Long = System.currentTimeMillis() / 1000,
        clockSkewSeconds: Long = 300,
        expectedL2Aud: String? = null,
        expectedL2Nonce: String? = null,
        strictness: StrictnessMode = StrictnessMode.PERMISSIVE,
        requireReplayProtection: Boolean = true,
        allowMissingTemporalClaims: Boolean = false,
        expectedL3PaymentAud: String? = null,
        expectedL3PaymentNonce: String? = null,
        expectedL3CheckoutAud: String? = null,
        expectedL3CheckoutNonce: String? = null,
    ): ChainVerificationResult =
        ChainVerifier.verify(
            checkoutTrust = checkoutTrust,
            l1 = ViSdJwt.parse(l1),
            l2 = ViSdJwt.parse(l2),
            issuerJwk = issuerJwk,
            l3Payment = l3Payment?.let { ViSdJwt.parse(it) },
            l3Checkout = l3Checkout?.let { ViSdJwt.parse(it) },
            l2RoutedForPayment = l2RoutedForPayment,
            l2RoutedForCheckout = l2RoutedForCheckout,
            now = now,
            clockSkewSeconds = clockSkewSeconds,
            expectedL2Aud = expectedL2Aud,
            expectedL2Nonce = expectedL2Nonce,
            strictness = strictness,
            requireReplayProtection = requireReplayProtection,
            allowMissingTemporalClaims = allowMissingTemporalClaims,
            expectedL3PaymentAud = expectedL3PaymentAud,
            expectedL3PaymentNonce = expectedL3PaymentNonce,
            expectedL3CheckoutAud = expectedL3CheckoutAud,
            expectedL3CheckoutNonce = expectedL3CheckoutNonce,
        )

    /**
     * Verifies a payment chain and atomically reserves its cumulative budget and one-use challenge.
     * Uses a shared PostgreSQL ledger. Rejected chains do not consume budget. A successful result
     * is a conservative reservation, not proof of payment; downstream execution must be idempotent.
     * Supports one payment budget with per-payment minimum and bounded recurrence profiles.
     */
    public fun verifyAndReserveBudget(
        budgetLedger: org.trustweave.credential.vi.verification.PostgresIntentLedger,
        checkoutTrust: org.trustweave.credential.vi.verification.CheckoutTrust? = null,
        l1: String,
        l2: String,
        issuerJwk: JsonObject,
        l3Payment: String? = null,
        l3Checkout: String? = null,
        l2RoutedForPayment: String? = null,
        l2RoutedForCheckout: String? = null,
        now: Long = System.currentTimeMillis() / 1000,
        clockSkewSeconds: Long = 300,
        expectedL2Aud: String? = null,
        expectedL2Nonce: String? = null,
        strictness: StrictnessMode = StrictnessMode.PERMISSIVE,
        requireReplayProtection: Boolean = true,
        allowMissingTemporalClaims: Boolean = false,
        expectedL3PaymentAud: String? = null,
        expectedL3PaymentNonce: String? = null,
        expectedL3CheckoutAud: String? = null,
        expectedL3CheckoutNonce: String? = null,
    ): ChainVerificationResult =
        ChainVerifier.verify(
            budgetLedger = budgetLedger,
            checkoutTrust = checkoutTrust,
            l1 = ViSdJwt.parse(l1),
            l2 = ViSdJwt.parse(l2),
            issuerJwk = issuerJwk,
            l3Payment = l3Payment?.let { ViSdJwt.parse(it) },
            l3Checkout = l3Checkout?.let { ViSdJwt.parse(it) },
            l2RoutedForPayment = l2RoutedForPayment,
            l2RoutedForCheckout = l2RoutedForCheckout,
            now = now,
            clockSkewSeconds = clockSkewSeconds,
            expectedL2Aud = expectedL2Aud,
            expectedL2Nonce = expectedL2Nonce,
            strictness = strictness,
            requireReplayProtection = requireReplayProtection,
            allowMissingTemporalClaims = allowMissingTemporalClaims,
            expectedL3PaymentAud = expectedL3PaymentAud,
            expectedL3PaymentNonce = expectedL3PaymentNonce,
            expectedL3CheckoutAud = expectedL3CheckoutAud,
            expectedL3CheckoutNonce = expectedL3CheckoutNonce,
        )
}
