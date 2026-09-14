package org.trustweave.credential.avpauth.state

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The properties the in-memory store cannot have: shared across instances, surviving a restart.
 *
 * Each assertion here is one half of the finding this store exists to close — two engines against
 * one database must enforce one set of limits, and a restarted instance must not forget.
 */
class PostgresAuthorizationStoreTest {
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

    private fun dataSource(container: PostgreSQLContainer<Nothing>): DataSource =
        PGSimpleDataSource().apply {
            setURL(container.jdbcUrl)
            user = container.username
            password = container.password
        }

    @Test
    fun `two instances against one database enforce one set of limits and survive a restart`() =
        runBlocking<Unit> {
            PostgreSQLContainer<Nothing>("postgres:16-alpine").use { container ->
                container.start()
                val source = dataSource(container)
                PostgresAuthorizationStore(source).initializeSchema()

                // Two independent store instances, as two replicas would be.
                val first = PostgresAuthorizationStore(source)
                val second = PostgresAuthorizationStore(source)

                assertEquals(Admission.Admitted, first.admit(request()))
                assertEquals(
                    StatefulRejection.NONCE_REUSE,
                    refusal(second.admit(request())),
                    "the second replica must see the first replica's nonce",
                )
                assertEquals(
                    StatefulRejection.DOUBLE_SPEND,
                    refusal(second.admit(request(nonce = "n-2"))),
                    "the second replica must see the first replica's consumption",
                )

                // A fresh instance is exactly what a restarted process is.
                val restarted = PostgresAuthorizationStore(source)
                assertEquals(
                    StatefulRejection.NONCE_REUSE,
                    refusal(restarted.admit(request())),
                    "a restart must not forget a nonce it has already seen",
                )

                // The daily cap is one budget across both, not one each.
                repeat(4) { attempt ->
                    val store = if (attempt % 2 == 0) first else second
                    assertEquals(
                        Admission.Admitted,
                        store.admit(request(nonce = "d-$attempt", authorizationId = "da-$attempt")),
                    )
                }
                assertEquals(
                    StatefulRejection.DAILY_LIMIT_EXCEEDED,
                    refusal(second.admit(request(nonce = "d-x", authorizationId = "da-x"))),
                    "5.00 of a 5.00 limit is spent; the sixth unit must be refused by either replica",
                )
            }
        }

    @Test
    fun `concurrent presentations of one authorization yield exactly one admission`() =
        runBlocking<Unit> {
            PostgreSQLContainer<Nothing>("postgres:16-alpine").use { container ->
                container.start()
                val source = dataSource(container)
                PostgresAuthorizationStore(source).initializeSchema()
                val stores = List(8) { PostgresAuthorizationStore(source) }

                val outcomes =
                    withContext(Dispatchers.IO) {
                        stores.map { store -> async { store.admit(request(dailyLimit = null)) } }.awaitAll()
                    }
                assertEquals(1, outcomes.count { it is Admission.Admitted }, "outcomes: $outcomes")
                assertEquals(7, outcomes.count { it is Admission.Refused })
            }
        }

    @Test
    fun `a refusal records nothing and a purge drops only what can no longer decide anything`() =
        runBlocking<Unit> {
            PostgreSQLContainer<Nothing>("postgres:16-alpine").use { container ->
                container.start()
                val source = dataSource(container)
                val store = PostgresAuthorizationStore(source)
                store.initializeSchema()

                assertEquals(Admission.Admitted, store.admit(request(amount = "5.00")))
                val refused = request(nonce = "n-2", authorizationId = "auth-2")
                assertEquals(StatefulRejection.DAILY_LIMIT_EXCEEDED, refusal(store.admit(refused)))
                assertEquals(1, rowCount(source, "avp_nonces"), "a refused authorization must record no nonce")
                assertEquals(1, rowCount(source, "avp_consumptions"))

                // Still inside the retention window: nothing is dropped.
                assertEquals(0, store.purgeExpired(now))
                assertEquals(1, rowCount(source, "avp_nonces"))

                // Past it: the record can no longer change a decision, so it goes.
                val dropped = store.purgeExpired(now.plus(Duration.ofDays(2)))
                assertEquals(3, dropped, "one nonce, one consumption and one past day")
                assertEquals(0, rowCount(source, "avp_nonces"))
                assertEquals(0, rowCount(source, "avp_daily_spend"))
            }
        }

    @Test
    fun `an authorization with no expiry bound is refused rather than half-remembered`() =
        runBlocking<Unit> {
            PostgreSQLContainer<Nothing>("postgres:16-alpine").use { container ->
                container.start()
                val store = PostgresAuthorizationStore(dataSource(container), retentionCeiling = Duration.ofDays(1))
                store.initializeSchema()
                assertEquals(
                    StatefulRejection.UNBOUNDED_LIFETIME,
                    refusal(store.admit(request(retainUntil = Instant.MAX))),
                )
            }
        }

    @Test
    fun `an unreachable database is a denial, never an admission`() =
        runBlocking<Unit> {
            val unreachable =
                PGSimpleDataSource().apply {
                    // A port nothing listens on: the connection fails rather than hanging.
                    setURL("jdbc:postgresql://127.0.0.1:1/trustweave?connectTimeout=2&loginTimeout=2")
                    user = "nobody"
                    password = "nobody"
                }
            val store = PostgresAuthorizationStore(unreachable)
            assertEquals(
                StatefulRejection.STORE_UNAVAILABLE,
                refusal(store.admit(request())),
                "a store that cannot decide must fail closed",
            )
        }

    private fun rowCount(
        source: DataSource,
        table: String,
    ): Int =
        source.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT count(*) FROM $table").use { rows ->
                    check(rows.next())
                    rows.getInt(1)
                }
            }
        }
}
