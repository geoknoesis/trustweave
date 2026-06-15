package org.trustweave.credential.avpmicro.crypto

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
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
}
