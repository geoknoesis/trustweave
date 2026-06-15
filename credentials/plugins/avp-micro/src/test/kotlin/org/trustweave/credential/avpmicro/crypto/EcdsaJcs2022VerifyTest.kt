package org.trustweave.credential.avpmicro.crypto

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.trustweave.credential.avpmicro.Vectors
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EcdsaJcs2022VerifyTest {
    @Test
    fun `verifies a genuine payment authorization`() {
        assertTrue(EcdsaJcs2022.verify(Vectors.paymentAuthorization))
    }

    @Test
    fun `verifies a genuine spending authorization credential`() {
        assertTrue(EcdsaJcs2022.verify(Vectors.spendingAuthorizationCredential))
    }

    @Test
    fun `rejects a tampered document`() {
        val doc = Vectors.paymentAuthorization
        val tampered = JsonObject(doc.toMutableMap().apply { put("amount", JsonPrimitive("999.00")) })
        assertFalse(EcdsaJcs2022.verify(tampered))
    }
}
