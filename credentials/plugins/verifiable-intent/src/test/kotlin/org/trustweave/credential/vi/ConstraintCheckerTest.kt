package org.trustweave.credential.vi

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.trustweave.credential.vi.crypto.Disclosures
import org.trustweave.credential.vi.model.Constraint
import org.trustweave.credential.vi.verification.ConstraintChecker

/**
 * Guards the `allowed_payees` / `allowed_merchants` enforcement. The allowlist entries are normally
 * carried as SD-references (`{"...": digest}`) whose values are disclosed separately; a verifier
 * must resolve those disclosures and match the fulfillment against them — and, for an open mandate,
 * must NOT silently pass when it cannot evaluate the allowlist (otherwise an agent disables the
 * constraint simply by withholding the allowlist disclosures).
 */
class ConstraintCheckerTest {

    private fun payee(id: String) = buildJsonObject { put("id", id) }

    @Test
    fun `SD-ref allowed_payees rejects a payee not in the disclosed allowlist`() {
        val allowed = Disclosures.makeArrayElement(payee("good-merchant"))
        val constraint = Constraint.AllowedPayees(
            raw = buildJsonObject {},
            allowed = listOf(buildJsonObject { put("...", allowed.hash) }),
        )
        val fulfillment = buildJsonObject { put("payee", payee("evil-merchant")) }

        val result = ConstraintChecker.check(
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
        val constraint = Constraint.AllowedPayees(
            raw = buildJsonObject {},
            allowed = listOf(buildJsonObject { put("...", allowed.hash) }),
        )
        val fulfillment = buildJsonObject { put("payee", payee("good-merchant")) }

        val result = ConstraintChecker.check(
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
        val constraint = Constraint.AllowedPayees(
            raw = buildJsonObject {},
            allowed = listOf(buildJsonObject { put("...", allowed.hash) }),
        )
        val fulfillment = buildJsonObject { put("payee", payee("anything")) }

        // No disclosures supplied: the allowlist cannot be evaluated. An open mandate must not pass.
        val result = ConstraintChecker.check(
            constraints = listOf(constraint),
            fulfillment = fulfillment,
            isOpenMandate = true,
            disclosuresByHash = emptyMap(),
        )

        result.satisfied.shouldBeFalse()
    }
}
