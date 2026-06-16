package org.trustweave.credential.vi.verification

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.trustweave.credential.vi.crypto.Cnf
import org.trustweave.credential.vi.crypto.Es256
import org.trustweave.credential.vi.crypto.ViSdJwt
import org.trustweave.credential.vi.crypto.contentOrNull
import org.trustweave.credential.vi.crypto.sha256B64Url
import org.trustweave.credential.vi.model.Constraint
import org.trustweave.credential.vi.model.MandateMode
import org.trustweave.credential.vi.model.Vct

/**
 * Verifies the Verifiable Intent delegation chain L1 → L2 → L3 (autonomous mode).
 *
 * Ports the security-critical path of `verification/chain.py` for the common single mandate-pair
 * case. Implemented: L1/L2/L3 ES256 signatures, cross-layer `sd_hash` bindings, embedded-JWK key
 * resolution (L1.cnf→L2, L2.mandate.cnf→L3, L3 no-cnf, `kid` match), the L2 reference binding, the
 * L3 pair-identity binding, temporal checks (incl. L3 `exp − iat ≤ 1h`), payment required-fields and
 * the L2↔L3 `payment_instrument` cross-check, and constraint enforcement on the payment side.
 *
 * Documented TODO (return an explicit error rather than silently passing): immediate mode, multi-pair
 * L2, the L3a↔L3b cross-reference when both L3s are presented together, and `card_id` cross-check.
 */
internal object ChainVerifier {

    private const val MAX_L3_LIFETIME_SECONDS = 3600L

    /**
     * @param l2RoutedForPayment the exact L2 selective presentation the L3a `sd_hash` binds to
     *        (required when [l3Payment] is supplied).
     * @param l2RoutedForCheckout likewise for [l3Checkout].
     * @param now verification time (epoch seconds); inject for reproducible tests.
     */
    fun verify(
        l1: ViSdJwt,
        l2: ViSdJwt,
        issuerJwk: JsonObject,
        l3Payment: ViSdJwt? = null,
        l3Checkout: ViSdJwt? = null,
        l2RoutedForPayment: String? = null,
        l2RoutedForCheckout: String? = null,
        now: Long,
        clockSkewSeconds: Long = 300,
        expectedL1Vct: String = Vct.L1_CARD,
        expectedL2Aud: String? = null,
        expectedL2Nonce: String? = null,
        strictness: StrictnessMode = StrictnessMode.PERMISSIVE,
    ): ChainVerificationResult {
        val performed = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        // --- L1: header, signature, vct, temporal ---
        header(l1.header, "L1", Vct.Typ.L1)?.let { return fail(it, performed) }
        if (!Es256.verify(l1.jwt, issuerJwk)) return fail("L1 signature verification failed", performed)
        val l1Vct = l1.payload["vct"]?.contentOrNull()
        if (l1Vct != expectedL1Vct) return fail("L1 vct must be '$expectedL1Vct', got '$l1Vct'", performed)
        sdAlg(l1.payload, "L1")?.let { return fail(it, performed) }
        expired(l1.payload["exp"], now, clockSkewSeconds)?.let { return fail("L1 $it", performed) }
        future(l1.payload["iat"], now, clockSkewSeconds)?.let { return fail("L1 iat $it", performed) }

        // --- L2: user-key signature + sd_hash binding to L1 ---
        val userJwk = Cnf.jwk(l1.payload) ?: return fail("L1 missing cnf.jwk (user public key)", performed)
        if (!Es256.verify(l2.jwt, userJwk)) return fail("L2 signature verification failed (user key mismatch)", performed)
        val l2SdHash = l2.payload["sd_hash"]?.contentOrNull()
            ?: return fail("L2 missing required sd_hash binding to L1", performed)
        if (l2SdHash != sha256B64Url(l1.compact.toByteArray(Charsets.US_ASCII))) {
            return fail("L2 sd_hash does not match L1 serialized form", performed)
        }
        sdAlg(l2.payload, "L2")?.let { return fail(it, performed) }
        future(l2.payload["iat"], now, clockSkewSeconds)?.let { return fail("L2 iat $it", performed) }
        expired(l2.payload["exp"], now, clockSkewSeconds)?.let { return fail("L2 $it", performed) }
        audNonce("l2", l2.payload, expectedL2Aud, expectedL2Nonce, performed, skipped)?.let { return fail(it, performed) }

        // --- Resolve L2, infer mode ---
        val l2Resolved = l2.resolve()
        val resolvedDelegates = l2Resolved["delegate_payload"] as? JsonArray ?: JsonArray(emptyList())
        val mode = inferMode(resolvedDelegates) ?: return fail(
            "L2 contains both open (autonomous) and final (immediate) mandate VCTs", performed,
        )
        val expectedL2Typ = if (mode == MandateMode.AUTONOMOUS) Vct.Typ.L2_AUTONOMOUS else Vct.Typ.L2_IMMEDIATE
        header(l2.header, "L2", expectedL2Typ)?.let { return fail(it, performed) }

        // --- Extract & pair mandates (single-pair scaffold) ---
        val discByHash = l2.discStringByHash()
        val rawDelegates = l2.rawDelegatePayload
        val checkouts = mutableListOf<MandateInfo>()
        val payments = mutableListOf<MandateInfo>()
        val seenRefs = mutableSetOf<String>()
        var duplicateRef = false
        resolvedDelegates.forEachIndexed { i, resolvedItem ->
            val obj = resolvedItem as? JsonObject ?: return@forEachIndexed
            val vct = obj["vct"]?.contentOrNull() ?: return@forEachIndexed
            val refHash = (rawDelegates.getOrNull(i) as? JsonObject)?.get("...")?.contentOrNull()
            if (refHash != null && !seenRefs.add(refHash)) {
                duplicateRef = true
                return@forEachIndexed
            }
            val info = MandateInfo(obj, refHash, refHash?.let { discByHash[it] })
            when (vct) {
                in Vct.CHECKOUT_VCTS -> checkouts += info
                in Vct.PAYMENT_VCTS -> payments += info
            }
        }
        if (duplicateRef) {
            return fail("L2 delegate_payload contains duplicate disclosure reference (mandate smuggling)", performed)
        }
        if (checkouts.size > 1 || payments.size > 1) {
            return fail("Multi-pair L2 is not yet implemented in this scaffold (TODO)", performed)
        }
        val checkout = checkouts.firstOrNull()
        val payment = payments.firstOrNull()
        if (checkout == null && payment == null) {
            return fail("L2 delegate_payload resolved zero mandate disclosures", performed)
        }

        // L1 card_id (SHOULD) must match the authorized payment_instrument.id, when present.
        val cardId = l1.payload["card_id"]?.contentOrNull()
        if (!cardId.isNullOrEmpty()) {
            val piId = (payment?.resolved?.get("payment_instrument") as? JsonObject)?.get("id")?.contentOrNull()
            if (piId != null && piId != cardId) {
                return fail("L1 card_id ($cardId) does not match payment_instrument.id ($piId)", performed)
            }
            performed += "l1_card_id_cross_check"
        } else {
            skipped += "l1_card_id_cross_check"
        }

        // --- Immediate mode: 2-layer, finalized values, no L3 ---
        if (mode == MandateMode.IMMEDIATE) {
            if (l3Payment != null || l3Checkout != null) {
                return fail("Immediate mode does not permit L3 credentials", performed)
            }
            verifyImmediate(checkout, payment, performed)?.let { return fail(it, performed) }
            return ChainVerificationResult(
                valid = true, l1Claims = l1.resolve(), l2Claims = l2Resolved,
                checksPerformed = performed, checksSkipped = skipped,
            )
        }

        // Open-mandate structural requirements
        checkout?.let {
            if (!hasConstraint(it.resolved, Constraint.LineItems.TYPE)) {
                return fail("Open checkout mandate must contain a mandate.checkout.line_items constraint", performed)
            }
            performed += "open_checkout_contains_line_items"
        }
        payment?.let {
            if (!hasConstraint(it.resolved, Constraint.Reference.TYPE)) {
                return fail("Open payment mandate must contain a mandate.payment.reference constraint", performed)
            }
            performed += "open_payment_contains_reference"
        }

        // L2 reference binding (when both mandates present)
        if (checkout != null && payment != null) {
            val discB64 = checkout.discB64
                ?: return fail("L2 checkout disclosure string missing (required for reference binding)", performed)
            val (ok, err) = IntegrityChecker.l2ReferenceBinding(payment.resolved, discB64)
            if (!ok) return fail("L2 reference binding failed: $err", performed)
            performed += "l2_reference_binding"
        }

        // --- Agent delegation key (identical across open mandates) ---
        val openMandates = listOfNotNull(checkout, payment).filter {
            it.resolved["vct"]?.contentOrNull() in setOf(Vct.CHECKOUT_OPEN, Vct.PAYMENT_OPEN)
        }
        val agentJwks = openMandates.mapNotNull { Cnf.jwk(it.resolved) }
        val agentJwk = agentJwks.firstOrNull()
            ?: return fail("L2 mandates missing cnf.jwk for agent delegation", performed)
        if (agentJwks.any { it["x"]?.contentOrNull() != agentJwk["x"]?.contentOrNull() }) {
            return fail("L2 mandate cnf.jwk values must be identical across mandates but differ", performed)
        }
        val agentKid = Cnf.kid(agentJwk)

        // An L3 fulfilment is only meaningful against its authorizing L2 open mandate. A malicious
        // agent can selectively present an L2 that withholds an open mandate (so it resolves to
        // null), which would otherwise silently skip that side's constraint / payee /
        // payment_instrument / pair-identity checks below. Refuse such a chain — fail closed rather
        // than accept an unconstrained L3.
        if (l3Payment != null && payment == null) {
            return fail("L3 payment presented without its authorizing L2 payment mandate", performed)
        }
        if (l3Checkout != null && checkout == null) {
            return fail("L3 checkout presented without its authorizing L2 checkout mandate", performed)
        }

        // --- Verify supplied L3s ---
        var l3PaymentResolved: JsonObject? = null
        var l3CheckoutResolved: JsonObject? = null
        if (l3Payment != null) {
            val routed = l2RoutedForPayment ?: return fail("l2RoutedForPayment required to verify L3a", performed)
            verifyL3(
                l3 = l3Payment, label = "L3a (payment)", agentJwk = agentJwk, agentKid = agentKid,
                routedL2 = routed, expectedPairDisc = payment?.discB64, requiredVct = Vct.PAYMENT_FINAL,
                l2PaymentMandate = payment?.resolved, now = now, skew = clockSkewSeconds, performed = performed,
            )?.let { return fail(it, performed) }
            val resolved = l3Payment.resolve()
            l3PaymentResolved = resolved
            // Constraint enforcement on the payment side
            payment?.let { pm ->
                val constraints = (pm.resolved["constraints"] as? JsonArray)
                    ?.mapNotNull { it as? JsonObject }?.map(Constraint::parse) ?: emptyList()
                val fulfillment = finalMandate(resolved, Vct.PAYMENT_FINAL)
                    ?: return fail("L3a missing final payment mandate", performed)
                val cr = ConstraintChecker.check(
                    constraints, fulfillment, strictness, isOpenMandate = true, disclosuresByHash = discByHash,
                )
                performed += cr.checked.map { "constraint:$it" }
                if (!cr.satisfied) return fail("Constraints not satisfied: ${cr.violations}", performed)
                performed += "constraints_satisfied"
            }
        }
        if (l3Checkout != null) {
            val routed = l2RoutedForCheckout ?: return fail("l2RoutedForCheckout required to verify L3b", performed)
            verifyL3(
                l3 = l3Checkout, label = "L3b (checkout)", agentJwk = agentJwk, agentKid = agentKid,
                routedL2 = routed, expectedPairDisc = checkout?.discB64, requiredVct = Vct.CHECKOUT_FINAL,
                l2PaymentMandate = payment?.resolved, now = now, skew = clockSkewSeconds, performed = performed,
            )?.let { return fail(it, performed) }
            l3CheckoutResolved = l3Checkout.resolve()
        }
        // When both L3s are presented together, they must agree on the cross-reference value.
        if (l3PaymentResolved != null && l3CheckoutResolved != null) {
            val (ok, err) = IntegrityChecker.l3CrossReference(l3PaymentResolved, l3CheckoutResolved)
            if (!ok) return fail("L3 cross-reference check failed: $err", performed)
            performed += "l3_cross_reference"
        }

        return ChainVerificationResult(
            valid = true, l1Claims = l1.resolve(), l2Claims = l2Resolved,
            l3Claims = l3PaymentResolved ?: l3CheckoutResolved,
            checksPerformed = performed, checksSkipped = skipped,
        )
    }

    // ---------------------------------------------------------------------------------------------

    private data class MandateInfo(val resolved: JsonObject, val refHash: String?, val discB64: String?)

    /** Immediate-mode validation: both final mandates, no `cnf`, and the checkout-hash binding. */
    private fun verifyImmediate(checkout: MandateInfo?, payment: MandateInfo?, performed: MutableList<String>): String? {
        if (checkout == null || payment == null) {
            return "Immediate mode requires both checkout and payment mandate disclosures"
        }
        val cm = checkout.resolved
        val pm = payment.resolved
        if (cm.containsKey("cnf") || pm.containsKey("cnf")) {
            return "Immediate mode mandates must not contain cnf (cnf is for autonomous delegation only)"
        }
        for (f in listOf("checkout_jwt", "checkout_hash")) {
            if (cm[f]?.contentOrNull().isNullOrEmpty()) return "Closed checkout mandate missing required field: $f"
        }
        paymentRequiredFields(pm, "Closed payment mandate")?.let { return it }
        val (ok, err) = IntegrityChecker.checkoutHashBinding(cm, pm)
        if (!ok) return "L2 checkout-payment binding failed: $err"
        performed += "l2_checkout_payment_binding"
        return null
    }

    private fun verifyL3(
        l3: ViSdJwt,
        label: String,
        agentJwk: JsonObject,
        agentKid: String?,
        routedL2: String,
        expectedPairDisc: String?,
        requiredVct: String,
        l2PaymentMandate: JsonObject?,
        now: Long,
        skew: Long,
        performed: MutableList<String>,
    ): String? {
        if (l3.payload.containsKey("cnf")) return "$label payload MUST NOT contain cnf claim"
        if (!Es256.verify(l3.jwt, agentJwk)) return "$label signature verification failed (agent key mismatch)"
        header(l3.header, label, Vct.Typ.L3)?.let { return it }
        val sdHash = l3.payload["sd_hash"]?.contentOrNull() ?: return "$label missing required sd_hash binding to L2"
        if (sdHash != sha256B64Url(routedL2.toByteArray(Charsets.US_ASCII))) {
            return "$label sd_hash does not match L2 selective presentation"
        }
        if (expectedPairDisc != null && expectedPairDisc !in routedL2.split("~")) {
            return "$label L2 presentation does not include the mandate-pair disclosure (identity mismatch)"
        }
        performed += "${tag(label)}_identity_binding"
        sdAlg(l3.payload, label)?.let { return it }
        future(l3.payload["iat"], now, skew)?.let { return "$label iat $it" }
        expired(l3.payload["exp"], now, skew)?.let { return "$label $it" }
        val iat = (l3.payload["iat"] as? JsonPrimitive)?.longOrNull
        val exp = (l3.payload["exp"] as? JsonPrimitive)?.longOrNull
        if (iat != null && exp != null && exp - iat > MAX_L3_LIFETIME_SECONDS) {
            return "$label exp MUST NOT exceed 1 hour from iat"
        }
        val headerKid = l3.header["kid"]?.contentOrNull()
        if (headerKid.isNullOrEmpty()) return "$label header missing required kid parameter"
        if (agentKid != null && headerKid != agentKid) {
            return "$label header kid '$headerKid' does not match L2 cnf.jwk.kid '$agentKid'"
        }
        val l3Resolved = l3.resolve()
        val mandate = finalMandate(l3Resolved, requiredVct)
            ?: return "$label missing required Layer 3 mandate disclosure: $requiredVct"
        if (requiredVct == Vct.PAYMENT_FINAL) {
            paymentRequiredFields(mandate, label)?.let { return it }
            paymentInstrumentCrossCheck(mandate, l2PaymentMandate, label)?.let { return it }
        } else {
            for (f in listOf("checkout_jwt", "checkout_hash")) {
                if (mandate[f]?.contentOrNull().isNullOrEmpty()) return "$label checkout mandate missing required field: $f"
            }
        }
        performed += "${tag(label)}_structural_chain"
        return null
    }

    private fun finalMandate(l3Claims: JsonObject, vct: String): JsonObject? =
        (l3Claims["delegate_payload"] as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it["vct"]?.contentOrNull() == vct }

    private fun paymentRequiredFields(m: JsonObject, label: String): String? {
        if (m["transaction_id"]?.contentOrNull().isNullOrEmpty()) return "$label payment mandate missing transaction_id"
        val payee = m["payee"] as? JsonObject ?: return "$label payment mandate missing payee"
        if (payee["name"]?.contentOrNull().isNullOrEmpty()) return "$label payee missing name"
        if (payee["website"]?.contentOrNull().isNullOrEmpty()) return "$label payee missing website"
        val pa = m["payment_amount"] as? JsonObject ?: return "$label missing payment_amount"
        if (pa["currency"]?.contentOrNull().isNullOrEmpty()) return "$label payment_amount missing currency"
        if (pa["amount"] == null) return "$label payment_amount missing amount"
        val pi = m["payment_instrument"] as? JsonObject ?: return "$label missing payment_instrument"
        if (pi["id"]?.contentOrNull().isNullOrEmpty() || pi["type"]?.contentOrNull().isNullOrEmpty()) {
            return "$label payment_instrument requires id and type"
        }
        return null
    }

    private fun paymentInstrumentCrossCheck(l3Mandate: JsonObject, l2Payment: JsonObject?, label: String): String? {
        val l2Pi = (l2Payment?.get("payment_instrument") as? JsonObject) ?: return null
        val l3Pi = l3Mandate["payment_instrument"] as? JsonObject ?: return null
        if (l3Pi["id"]?.contentOrNull() != l2Pi["id"]?.contentOrNull() ||
            l3Pi["type"]?.contentOrNull() != l2Pi["type"]?.contentOrNull()
        ) {
            return "$label payment_instrument does not match L2 authorized value"
        }
        return null
    }

    private fun inferMode(resolvedDelegates: JsonArray): MandateMode? {
        var hasOpen = false
        var hasFinal = false
        for (item in resolvedDelegates) {
            val vct = (item as? JsonObject)?.get("vct")?.contentOrNull() ?: continue
            if (vct == Vct.CHECKOUT_OPEN || vct == Vct.PAYMENT_OPEN) hasOpen = true
            if (vct == Vct.CHECKOUT_FINAL || vct == Vct.PAYMENT_FINAL) hasFinal = true
        }
        if (hasOpen && hasFinal) return null
        return if (hasOpen) MandateMode.AUTONOMOUS else MandateMode.IMMEDIATE
    }

    private fun hasConstraint(mandate: JsonObject, type: String): Boolean =
        (mandate["constraints"] as? JsonArray)?.mapNotNull { it as? JsonObject }
            ?.any { it["type"]?.contentOrNull() == type } ?: false

    private fun header(header: JsonObject, layer: String, expectedTyp: String): String? {
        val alg = header["alg"]?.contentOrNull()
        if (alg != Vct.ALG) return "$layer header alg must be ${Vct.ALG}, got '$alg'"
        val typ = header["typ"]?.contentOrNull()
        if (typ != expectedTyp) return "$layer header typ must be '$expectedTyp', got '$typ'"
        return null
    }

    private fun sdAlg(payload: JsonObject, layer: String): String? {
        val alg = payload["_sd_alg"]?.contentOrNull() ?: return null
        return if (alg != Vct.SD_ALG) "$layer _sd_alg must be ${Vct.SD_ALG}, got '$alg'" else null
    }

    private fun expired(exp: JsonElement?, now: Long, skew: Long): String? {
        val v = (exp as? JsonPrimitive)?.longOrNull ?: return null
        return if (now > v + skew) "expired at $v" else null
    }

    private fun future(iat: JsonElement?, now: Long, skew: Long): String? {
        val v = (iat as? JsonPrimitive)?.longOrNull ?: return null
        return if (v > now + skew) "is in the future: $v" else null
    }

    private fun audNonce(
        layer: String,
        payload: JsonObject,
        expectedAud: String?,
        expectedNonce: String?,
        performed: MutableList<String>,
        skipped: MutableList<String>,
    ): String? {
        val aud = payload["aud"]?.contentOrNull()
        if (expectedAud != null) {
            if (aud != expectedAud) return "$layer aud mismatch: expected '$expectedAud', got '$aud'"
            performed += "${layer}_aud"
        } else if (!aud.isNullOrEmpty()) {
            skipped += "${layer}_aud (no expected value provided)"
        }
        val nonce = payload["nonce"]?.contentOrNull()
        if (expectedNonce != null) {
            if (nonce != expectedNonce) return "$layer nonce mismatch: expected '$expectedNonce', got '$nonce'"
            performed += "${layer}_nonce"
        } else if (!nonce.isNullOrEmpty()) {
            skipped += "${layer}_nonce (no expected value provided)"
        }
        return null
    }

    private fun tag(label: String): String = label.lowercase().replace(" ", "_").replace("(", "").replace(")", "")

    private fun fail(error: String, performed: List<String>): ChainVerificationResult =
        ChainVerificationResult(valid = false, errors = listOf(error), checksPerformed = performed)
}
