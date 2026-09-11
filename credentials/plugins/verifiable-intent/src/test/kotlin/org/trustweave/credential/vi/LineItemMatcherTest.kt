package org.trustweave.credential.vi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import org.trustweave.credential.vi.model.Constraint
import org.trustweave.credential.vi.verification.ConstraintChecker
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LineItemMatcherTest {
    private fun obj(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private fun requirement(
        id: String,
        choices: List<String>,
        quantity: Int,
    ) = """{"id":"$id","quantity":$quantity,"acceptable_items":[${choices.joinToString { """{"id":"$it","title":"Item"}""" }}]}"""

    private fun check(
        requirements: List<String>,
        cart: String,
        mode: String = "minimum",
    ): Boolean {
        val constraint =
            Constraint.parse(
                obj("""{"type":"mandate.checkout.line_items","match_mode":"$mode","items":[${requirements.joinToString()}]}"""),
            )
        return ConstraintChecker.check(listOf(constraint), obj("""{"line_items":$cart}"""), isOpenMandate = true).satisfied
    }

    @Test
    fun `overlapping alternatives cannot reuse a requirement quota`() {
        val requirements = listOf(requirement("one", listOf("a", "b"), 1), requirement("two", listOf("c"), 1))
        assertTrue(check(requirements, """[{"id":"a","quantity":1},{"id":"c","quantity":1}]"""))
        assertFalse(check(requirements, """[{"id":"a","quantity":1},{"id":"b","quantity":1}]"""))
        assertFalse(check(requirements, """[{"id":"a","quantity":1},{"id":"a","quantity":1}]"""))
    }

    @Test
    fun `exact matching allocates positive units to each requirement`() {
        val requirements = listOf(requirement("one", listOf("a", "b"), 1), requirement("two", listOf("a"), 1))
        assertTrue(check(requirements, """[{"id":"a","quantity":1},{"id":"b","quantity":1}]""", "exact"))
        assertFalse(check(requirements, """[{"id":"a","quantity":1}]""", "exact"))
        assertTrue(check(requirements, """[{"id":"a","quantity":1}]"""))
        assertTrue(check(listOf(requirement("wildcard", emptyList(), 1)), """[{"id":"any","quantity":1}]""", "exact"))
    }

    @Test
    fun `invalid shapes quantities and unknown products fail closed`() {
        val requirements = listOf(requirement("one", listOf("a"), 1))
        for (cart in listOf(
            "[]",
            "null",
            "[null]",
            """[{"id":"a","quantity":-1}]""",
            """[{"id":"a","quantity":"1"}]""",
            """[{"id":"a","quantity":true}]""",
            """[{"id":true,"sku":"a","quantity":1}]""",
            """[{"id":"b","quantity":1}]""",
            """[{"id":"a","quantity":9223372036854775807}]""",
        )) {
            assertFalse(check(requirements, cart), cart)
        }
        assertFalse(check(requirements, """[{"id":"a","quantity":1}]""", "unknown"))
        assertFalse(check(listOf(requirement("one", listOf("a"), 0)), """[{"id":"a","quantity":1}]"""))
    }

    @Test
    fun `circulation matches exhaustive small cart allocations`() {
        for (leftMask in 0..3) {
            for (rightMask in 0..3) {
                for (a in 0..3) {
                    for (b in 0..3) {
                        fun allowed(mask: Int) = listOf("a", "b").filterIndexed { index, _ -> mask and (1 shl index) != 0 }
                        val left = allowed(leftMask)
                        val right = allowed(rightMask)
                        for (exact in listOf(false, true)) {
                            val possible =
                                (0..a).any { leftA ->
                                    (0..b).any { leftB ->
                                        val rightA = a - leftA
                                        val rightB = b - leftB
                                        leftA + leftB <= 2 &&
                                            rightA + rightB <= 2 &&
                                            (!exact || (leftA + leftB > 0 && rightA + rightB > 0)) &&
                                            (left.isEmpty() || ((leftA == 0 || "a" in left) && (leftB == 0 || "b" in left))) &&
                                            (right.isEmpty() || ((rightA == 0 || "a" in right) && (rightB == 0 || "b" in right)))
                                    }
                                }
                            val actual =
                                check(
                                    listOf(requirement("left", left, 2), requirement("right", right, 2)),
                                    """[{"id":"a","quantity":$a},{"id":"b","quantity":$b}]""",
                                    if (exact) "exact" else "minimum",
                                )
                            kotlin.test.assertEquals(possible, actual, "masks=$leftMask,$rightMask cart=$a,$b exact=$exact")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `item disclosures must be present and hash bound`() {
        val made =
            org.trustweave.credential.vi.crypto.Disclosures
                .makeArrayElement(obj("""{"id":"a","title":"Item"}"""))
        val constraint =
            Constraint.parse(
                obj(
                    """{"type":"mandate.checkout.line_items",
                        "items":[{"id":"r","quantity":1,"acceptable_items":[{"...":"${made.hash}"}]}]}""",
                ),
            )
        val cart = obj("""{"line_items":[{"id":"a","quantity":1}]}""")
        assertFalse(ConstraintChecker.check(listOf(constraint), cart).satisfied)
        assertTrue(ConstraintChecker.check(listOf(constraint), cart, disclosuresByHash = mapOf(made.hash to made.b64)).satisfied)
        assertFalse(ConstraintChecker.check(listOf(constraint), cart, disclosuresByHash = mapOf(made.hash to "tampered")).satisfied)
    }
}
