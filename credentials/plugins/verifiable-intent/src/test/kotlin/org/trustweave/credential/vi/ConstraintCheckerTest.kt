package org.trustweave.credential.vi

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.trustweave.credential.vi.crypto.Disclosures
import org.trustweave.credential.vi.model.Constraint
import org.trustweave.credential.vi.verification.ConstraintChecker
import org.trustweave.credential.vi.verification.StrictnessMode

/**
 * Guards the `allowed_payees` / `allowed_merchants` enforcement. The allowlist entries are normally
 * carried as SD-references (`{"...": digest}`) whose values are disclosed separately; a verifier
 * must resolve those disclosures and match the fulfillment against them — and, for an open mandate,
 * must NOT silently pass when it cannot evaluate the allowlist (otherwise an agent disables the
 * constraint simply by withholding the allowlist disclosures).
 */
class ConstraintCheckerTest {
    @Test
    fun `empty or invalid allowlists never authorize an open mandate`() {
        val fulfillment = buildJsonObject { put("payee", payee("unexpected")) }
        for (allowed in listOf(emptyList(), listOf(buildJsonObject {}))) {
            val constraint = Constraint.AllowedPayees(buildJsonObject {}, allowed)
            ConstraintChecker
                .check(listOf(constraint), fulfillment, isOpenMandate = true)
                .satisfied
                .shouldBeFalse()
        }
    }

    @Test
    fun `malformed constraint shapes return violations instead of throwing or dropping entries`() {
        val malformed =
            listOf(
                "{}",
                "{\"type\":{}}",
                "{\"type\":false}",
                "{\"type\":\"mandate.payment.allowed_payees\"}",
                "{\"type\":\"mandate.payment.allowed_payees\",\"allowed\":[{},42]}",
                "{\"type\":\"mandate.checkout.allowed_merchants\",\"allowed\":{}}",
                "{\"type\":\"mandate.checkout.line_items\",\"items\":[null]}",
            )
        for (json in malformed) {
            val constraint =
                Constraint.parse(
                    kotlinx.serialization.json.Json
                        .parseToJsonElement(json) as kotlinx.serialization.json.JsonObject,
                )
            ConstraintChecker.check(listOf(constraint), buildJsonObject {}).satisfied.shouldBeFalse()
        }
    }

    private fun payee(id: String) = buildJsonObject { put("id", id) }

    @Test
    fun `SD-ref allowed_payees rejects a payee not in the disclosed allowlist`() {
        val allowed = Disclosures.makeArrayElement(payee("good-merchant"))
        val constraint =
            Constraint.AllowedPayees(
                raw = buildJsonObject {},
                allowed = listOf(buildJsonObject { put("...", allowed.hash) }),
            )
        val fulfillment = buildJsonObject { put("payee", payee("evil-merchant")) }

        val result =
            ConstraintChecker.check(
                constraints = listOf(constraint),
                fulfillment = fulfillment,
                isOpenMandate = true,
                disclosuresByHash = mapOf(allowed.hash to allowed.b64),
            )

        result.satisfied.shouldBeFalse()
    }

    @Test
    fun `SD-ref allowed_payees accepts a payee that matches a disclosed allowlist entry`() {
        val allowed = Disclosures.makeArrayElement(payee("good-merchant"))
        val constraint =
            Constraint.AllowedPayees(
                raw = buildJsonObject {},
                allowed = listOf(buildJsonObject { put("...", allowed.hash) }),
            )
        val fulfillment = buildJsonObject { put("payee", payee("good-merchant")) }

        val result =
            ConstraintChecker.check(
                constraints = listOf(constraint),
                fulfillment = fulfillment,
                isOpenMandate = true,
                disclosuresByHash = mapOf(allowed.hash to allowed.b64),
            )

        result.satisfied.shouldBeTrue()
    }

    @Test
    fun `SD-ref allowed_payees fails closed for an open mandate when entries are not disclosed`() {
        val allowed = Disclosures.makeArrayElement(payee("good-merchant"))
        val constraint =
            Constraint.AllowedPayees(
                raw = buildJsonObject {},
                allowed = listOf(buildJsonObject { put("...", allowed.hash) }),
            )
        val fulfillment = buildJsonObject { put("payee", payee("anything")) }

        // No disclosures supplied: the allowlist cannot be evaluated. An open mandate must not pass.
        val result =
            ConstraintChecker.check(
                constraints = listOf(constraint),
                fulfillment = fulfillment,
                isOpenMandate = true,
                disclosuresByHash = emptyMap(),
            )

        result.satisfied.shouldBeFalse()
    }

    private fun amountRange(build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) =
        Constraint.parse(
            buildJsonObject {
                put("type", "mandate.payment.amount_range")
                put("currency", "USD")
                build()
            },
        )

    private fun paymentOf(amount: Number) =
        buildJsonObject {
            put(
                "payment_amount",
                buildJsonObject {
                    put("amount", amount)
                    put("currency", "USD")
                },
            )
        }

    private fun check(
        constraint: Constraint,
        fulfillment: kotlinx.serialization.json.JsonObject,
    ) = ConstraintChecker.check(
        constraints = listOf(constraint),
        fulfillment = fulfillment,
        isOpenMandate = true,
        disclosuresByHash = emptyMap(),
    )

    @Test
    fun `amount_range fails closed when the declared maximum is not a whole number`() {
        // A max the parser cannot read must never be treated as "no limit". 1000.0 is not an Int,
        // so the ceiling silently disappeared and a 5000 payment was accepted against a 1000 cap.
        val result = check(amountRange { put("max", 1000.0) }, paymentOf(5000))

        result.satisfied.shouldBeFalse()
    }

    @Test
    fun `amount_range fails closed when the declared maximum is not numeric at all`() {
        val result = check(amountRange { put("max", "unlimited") }, paymentOf(5000))

        result.satisfied.shouldBeFalse()
    }

    @Test
    fun `amount_range enforces a maximum larger than Int range`() {
        // 3_000_000_000 exceeds Int.MAX_VALUE; the cap must still bind.
        val result = check(amountRange { put("max", 3_000_000_000L) }, paymentOf(4_000_000_000L))

        result.satisfied.shouldBeFalse()
    }

    @Test
    fun `amount_range accepts an amount within a maximum larger than Int range`() {
        val result = check(amountRange { put("max", 3_000_000_000L) }, paymentOf(2_500_000_000L))

        result.satisfied.shouldBeTrue()
    }

    @Test
    fun `budget fails closed when its declared cap is unreadable`() {
        // Budget is network-enforced downstream, but a cap the verifier cannot read must not be
        // quietly rebuilt as 0 (or as no cap at all) - the constraint is malformed, so reject it.
        val constraint =
            Constraint.parse(
                buildJsonObject {
                    put("type", "mandate.payment.budget")
                    put("currency", "USD")
                    put("max", "lots")
                },
            )

        check(constraint, paymentOf(5000)).satisfied.shouldBeFalse()
    }

    @Test
    fun `amount_range still enforces an ordinary maximum`() {
        check(amountRange { put("max", 40_000) }, paymentOf(40_001)).satisfied.shouldBeFalse()
        check(amountRange { put("max", 40_000) }, paymentOf(40_000)).satisfied.shouldBeTrue()
    }

    // --- line_items must not be reported as enforced while nothing enforces it ---
    //
    // ConstraintCheckResult distinguishes `checked` (evaluated) from `skipped` (not evaluated), and
    // a verifier reads that distinction to decide how much the result is worth. `line_items` bounds
    // WHAT a delegated agent may buy - acceptable items and quantity caps - so claiming it was
    // evaluated when it was not overstates the guarantee in exactly the place an agent would exploit.
    // Until matching is implemented it must behave like any other constraint the verifier cannot
    // evaluate: reported as skipped, and fatal for an open mandate or under STRICT.

    private fun lineItems() =
        Constraint.LineItems(
            raw = buildJsonObject {},
            items = listOf(buildJsonObject { put("id", "sku-1") }),
            matchMode = "minimum",
        )

    @Test
    fun `line_items is not reported as checked while it is unimplemented`() {
        val result = ConstraintChecker.check(listOf(lineItems()), buildJsonObject {})

        result.checked.contains(Constraint.LineItems.TYPE).shouldBeFalse()
        result.skipped.contains(Constraint.LineItems.TYPE).shouldBeTrue()
    }

    @Test
    fun `an open mandate with an unevaluable line_items constraint fails closed`() {
        // An open mandate is one whose bounds are the only thing standing between the agent and
        // unbounded authority. An unevaluable bound there must reject, exactly as an unknown
        // constraint type already does.
        val result =
            ConstraintChecker.check(
                listOf(lineItems()),
                buildJsonObject {},
                isOpenMandate = true,
            )

        result.satisfied.shouldBeFalse()
    }

    @Test
    fun `STRICT mode also rejects an unevaluable line_items constraint`() {
        val result =
            ConstraintChecker.check(
                listOf(lineItems()),
                buildJsonObject {},
                mode = StrictnessMode.STRICT,
            )

        result.satisfied.shouldBeFalse()
    }

    @Test
    fun `a closed mandate under PERMISSIVE still passes, but says line_items was skipped`() {
        // Not every mandate is open; a closed one is bounded by its own contents. Rejecting here
        // would break working deployments for a constraint that was never enforced anyway - the
        // honest outcome is "passed, and here is what I could not evaluate".
        val result = ConstraintChecker.check(listOf(lineItems()), buildJsonObject {})

        result.satisfied.shouldBeTrue()
        result.skipped.contains(Constraint.LineItems.TYPE).shouldBeTrue()
    }
}
