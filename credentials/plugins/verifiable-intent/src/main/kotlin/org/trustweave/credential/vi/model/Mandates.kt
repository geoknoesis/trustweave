package org.trustweave.credential.vi.model

import kotlinx.serialization.json.JsonObject

/**
 * Verifiable Intent data model for the three delegation layers.
 *
 * These types document the issued-credential shape and back the (forthcoming) issuance builders in
 * `org.trustweave.credential.vi.issuance`. Verification operates on the parsed SD-JWT directly, so
 * the [org.trustweave.credential.vi.verification.ChainVerifier] does not consume these — they are the
 * authoring-side model. `cnf.jwk` is an EC P-256 JWK as a [JsonObject].
 */

/** Layer 1 — issuer credential binding the user's public key via `cnf.jwk`. */
public data class IssuerCredential(
    val iss: String,
    val sub: String,
    val iat: Long,
    val exp: Long,
    val userCnfJwk: JsonObject,
    val panLastFour: String,
    val scheme: String,
    val cardId: String? = null,
    /** Selectively disclosable. */
    val email: String? = null,
    val aud: String? = null,
    val vct: String = Vct.L1_CARD,
)

/** Layer 2 checkout mandate — open (autonomous, with [agentCnfJwk]+[constraints]) or final. */
public data class CheckoutMandate(
    val vct: String = Vct.CHECKOUT_OPEN,
    val agentCnfJwk: JsonObject? = null,
    val agentCnfKid: String? = null,
    val constraints: List<Constraint> = emptyList(),
    val checkoutJwt: String? = null,
    val checkoutHash: String? = null,
)

/** Layer 2 payment mandate — open (autonomous) or final (immediate). */
public data class PaymentMandate(
    val vct: String = Vct.PAYMENT_OPEN,
    val agentCnfJwk: JsonObject? = null,
    val agentCnfKid: String? = null,
    val constraints: List<Constraint> = emptyList(),
    val paymentInstrument: JsonObject? = null,
    val riskData: JsonObject? = null,
    val payee: JsonObject? = null,
    val currency: String? = null,
    val amount: Int? = null,
    val transactionId: String? = null,
)

/** Layer 3a — final payment mandate the agent sends to the payment network. */
public data class FinalPaymentMandate(
    val transactionId: String,
    val payee: JsonObject,
    val paymentAmount: JsonObject,
    val paymentInstrument: JsonObject,
    val vct: String = Vct.PAYMENT_FINAL,
)

/** Layer 3b — final checkout mandate the agent sends to the merchant. */
public data class FinalCheckoutMandate(
    val checkoutJwt: String,
    val checkoutHash: String,
    val vct: String = Vct.CHECKOUT_FINAL,
)
