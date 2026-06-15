package org.trustweave.credential.avpmicro

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleSmokeTest {
    @Test
    fun `module loads`() {
        assertEquals("ecdsa-jcs-2022", AvpMicro.CRYPTOSUITE)
    }

    @Test
    fun `vectors load`() {
        assertEquals(
            "PaymentAuthorization",
            (Vectors.paymentAuthorization["type"] as JsonPrimitive).content,
        )
    }
}
