package org.trustweave.credential.avpauth.state

import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NonPositiveAmountStoreTest {
    private val at = Instant.parse("2026-03-25T21:30:30Z")

    private fun request(
        amount: String,
        nonce: String,
    ) = AdmissionRequest(
        credentialId = "urn:vc:1",
        nonce = nonce,
        authorizationId = "urn:authz:$nonce",
        payer = "did:key:zPayer",
        amount = BigDecimal(amount),
        dailyLimit = BigDecimal("1.00"),
        at = at,
        retainUntil = at.plusSeconds(600),
    )

    @Test
    fun `zero and negative amounts are refused and record nothing`() =
        runTest {
            val store = InMemoryAuthorizationStore()
            for ((i, amount) in listOf("0", "-0.5", "-100").withIndex()) {
                val admission = store.admit(request(amount, "n$i"))
                assertEquals(StatefulRejection.INVALID_AMOUNT, (admission as Admission.Refused).rejection, "amount $amount")
            }
            assertEquals(InMemoryAuthorizationStore.RetainedRecords(0, 0, 0), store.retained())
        }

    @Test
    fun `a negative amount cannot be used to buy back daily headroom`() =
        runTest {
            val store = InMemoryAuthorizationStore()
            assertTrue(store.admit(request("0.9", "a")) is Admission.Admitted)
            store.admit(request("-0.9", "b"))
            val over = store.admit(request("0.5", "c"))
            assertEquals(StatefulRejection.DAILY_LIMIT_EXCEEDED, (over as Admission.Refused).rejection)
        }

    @Test
    fun `the postgres store refuses a non-positive amount before touching the database`() =
        runTest {
            val store = PostgresAuthorizationStore(source = failingSource())
            val admission = store.admit(request("-1", "x"))
            assertEquals(StatefulRejection.INVALID_AMOUNT, (admission as Admission.Refused).rejection)
        }

    private fun failingSource() =
        java.lang.reflect.Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(javax.sql.DataSource::class.java),
        ) { _, _, _ -> error("the database must not be reached") } as javax.sql.DataSource
}
