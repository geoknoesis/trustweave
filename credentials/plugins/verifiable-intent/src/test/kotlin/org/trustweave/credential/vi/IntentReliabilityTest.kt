package org.trustweave.credential.vi

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.trustweave.credential.vi.crypto.sha256B64Url
import org.trustweave.credential.vi.verification.BudgetReservation
import org.trustweave.credential.vi.verification.PostgresIntentLedger
import org.trustweave.credential.vi.verification.SettlementOutcome
import java.nio.file.Files
import java.sql.SQLException
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Timeout(300)
class IntentReliabilityTest {
    private val reportJson = Json { prettyPrint = true }

    private fun hash(value: String) = sha256B64Url(value.toByteArray())

    private fun source(postgres: PostgreSQLContainer<Nothing>) =
        PGSimpleDataSource().apply {
            setURL(postgres.jdbcUrl)
            user = postgres.username
            password = postgres.password
            connectTimeout = 3
            socketTimeout = 20
        }

    private fun sql(
        source: DataSource,
        query: String,
    ): Long =
        source.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.queryTimeout = 30
                statement.executeQuery(query).use { rows ->
                    check(rows.next())
                    rows.getLong(1)
                }
            }
        }

    private fun execute(
        source: DataSource,
        query: String,
    ) = source.connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.queryTimeout = 30
            statement.execute(query)
        }
    }

    private fun report(
        name: String,
        values: Map<String, Number>,
    ) {
        val target = evidenceDir("reliability", "$name.json")
        Files.createDirectories(target.parent)
        Files.writeString(
            target,
            reportJson.encodeToString(
                buildJsonObject {
                    put("scenario", name)
                    put("scope", "Disposable PostgreSQL 16 component qualification; not production capacity or RTO")
                    values.forEach { (key, value) -> put(key, value) }
                },
            ),
        )
    }

    private fun assertConsistent(source: DataSource) {
        assertEquals(
            0L,
            sql(
                source,
                """
                SELECT count(*) FROM vi_budget_accounts a LEFT JOIN
                  (SELECT scope,count(*) AS occurrences,
                   coalesce(sum(amount) FILTER (WHERE settlement_state <> 'RELEASED'),0) AS spending
                   FROM vi_budget_reservations GROUP BY scope) r USING(scope)
                WHERE a.spent <> coalesce(r.spending,0) OR a.occurrence_count <> coalesce(r.occurrences,0)
                   OR a.spent > a.maximum OR a.spent < 0
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `bounded pool sustains mixed multi instance contention without overspend or replay`() {
        PostgreSQLContainer<Nothing>("postgres:16-alpine").use { postgres ->
            postgres.start()
            HikariDataSource(
                HikariConfig().apply {
                    dataSource = source(postgres)
                    maximumPoolSize = 8
                    minimumIdle = 2
                    connectionTimeout = 15_000
                },
            ).use { pool ->
                val ledgers = List(4) { PostgresIntentLedger(pool) }
                ledgers.first().initializeSchema()
                val callers = Executors.newFixedThreadPool(32)
                val durations = ConcurrentLinkedQueue<Long>()
                val started = System.nanoTime()
                try {
                    val results =
                        callers
                            .invokeAll(
                                (0 until 4096).map { id ->
                                    Callable {
                                        val start = System.nanoTime()
                                        val accepted =
                                            ledgers[id % 4].reserve(
                                                hash("scope-${id % 64}"),
                                                hash("tx-$id"),
                                                hash("nonce-$id"),
                                                BudgetReservation("USD", 32, 1, 40),
                                            )
                                        durations.add(System.nanoTime() - start)
                                        id to accepted
                                    }
                                },
                                120,
                                TimeUnit.SECONDS,
                            ).map { it.get() }
                    val accepted = results.filter { it.second }.map { it.first }
                    assertEquals(2048, accepted.size)
                    assertEquals(
                        List(64) { 32 },
                        accepted
                            .groupingBy { it % 64 }
                            .eachCount()
                            .values
                            .sorted(),
                    )
                    assertConsistent(pool)
                    // Replay both within the original mandate and across mandates must remain rejected.
                    assertTrue(
                        callers
                            .invokeAll(
                                accepted.take(128).map { id ->
                                    Callable {
                                        !ledgers[0].reserve(
                                            hash("scope-${id % 64}"),
                                            hash("tx-$id"),
                                            hash("fresh-$id"),
                                            BudgetReservation("USD", 32, 0, 40),
                                        ) &&
                                            !ledgers[1].reserve(
                                                hash("cross-$id"),
                                                hash("cross-$id"),
                                                hash("nonce-$id"),
                                                BudgetReservation("USD", 32, 0, 40),
                                            )
                                    }
                                },
                            ).all { it.get() },
                    )
                    val ordered =
                        accepted.groupBy { it % 64 }.values.flatMap {
                            it.sorted().mapIndexed {
                                index,
                                id,
                                ->
                                id to (index % 2 == 0)
                            }
                        }
                    assertTrue(
                        callers
                            .invokeAll(
                                ordered.map { (id, release) ->
                                    Callable {
                                        ledgers[id % 4].reconcile(
                                            "scope-${id % 64}",
                                            "tx-$id",
                                            if (release) SettlementOutcome.RELEASED else SettlementOutcome.SETTLED,
                                            "journal-$id",
                                        )
                                    }
                                },
                            ).all { it.get() },
                    )
                    val next =
                        callers
                            .invokeAll(
                                (4096 until 5120).map { id ->
                                    Callable {
                                        ledgers[id % 4].reserve(
                                            hash("scope-${id % 64}"),
                                            hash("tx-$id"),
                                            hash("nonce-$id"),
                                            BudgetReservation("USD", 32, 1, 40),
                                        )
                                    }
                                },
                            ).map { it.get() }
                    assertEquals(512, next.count { it }) // Eight remaining occurrences per scope, despite sixteen refunded units.
                    assertConsistent(pool)
                    assertEquals(64L, sql(pool, "SELECT count(*) FROM vi_budget_accounts WHERE occurrence_count=40 AND spent=24"))
                    assertEquals(0, pool.hikariPoolMXBean.activeConnections)
                    assertTrue(pool.hikariPoolMXBean.totalConnections <= 8)
                    val sorted = durations.sorted()
                    report(
                        "contention",
                        mapOf(
                            "callers" to 32,
                            "instances" to 4,
                            "pool_limit" to 8,
                            "initial_requests" to 4096,
                            "initial_accepted" to accepted.size,
                            "followup_accepted" to 512,
                            "elapsed_ms" to (System.nanoTime() - started) / 1_000_000,
                            "reserve_p95_ms" to sorted[(sorted.size * .95).toInt()] / 1_000_000,
                            "reserve_p99_ms" to sorted[(sorted.size * .99).toInt()] / 1_000_000,
                            "reserve_max_ms" to sorted.last() / 1_000_000,
                            "invariant_violations" to 0,
                        ),
                    )
                } finally {
                    callers.shutdownNow()
                    assertTrue(callers.awaitTermination(30, TimeUnit.SECONDS))
                }
            }
        }
    }

    @Test
    fun `sustained skewed traffic keeps the pool bounded and the durable ledger consistent`() {
        PostgreSQLContainer<Nothing>("postgres:16-alpine").use { postgres ->
            postgres.start()
            HikariDataSource(
                HikariConfig().apply {
                    dataSource = source(postgres)
                    maximumPoolSize = 8
                    minimumIdle = 2
                    connectionTimeout = 15_000
                },
            ).use { pool ->
                val ledgers = List(4) { PostgresIntentLedger(pool) }
                ledgers.first().initializeSchema()
                val workers = Executors.newFixedThreadPool(32)
                val sequence =
                    java.util.concurrent.atomic
                        .AtomicLong()
                val peakConnections =
                    java.util.concurrent.atomic
                        .AtomicInteger()
                val durations = ConcurrentLinkedQueue<Long>()
                val started = System.nanoTime()
                val until = started + TimeUnit.SECONDS.toNanos(60)
                try {
                    val results =
                        workers
                            .invokeAll(
                                (0 until 32).map { worker ->
                                    Callable {
                                        var completed = 0
                                        val ledger = ledgers[worker % 4]
                                        val policy = BudgetReservation("USD", 1000000, 1, 1000000)
                                        while (System.nanoTime() < until) {
                                            val id = sequence.getAndIncrement()
                                            val scope = if (id % 5 != 0L) "hot" else "cold-${id % 101}"
                                            val start = System.nanoTime()
                                            assertTrue(ledger.reserve(hash(scope), hash("soak-$id"), hash("soak-$id"), policy))
                                            if (id % 3 != 0L) {
                                                assertTrue(
                                                    ledger.reconcile(
                                                        scope,
                                                        "soak-$id",
                                                        if (id % 3 == 1L) SettlementOutcome.SETTLED else SettlementOutcome.RELEASED,
                                                        "journal-$id",
                                                    ),
                                                )
                                            }
                                            assertFalse(ledger.reserve(hash(scope), hash("soak-$id"), hash("soak-$id"), policy))
                                            durations.add(System.nanoTime() - start)
                                            peakConnections.accumulateAndGet(pool.hikariPoolMXBean.totalConnections, ::maxOf)
                                            completed++
                                        }
                                        completed
                                    }
                                },
                                120,
                                TimeUnit.SECONDS,
                            ).sumOf { it.get() }
                    assertTrue(results >= 1000, "Qualification needs at least 1000 completed workflows, got $results")
                    assertTrue(peakConnections.get() <= 8)
                    assertEquals(0, pool.hikariPoolMXBean.activeConnections)
                    assertConsistent(pool)
                    assertEquals(results.toLong(), sql(pool, "SELECT count(*) FROM vi_budget_reservations"))
                    val sorted = durations.sorted()
                    report(
                        "sustained",
                        mapOf(
                            "duration_ms" to (System.nanoTime() - started) / 1_000_000,
                            "completed_workflows" to results,
                            "callers" to 32,
                            "instances" to 4,
                            "pool_limit" to 8,
                            "peak_connections" to peakConnections.get(),
                            "hot_scope_percent" to 80,
                            "workflow_p95_ms" to sorted[(sorted.size * .95).toInt()] / 1_000_000,
                            "workflow_p99_ms" to sorted[(sorted.size * .99).toInt()] / 1_000_000,
                            "workflow_max_ms" to sorted.last() / 1_000_000,
                            "invariant_violations" to 0,
                        ),
                    )
                } finally {
                    workers.shutdownNow()
                    assertTrue(workers.awaitTermination(30, TimeUnit.SECONDS))
                }
            }
        }
    }

    @Test
    fun `large legacy history migrates atomically and occurrence admission avoids history scans`() {
        PostgreSQLContainer<Nothing>("postgres:16-alpine").use { postgres ->
            postgres.start()
            val source = source(postgres)
            val ledger = PostgresIntentLedger(source)
            // Seed the immediately preceding schema, with a large retained replay history.
            execute(
                source,
                "CREATE TABLE vi_budget_accounts(scope VARCHAR(43) PRIMARY KEY,currency VARCHAR(3) NOT NULL,maximum BIGINT NOT NULL,spent BIGINT NOT NULL)",
            )
            execute(
                source,
                "CREATE TABLE vi_budget_reservations(scope VARCHAR(43) REFERENCES vi_budget_accounts(scope),transaction_hash VARCHAR(43),challenge_hash VARCHAR(43) UNIQUE,amount BIGINT NOT NULL,PRIMARY KEY(scope,transaction_hash))",
            )
            execute(source, "INSERT INTO vi_budget_accounts VALUES ('${hash("history")}','USD',1000000,100000)")
            execute(
                source,
                "INSERT INTO vi_budget_reservations SELECT '${hash(
                    "history",
                )}',lpad(i::text,43,'0'),lpad(i::text,43,'0'),1 FROM generate_series(1,100000) i",
            )
            val started = System.nanoTime()
            ledger.initializeSchema()
            val migrationMs = (System.nanoTime() - started) / 1_000_000
            ledger.initializeSchema()
            assertEquals(100000L, sql(source, "SELECT occurrence_count FROM vi_budget_accounts"))
            assertFalse(ledger.reserve(hash("history"), hash("over-cap"), hash("over-cap"), BudgetReservation("USD", 1000000, 0, 100000)))
            val queries = ConcurrentLinkedQueue<String>()
            val monitored =
                object : DataSource by source {
                    override fun getConnection(): java.sql.Connection {
                        val actual = source.connection
                        return java.lang.reflect.Proxy.newProxyInstance(
                            java.sql.Connection::class.java.classLoader,
                            arrayOf(java.sql.Connection::class.java),
                        ) { _, method, args ->
                            if (method.name == "prepareStatement") queries.add(args!![0] as String)
                            try {
                                method.invoke(actual, *(args ?: emptyArray()))
                            } catch (
                                failure: java.lang.reflect.InvocationTargetException,
                            ) {
                                throw failure.cause!!
                            }
                        } as java.sql.Connection
                    }
                }
            val next = PostgresIntentLedger(monitored)
            val duration = System.nanoTime()
            repeat(
                100,
            ) { id ->
                assertTrue(
                    next.reserve(hash("history"), hash("next-$id"), hash("next-$id"), BudgetReservation("USD", 1000000, 1, 100100)),
                )
            }
            val reserveMs = (System.nanoTime() - duration) / 1_000_000
            assertFalse(queries.any { it.contains("count(", ignoreCase = true) })
            assertEquals(100100L, sql(source, "SELECT occurrence_count FROM vi_budget_accounts"))
            // An insert by a preceding writer is also counted, and a duplicate does not increment.
            execute(
                source,
                "INSERT INTO vi_budget_reservations(scope,transaction_hash,challenge_hash,amount) VALUES ('${hash(
                    "history",
                )}','old-writer','old-writer',0) ON CONFLICT DO NOTHING",
            )
            execute(
                source,
                "INSERT INTO vi_budget_reservations(scope,transaction_hash,challenge_hash,amount) VALUES ('${hash(
                    "history",
                )}','old-writer','old-writer',0) ON CONFLICT DO NOTHING",
            )
            assertEquals(100101L, sql(source, "SELECT occurrence_count FROM vi_budget_accounts"))
            assertConsistent(source)
            report(
                "history",
                mapOf(
                    "legacy_rows" to 100000,
                    "migration_ms" to migrationMs,
                    "new_requests" to 100,
                    "new_requests_ms" to reserveMs,
                    "admission_history_scans" to 0,
                ),
            )
        }
    }

    @Test
    fun `database lock timeout rolls back completely and unrelated mandates progress`() {
        PostgreSQLContainer<Nothing>("postgres:16-alpine").use { postgres ->
            postgres.start()
            val source = source(postgres)
            val ledger = PostgresIntentLedger(source)
            ledger.initializeSchema()
            assertTrue(ledger.reserve(hash("locked"), hash("first"), hash("first"), BudgetReservation("USD", 10, 1, 10)))
            val worker = Executors.newSingleThreadExecutor()
            try {
                source.connection.use { blocker ->
                    blocker.autoCommit = false
                    blocker.createStatement().execute("SELECT scope FROM vi_budget_accounts WHERE scope='${hash("locked")}' FOR UPDATE")
                    val started = System.nanoTime()
                    val waiting =
                        worker.submit(
                            Callable {
                                assertFailsWith<SQLException> {
                                    ledger.reserve(
                                        hash("locked"),
                                        hash("retry"),
                                        hash("retry"),
                                        BudgetReservation("USD", 10, 1, 10),
                                    )
                                }
                            },
                        )
                    assertTrue(ledger.reserve(hash("unrelated"), hash("other"), hash("other"), BudgetReservation("USD", 10, 1, 10)))
                    val failure = waiting.get(25, TimeUnit.SECONDS)
                    assertEquals("57014", failure.sqlState)
                    blocker.rollback()
                    assertTrue(ledger.reserve(hash("locked"), hash("retry"), hash("retry"), BudgetReservation("USD", 10, 1, 10)))
                    assertConsistent(source)
                    report(
                        "lock-timeout",
                        mapOf(
                            "elapsed_ms" to (System.nanoTime() - started) / 1_000_000,
                            "unrelated_progress" to 1,
                            "rollback_retry_success" to 1,
                        ),
                    )
                }
            } finally {
                worker.shutdownNow()
                assertTrue(worker.awaitTermination(30, TimeUnit.SECONDS))
            }
        }
    }

    @Test
    fun `counter trigger respects runtime privileges and rolls back failed budget writes`() {
        PostgreSQLContainer<Nothing>("postgres:16-alpine").use { postgres ->
            postgres.start()
            val source = source(postgres)
            PostgresIntentLedger(source).initializeSchema()
            execute(source, "CREATE ROLE ledger_runtime LOGIN PASSWORD 'disposable-fixture'")
            execute(source, "GRANT USAGE ON SCHEMA public TO ledger_runtime")
            execute(source, "GRANT SELECT,INSERT,UPDATE ON vi_budget_accounts TO ledger_runtime")
            execute(source, "GRANT SELECT,INSERT ON vi_budget_reservations TO ledger_runtime")
            val runtime =
                source(postgres).apply {
                    user = "ledger_runtime"
                    password = "disposable-fixture"
                }
            val ledger = PostgresIntentLedger(runtime)
            assertTrue(ledger.reserve(hash("role"), hash("first"), hash("first"), BudgetReservation("USD", 10, 1, 10)))
            execute(
                source,
                """CREATE FUNCTION fail_spending() RETURNS trigger AS ${'$'}${'$'} BEGIN
                IF NEW.spent > OLD.spent THEN RAISE EXCEPTION 'injected spending failure'; END IF;
                RETURN NEW; END; ${'$'}${'$'} LANGUAGE plpgsql""",
            )
            execute(
                source,
                "CREATE TRIGGER fail_spending BEFORE UPDATE ON vi_budget_accounts FOR EACH ROW EXECUTE FUNCTION fail_spending()",
            )
            assertFailsWith<SQLException> {
                ledger.reserve(hash("role"), hash("retry"), hash("retry"), BudgetReservation("USD", 10, 1, 10))
            }
            assertEquals(1L, sql(source, "SELECT occurrence_count FROM vi_budget_accounts"))
            assertEquals(1L, sql(source, "SELECT count(*) FROM vi_budget_reservations"))
            assertConsistent(source)
            execute(source, "DROP TRIGGER fail_spending ON vi_budget_accounts")
            assertTrue(ledger.reserve(hash("role"), hash("retry"), hash("retry"), BudgetReservation("USD", 10, 1, 10)))
            assertEquals(2L, sql(source, "SELECT occurrence_count FROM vi_budget_accounts"))
            assertConsistent(source)
            // A row-security policy that hides updates must not allow an uncounted direct insert.
            execute(source, "ALTER TABLE vi_budget_accounts ENABLE ROW LEVEL SECURITY")
            execute(source, "CREATE POLICY readable ON vi_budget_accounts FOR SELECT TO ledger_runtime USING (true)")
            execute(source, "CREATE POLICY no_updates ON vi_budget_accounts FOR UPDATE TO ledger_runtime USING (false)")
            assertFailsWith<SQLException> {
                execute(
                    runtime,
                    "INSERT INTO vi_budget_reservations(scope,transaction_hash,challenge_hash,amount) " +
                        "VALUES ('${hash("role")}','uncounted','uncounted',0)",
                )
            }
            assertEquals(2L, sql(source, "SELECT occurrence_count FROM vi_budget_accounts"))
            assertConsistent(source)
        }
    }

    @Test
    fun `synchronous commit is enforced without weakening remote apply or leaking pooled settings`() {
        PostgreSQLContainer<Nothing>("postgres:16-alpine").use { postgres ->
            postgres.start()
            val source = source(postgres)
            val ledger = PostgresIntentLedger(source)
            ledger.initializeSchema()
            execute(source, "CREATE TABLE commit_modes(mode text)")
            execute(
                source,
                """CREATE FUNCTION record_commit_mode() RETURNS trigger AS ${'$'}${'$'} BEGIN
                INSERT INTO commit_modes VALUES (current_setting('synchronous_commit')); RETURN NEW;
                END; ${'$'}${'$'} LANGUAGE plpgsql""",
            )
            execute(
                source,
                "CREATE TRIGGER record_commit_mode AFTER INSERT OR UPDATE ON vi_budget_accounts FOR EACH ROW EXECUTE FUNCTION record_commit_mode()",
            )
            for (mode in listOf("off", "local", "remote_write", "on", "remote_apply")) {
                source.connection.use { connection ->
                    connection.createStatement().execute("SET synchronous_commit='$mode'")
                    val single =
                        object : DataSource by source {
                            override fun getConnection(): java.sql.Connection =
                                java.lang.reflect.Proxy.newProxyInstance(
                                    java.sql.Connection::class.java.classLoader,
                                    arrayOf(java.sql.Connection::class.java),
                                ) { _, method, args ->
                                    if (method.name == "close") {
                                        null
                                    } else {
                                        try {
                                            method.invoke(connection, *(args ?: emptyArray()))
                                        } catch (
                                            failure: java.lang.reflect.InvocationTargetException,
                                        ) {
                                            throw failure.cause!!
                                        }
                                    }
                                } as java.sql.Connection
                        }
                    assertTrue(PostgresIntentLedger(single).reserve(hash(mode), hash(mode), hash(mode), BudgetReservation("USD", 10, 1)))
                    connection.createStatement().executeQuery("SHOW synchronous_commit").use { row ->
                        assertTrue(row.next())
                        assertEquals(mode, row.getString(1))
                    }
                    connection.rollback()
                }
            }
            assertEquals(0L, sql(source, "SELECT count(*) FROM commit_modes WHERE mode NOT IN ('on','remote_apply')"))
            assertTrue(sql(source, "SELECT count(*) FROM commit_modes WHERE mode='remote_apply'") > 0)
            assertConsistent(source)
        }
    }
}
