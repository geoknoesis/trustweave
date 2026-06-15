package org.trustweave.credential.avpmicro.model

import org.trustweave.credential.avpmicro.Vectors
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class PolicyViewsTest {
    @Test
    fun `reads the spending authority from an embedded credential`() {
        val auth = AuthorizationView.from(Vectors.paymentAuthorization)
        val sa = auth.spendingAuthority
        assertEquals("USD", sa.currency)
        assertEquals(BigDecimal("0.05"), sa.maxPerTransaction)
        assertEquals(BigDecimal("5.00"), sa.dailyLimit)
        assertEquals("did:key:zDnaew8NDU8VgvxWpWWxBeLWaVbGNEuXYyRFk2uLMjCdhxkSU", sa.subjectId)
        assertEquals(listOf("did:key:zDnaenNXPt8JM5YYhrjp23T2ZsgGjyEhVVSC7dhFjbQwdrxEN"), sa.allowedPayees)
    }

    @Test
    fun `reads the authorization fields`() {
        val auth = AuthorizationView.from(Vectors.paymentAuthorization)
        assertEquals("did:key:zDnaew8NDU8VgvxWpWWxBeLWaVbGNEuXYyRFk2uLMjCdhxkSU", auth.payer)
        assertEquals("did:key:zDnaenNXPt8JM5YYhrjp23T2ZsgGjyEhVVSC7dhFjbQwdrxEN", auth.payee)
        assertEquals(BigDecimal("0.001"), auth.amount)
        assertEquals("USD", auth.currency)
        assertEquals("n-39102", auth.nonce)
    }
}
