package org.trustweave.credential.avpmicro

import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleSmokeTest {
    @Test
    fun `module loads`() {
        assertEquals("ecdsa-jcs-2022", AvpMicro.CRYPTOSUITE)
    }

    @org.junit.jupiter.api.Test
    fun `vectors load`() {
        kotlin.test.assertEquals(
            "PaymentAuthorization",
            (org.trustweave.credential.avpmicro.Vectors.paymentAuthorization["type"]
                as kotlinx.serialization.json.JsonPrimitive).content,
        )
    }
}
