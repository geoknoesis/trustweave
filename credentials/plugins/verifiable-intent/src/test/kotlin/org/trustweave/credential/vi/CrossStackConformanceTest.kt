package org.trustweave.credential.vi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.trustweave.credential.vi.verification.CheckoutTrust

class CrossStackConformanceTest {
    @Test
    fun `Python issued immediate positive and adversarial vectors agree`() {
        val text = checkNotNull(javaClass.getResource("/vi_python_cross_stack.json")).readText()
        val fixture = Json.parseToJsonElement(text).jsonObject
        assertEquals("356c29635f1c44df7de02edb58699ca9f29bece6", fixture["reference_revision"]!!.jsonPrimitive.content)
        for (element in fixture["cases"]!!.jsonArray) {
            val case = element.jsonObject
            val result =
                VerifiableIntent.verifyChain(
                    l1 = case["l1"]!!.jsonPrimitive.content,
                    l2 = case["l2"]!!.jsonPrimitive.content,
                    issuerJwk = case["issuerJwk"]!!.jsonObject,
                    now = case["now"]!!.jsonPrimitive.long,
                    expectedL2Aud = case["aud"]!!.jsonPrimitive.content,
                    expectedL2Nonce = case["nonce"]!!.jsonPrimitive.content,
                )
            assertEquals(case["expected"]!!.jsonPrimitive.boolean, result.valid, "${case["name"]}: ${result.errors}")
        }
    }

    @TestFactory
    fun `Python autonomous and authenticated checkout vectors`(): List<DynamicTest> {
        val fixture = Json.parseToJsonElement(checkNotNull(javaClass.getResource("/vi_python_autonomous.json")).readText()).jsonObject
        assertEquals("356c29635f1c44df7de02edb58699ca9f29bece6", fixture["reference_revision"]!!.jsonPrimitive.content)
        val cases = fixture["cases"]!!.jsonArray
        assertEquals(20, cases.size)
        return cases.map { element ->
            val case = element.jsonObject
            DynamicTest.dynamicTest(case["name"]!!.jsonPrimitive.content) {
                fun text(key: String): String? = case[key]?.jsonPrimitive?.content
                val trust = case["checkoutTrust"] as? JsonObject
                val result =
                    if (trust == null) {
                        VerifiableIntent.verifyChain(
                            l1 = text("l1")!!,
                            l2 = text("l2")!!,
                            issuerJwk = case["issuerJwk"]!!.jsonObject,
                            now = case["now"]!!.jsonPrimitive.long,
                            expectedL2Aud = text("aud"),
                            expectedL2Nonce = text("nonce"),
                            l3Payment = text("l3Payment"),
                            l2RoutedForPayment = text("routedL2"),
                            expectedL3PaymentAud = text("paymentAud"),
                            expectedL3PaymentNonce = text("paymentNonce"),
                        )
                    } else {
                        VerifiableIntent.verifyChainWithCheckout(
                            checkoutTrust =
                                CheckoutTrust(
                                    trust["issuer"]!!.jsonPrimitive.content,
                                    trust["audience"]!!.jsonPrimitive.content,
                                    trust["nonce"]!!.jsonPrimitive.content,
                                    trust["jwk"]!!.jsonObject,
                                    trust["merchant"]!!.jsonObject,
                                ),
                            l1 = text("l1")!!,
                            l2 = text("l2")!!,
                            issuerJwk = case["issuerJwk"]!!.jsonObject,
                            now = case["now"]!!.jsonPrimitive.long,
                            expectedL2Aud = text("aud"),
                            expectedL2Nonce = text("nonce"),
                            l3Payment = text("l3Payment"),
                            l2RoutedForPayment = text("routedL2"),
                            expectedL3PaymentAud = text("paymentAud"),
                            expectedL3PaymentNonce = text("paymentNonce"),
                            l3Checkout = text("l3Checkout"),
                            l2RoutedForCheckout = text("routedCheckout"),
                            expectedL3CheckoutAud = text("checkoutAud"),
                            expectedL3CheckoutNonce = text("checkoutNonce"),
                        )
                    }
                assertEquals(case["expected"]!!.jsonPrimitive.boolean, result.valid, "${case["name"]}: ${result.errors}")
                text("expectedError")?.let { expected ->
                    assertTrue(result.errors.any { expected in it }, "Expected $expected but got ${result.errors}")
                }
            }
        }
    }
}
