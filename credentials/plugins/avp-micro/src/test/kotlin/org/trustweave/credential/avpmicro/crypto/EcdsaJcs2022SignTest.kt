package org.trustweave.credential.avpmicro.crypto

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.trustweave.credential.avpmicro.Vectors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EcdsaJcs2022SignTest {
    @Test
    fun `sign then verify round-trips`() {
        val doc = JsonObject(
            mapOf(
                "@context" to JsonPrimitive("https://example.org/ctx"),
                "hello" to JsonPrimitive("world"),
            )
        )
        val signed = TestSigning.sign(doc, "service-tool-api", "2026-01-01T00:00:00Z")
        assertTrue(EcdsaJcs2022.verify(signed))
    }

    @Test
    fun `reproduces the harness quote signature byte-for-byte`() {
        val unsigned = JsonObject(Vectors.paymentQuote.filterKeys { it != "proof" })
        val signed = TestSigning.sign(unsigned, "service-tool-api", "2026-03-25T21:30:00Z")
        val expected = Vectors.paymentQuote.getValue("proof").jsonObject.getValue("proofValue").jsonPrimitive.content
        val actual = signed.getValue("proof").jsonObject.getValue("proofValue").jsonPrimitive.content
        assertEquals(expected, actual)
    }
}
