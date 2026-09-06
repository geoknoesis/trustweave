package org.trustweave.credential.vi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import org.trustweave.credential.vi.model.Constraint
import org.trustweave.credential.vi.verification.ConstraintChecker
import kotlin.test.assertFalse

class ConstraintValidationRegressionTest {
    private fun accepted(
        constraint: String,
        amount: String,
    ): Boolean {
        val c = Constraint.parse(Json.parseToJsonElement(constraint) as JsonObject)
        val f = Json.parseToJsonElement("""{"payment_amount":{"currency":"USD","amount":$amount}}""") as JsonObject
        return ConstraintChecker.check(listOf(c), f, isOpenMandate = true).satisfied
    }

    @Test
    fun `budget must reject a first transaction above its entire cap`() {
        assertFalse(accepted("""{"type":"mandate.payment.budget","currency":"USD","max":10}""", "1000"))
    }

    @Test
    fun `negative payment amounts must be rejected`() {
        assertFalse(accepted("""{"type":"mandate.payment.amount_range","currency":"USD","max":100}""", "-1"))
    }

    @Test
    fun `string encoded payment amounts must be rejected`() {
        assertFalse(accepted("""{"type":"mandate.payment.amount_range","currency":"USD","max":100}""", "\"50\""))
    }

    @Test
    fun `amount range without required currency must be rejected`() {
        assertFalse(accepted("""{"type":"mandate.payment.amount_range","max":100}""", "50"))
    }
}
