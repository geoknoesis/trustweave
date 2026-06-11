package org.trustweave.credential.vi.model

/**
 * Verifiable Intent credential type (`vct`) identifiers and JOSE `typ` header values.
 *
 * Mirrors the Mastercard/Google Verifiable Intent draft v0.1 credential-format spec. The L1 type is
 * the Mastercard reference profile; the mandate types (`mandate.{checkout,payment}.{open.,}1`) are
 * generic and shared across L2/L3. See `agent-intent/verifiable-intent` `spec/credential-format.md`.
 */
public object Vct {
    /** Layer 1 issuer credential — Mastercard reference profile (configurable per deployment). */
    public const val L1_CARD: String = "https://credentials.mastercard.com/card"

    /** L2 autonomous open checkout mandate (carries `cnf` + `constraints`). */
    public const val CHECKOUT_OPEN: String = "mandate.checkout.open.1"

    /** L2 autonomous open payment mandate (carries `cnf` + `constraints`). */
    public const val PAYMENT_OPEN: String = "mandate.payment.open.1"

    /** Final checkout mandate — L2 immediate, and L3b. No `cnf`/`constraints`. */
    public const val CHECKOUT_FINAL: String = "mandate.checkout.1"

    /** Final payment mandate — L2 immediate, and L3a. No `cnf`/`constraints`. */
    public const val PAYMENT_FINAL: String = "mandate.payment.1"

    public val CHECKOUT_VCTS: Set<String> = setOf(CHECKOUT_OPEN, CHECKOUT_FINAL)
    public val PAYMENT_VCTS: Set<String> = setOf(PAYMENT_OPEN, PAYMENT_FINAL)

    /** JOSE `typ` header values per layer/mode. */
    public object Typ {
        public const val L1: String = "sd+jwt"
        public const val L2_IMMEDIATE: String = "kb-sd-jwt"
        public const val L2_AUTONOMOUS: String = "kb-sd-jwt+kb"
        public const val L3: String = "kb-sd-jwt"
    }

    /** The only permitted signature algorithm across all layers. */
    public const val ALG: String = "ES256"

    /** The only permitted selective-disclosure hash algorithm. */
    public const val SD_ALG: String = "sha-256"
}
