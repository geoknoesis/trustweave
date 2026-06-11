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
 * lives in `org.trustweave.credential.vi.issuance` and is forthcoming.
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
     * @param now verification time as epoch seconds.
     */
    public fun verifyChain(
        l1: String,
        l2: String,
        issuerJwk: JsonObject,
        l3Payment: String? = null,
        l3Checkout: String? = null,
        l2RoutedForPayment: String? = null,
        l2RoutedForCheckout: String? = null,
        now: Long,
        clockSkewSeconds: Long = 300,
        expectedL2Aud: String? = null,
        expectedL2Nonce: String? = null,
        strictness: StrictnessMode = StrictnessMode.PERMISSIVE,
    ): ChainVerificationResult = ChainVerifier.verify(
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
    )
}
