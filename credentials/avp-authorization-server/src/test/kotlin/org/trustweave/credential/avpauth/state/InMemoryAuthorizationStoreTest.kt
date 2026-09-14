package org.trustweave.credential.avpauth.state

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryAuthorizationStoreTest {
    private val now = Instant.parse("2026-03-25T21:30:30Z")

    private fun request(
        nonce: String = "n-1",
        authorizationId: String = "auth-1",
        credentialId: String = "cred-1",
        payer: String = "did:example:payer",
        amount: String = "1.00",
        dailyLimit: String? = "5.00",
        at: Instant = now,
        retainUntil: Instant = now.plus(Duration.ofMinutes(5)),
    ) = AdmissionRequest(
        credentialId = credentialId,
        nonce = nonce,
        authorizationId = authorizationId,
        payer = payer,
        amount = BigDecimal(amount),
        dailyLimit = dailyLimit?.let(::BigDecimal),
        at = at,
        retainUntil = retainUntil,
    )

    private fun refusal(admission: Admission): StatefulRejection {
        assertTrue(admission is Admission.Refused, "expected a refusal, got $admission")
        return admission.rejection
    }

    @Test
    fun `a nonce is admitted once`() =
        runTest {
            val store = InMemoryAuthorizationStore()
            assertEquals(Admission.Admitted, store.admit(request()))
            assertEquals(StatefulRejection.NONCE_REUSE, refusal(store.admit(request())))
        }

    @Test
    fun `the same nonce under a different credential is a different record`() =
        runTest {
            val store = InMemoryAuthorizationStore()
            assertEquals(Admission.Admitted, store.admit(request()))
            assertEquals(
                Admission.Admitted,
                store.admit(request(credentialId = "cred-2", authorizationId = "auth-2")),
            )
        }

    @Test
    fun `composed keys cannot collide by concatenation`() =
        runTest {
            // "a" + "bc" and "ab" + "c" must not be the same record.
            val store = InMemoryAuthorizationStore()
            assertEquals(Admission.Admitted, store.admit(request(credentialId = "a", nonce = "bc")))
            assertEquals(
                Admission.Admitted,
                store.admit(request(credentialId = "ab", nonce = "c", authorizationId = "auth-2")),
            )
        }

    @Test
    fun `an authorization id is consumed once`() =
        runTest {
            val store = InMemoryAuthorizationStore()
            assertEquals(Admission.Admitted, store.admit(request()))
            assertEquals(
                StatefulRejection.DOUBLE_SPEND,
                refusal(store.admit(request(nonce = "n-2"))),
            )
        }

    @Test
    fun `daily spend accumulates and the limit refuses the amount that would cross it`() =
        runTest {
            val store = InMemoryAuthorizationStore()
            repeat(5) { attempt ->
                assertEquals(
                    Admission.Admitted,
                    store.admit(request(nonce = "n-$attempt", authorizationId = "auth-$attempt")),
                    "the ${attempt + 1}th unit of a 5.00 limit must be admitted",
                )
            }
            assertEquals(
                StatefulRejection.DAILY_LIMIT_EXCEEDED,
                refusal(store.admit(request(nonce = "n-x", authorizationId = "auth-x"))),
            )
        }

    @Test
    fun `a refusal records nothing`() =
        runTest {
            val store = InMemoryAuthorizationStore()
            // Spend the whole limit in one go, then be refused for a second authorization.
            assertEquals(Admission.Admitted, store.admit(request(amount = "5.00")))
            val refused = request(nonce = "n-2", authorizationId = "auth-2", amount = "1.00")
            assertEquals(StatefulRejection.DAILY_LIMIT_EXCEEDED, refusal(store.admit(refused)))
            // The refused nonce was never presented successfully, so it is still unconsumed.
            assertEquals(1, store.retained().nonces, "a refused authorization must not record its nonce")
        }

    @Test
    fun `spend rolls over at the UTC day boundary`() =
        runTest {
            val store = InMemoryAuthorizationStore()
            val tomorrow = Instant.parse("2026-03-26T00:00:01Z")
            assertEquals(Admission.Admitted, store.admit(request(amount = "5.00")))
            assertEquals(
                Admission.Admitted,
                store.admit(
                    request(
                        nonce = "n-2",
                        authorizationId = "auth-2",
                        amount = "5.00",
                        at = tomorrow,
                        retainUntil = tomorrow.plus(Duration.ofMinutes(5)),
                    ),
                ),
                "yesterday's spend must not count against today's limit",
            )
        }

    @Test
    fun `concurrent identical admissions yield exactly one`() =
        runTest {
            val store = InMemoryAuthorizationStore()
            val outcomes =
                coroutineScope {
                    (1..16).map { async { store.admit(request()) } }.awaitAll()
                }
            assertEquals(1, outcomes.count { it is Admission.Admitted })
            assertEquals(15, outcomes.count { it is Admission.Refused })
        }

    @Test
    fun `an authorization with no expiry bound is refused rather than half-remembered`() =
        runTest {
            val store = InMemoryAuthorizationStore(retentionCeiling = Duration.ofDays(1))
            assertEquals(
                StatefulRejection.UNBOUNDED_LIFETIME,
                refusal(store.admit(request(retainUntil = Instant.MAX))),
            )
        }

    @Test
    fun `expired records are swept and the store does not grow without bound`() =
        runTest {
            val store = InMemoryAuthorizationStore()
            repeat(50) { attempt ->
                store.admit(request(nonce = "n-$attempt", authorizationId = "auth-$attempt", dailyLimit = null))
            }
            assertEquals(50, store.retained().nonces)
            val purged = store.purgeExpired(now.plus(Duration.ofHours(1)))
            assertTrue(purged >= 100, "both nonce and consumption records must be swept, got $purged")
            assertEquals(0, store.retained().nonces)
            assertEquals(0, store.retained().consumptions)
        }

    @Test
    fun `at capacity the store refuses rather than forgetting a live record`() =
        runTest {
            val store = InMemoryAuthorizationStore(maxEntries = 2)
            assertEquals(Admission.Admitted, store.admit(request(nonce = "n-1", authorizationId = "auth-1", dailyLimit = null)))
            assertEquals(Admission.Admitted, store.admit(request(nonce = "n-2", authorizationId = "auth-2", dailyLimit = null)))
            assertEquals(
                StatefulRejection.STORE_UNAVAILABLE,
                refusal(store.admit(request(nonce = "n-3", authorizationId = "auth-3", dailyLimit = null))),
            )
            // The records that filled it are still there: nothing was evicted to make room.
            assertEquals(
                StatefulRejection.NONCE_REUSE,
                refusal(store.admit(request(nonce = "n-1", authorizationId = "auth-9", dailyLimit = null))),
            )
        }

    @Test
    fun `capacity is reclaimed once the records behind it expire`() =
        runTest {
            val store = InMemoryAuthorizationStore(maxEntries = 2)
            assertEquals(Admission.Admitted, store.admit(request(nonce = "n-1", authorizationId = "auth-1", dailyLimit = null)))
            assertEquals(Admission.Admitted, store.admit(request(nonce = "n-2", authorizationId = "auth-2", dailyLimit = null)))
            val later = now.plus(Duration.ofHours(1))
            assertEquals(
                Admission.Admitted,
                store.admit(
                    request(
                        nonce = "n-3",
                        authorizationId = "auth-3",
                        dailyLimit = null,
                        at = later,
                        retainUntil = later.plus(Duration.ofMinutes(5)),
                    ),
                ),
                "records past their retention must make room for a live one",
            )
        }
}
