package org.trustweave.credential.vi

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.MountableFile
import org.trustweave.credential.vi.crypto.sha256B64Url
import org.trustweave.credential.vi.verification.BudgetReservation
import org.trustweave.credential.vi.verification.PostgresIntentLedger
import org.trustweave.credential.vi.verification.SettlementOutcome
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Isolated component host exercise, not a production HTTP authorization endpoint. */
class IntentOperationsExerciseTest {
    private fun hash(value: String) = sha256B64Url(value.toByteArray())

    private fun source(container: PostgreSQLContainer<Nothing>) =
        PGSimpleDataSource().apply {
            setURL(container.jdbcUrl)
            user = container.username
            password = container.password
            connectTimeout = 1
            socketTimeout = 2
        }

    private fun snapshot(source: PGSimpleDataSource): String =
        source.connection.use { connection ->
            hash(
                buildString {
                    for (table in listOf("vi_budget_accounts", "vi_budget_reservations")) {
                        appendLine(table)
                        connection.createStatement().use { statement ->
                            statement.queryTimeout = 10
                            statement.executeQuery("SELECT row_to_json(t)::text FROM $table t ORDER BY row_to_json(t)::text").use { rows ->
                                while (rows.next()) appendLine(rows.getString(1))
                            }
                        }
                    }
                },
            )
        }

    @Test
    fun `instrumented host load failure alert and independent postgres restore`() {
        PostgreSQLContainer<Nothing>("postgres:16-alpine").use { primary ->
            PostgreSQLContainer<Nothing>("postgres:16-alpine").use { recovery ->
                primary.start()
                val ledger = PostgresIntentLedger(source(primary))
                ledger.initializeSchema()
                val active = AtomicReference(ledger)
                val accepting = AtomicBoolean(true)
                val accepted = AtomicLong()
                val rejected = AtomicLong()
                val errors = AtomicLong()
                val durations = ConcurrentLinkedQueue<Long>()
                val workers = Executors.newFixedThreadPool(8)
                val callers = Executors.newFixedThreadPool(8)
                val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 16)
                server.executor = workers
                server.createContext("/reserve") { exchange ->
                    val started = System.nanoTime()
                    val status =
                        try {
                            if (!accepting.get()) {
                                503
                            } else {
                                val id = exchange.requestURI.query?.toIntOrNull()
                                if (id == null || id !in 0..1000) {
                                    400
                                } else if (
                                    active.get().reserve(
                                        hash("load"),
                                        hash("request-$id"),
                                        hash("challenge-$id"),
                                        BudgetReservation("USD", 500, 10, 100),
                                    )
                                ) {
                                    accepted.incrementAndGet()
                                    200
                                } else {
                                    rejected.incrementAndGet()
                                    409
                                }
                            }
                        } catch (_: java.sql.SQLException) {
                            errors.incrementAndGet()
                            503
                        }
                    durations.add(System.nanoTime() - started)
                    val bytes = "status=$status".toByteArray()
                    exchange.sendResponseHeaders(status, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
                server.createContext("/metrics") { exchange ->
                    val text = active.get().diagnostics.prometheus()
                    val bytes = text.toByteArray()
                    exchange.responseHeaders.set("Content-Type", "text/plain; version=0.0.4")
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
                val dump = Files.createTempFile("intent-restore-", ".dump")
                try {
                    server.start()
                    val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
                    val base = "http://127.0.0.1:${server.address.port}"

                    fun request(path: String) =
                        client.send(
                            HttpRequest
                                .newBuilder(URI.create(base + path))
                                .timeout(Duration.ofSeconds(10))
                                .GET()
                                .build(),
                            HttpResponse.BodyHandlers.ofString(),
                        )
                    val started = System.nanoTime()
                    val results =
                        callers
                            .invokeAll((0 until 100).map { id -> Callable { id to request("/reserve?$id").statusCode() } })
                            .map { it.get() }
                    val loadMillis = (System.nanoTime() - started) / 1_000_000
                    val loadDurations = durations.sorted()
                    val successful = results.filter { it.second == 200 }.map { it.first }
                    assertEquals(50, successful.size)
                    assertEquals(50, results.count { it.second == 409 })
                    val released = successful[0]
                    val settled = successful[1]
                    val reconciled =
                        callers
                            .invokeAll(
                                (0 until 8).map {
                                    Callable {
                                        ledger.reconcile(
                                            "load",
                                            "request-$released",
                                            SettlementOutcome.RELEASED,
                                            "journal:confirmed-not-executed",
                                        )
                                    }
                                },
                            ).map { it.get() }
                    assertTrue(reconciled.all { it })
                    assertFalse(ledger.reconcile("load", "request-$released", SettlementOutcome.SETTLED, "conflicting-journal"))
                    assertTrue(ledger.reconcile("load", "request-$settled", SettlementOutcome.SETTLED, "journal:confirmed-settled"))
                    val before = ledger.healthSnapshot()
                    assertEquals(48, before.pending)
                    assertEquals(1, before.released)
                    assertEquals(1, before.settled)
                    // Stop admission and drain all submitted work before the zero-RPO backup.
                    accepting.set(false)
                    val beforeDigest = snapshot(source(primary))
                    assertEquals(503, request("/reserve?900").statusCode())
                    val backup =
                        primary.execInContainer(
                            "pg_dump",
                            "-U",
                            primary.username,
                            "-d",
                            primary.databaseName,
                            "-Fc",
                            "-f",
                            "/tmp/ledger.dump",
                        )
                    assertEquals(0, backup.exitCode)
                    primary.copyFileFromContainer("/tmp/ledger.dump", dump.toString())
                    primary.stop()
                    accepting.set(true)
                    assertEquals(503, request("/reserve?901").statusCode())
                    assertEquals(1, errors.get())
                    org.junit.jupiter.api.Assertions
                        .assertThrows(java.sql.SQLException::class.java) { ledger.healthSnapshot() }
                    val unavailableMetrics = request("/metrics")
                    assertEquals(200, unavailableMetrics.statusCode())
                    assertTrue(unavailableMetrics.body().contains("trustweave_intent_health_poll_success 0"))
                    assertFalse(unavailableMetrics.body().contains("trustweave_intent_reconciliation_pending "))
                    accepting.set(false)
                    val restoreStarted = System.nanoTime()
                    recovery.start()
                    recovery.copyFileToContainer(MountableFile.forHostPath(dump), "/tmp/ledger.dump")
                    val restore =
                        recovery.execInContainer(
                            "pg_restore",
                            "-U",
                            recovery.username,
                            "-d",
                            recovery.databaseName,
                            "--no-owner",
                            "--exit-on-error",
                            "/tmp/ledger.dump",
                        )
                    assertEquals(0, restore.exitCode)
                    val restored = PostgresIntentLedger(source(recovery), ledger.diagnostics)
                    assertEquals(before, restored.healthSnapshot())
                    assertEquals(beforeDigest, snapshot(source(recovery)))
                    active.set(restored)
                    accepting.set(true)
                    val restoreMillis = (System.nanoTime() - restoreStarted) / 1_000_000
                    for (id in successful) assertEquals(409, request("/reserve?$id").statusCode())
                    assertEquals(200, request("/reserve?902").statusCode())
                    assertEquals(409, request("/reserve?903").statusCode())
                    restored.healthSnapshot()
                    val metrics = request("/metrics").body()
                    assertTrue(metrics.contains("trustweave_intent_operations_total{operation=\"reserve\",outcome=\"storage_failure\"} 1"))
                    assertTrue(metrics.contains("trustweave_intent_reconciliation_pending 49"))
                    assertTrue(metrics.contains("trustweave_intent_health_poll_success 1"))
                    assertTrue(metrics.contains("trustweave_intent_operations_total{operation=\"reserve\",outcome=\"success\"} 51"))
                    assertTrue(metrics.contains("trustweave_intent_operations_total{operation=\"reserve\",outcome=\"rejected\"} 101"))
                    assertFalse(metrics.contains("request-"))
                    val report =
                        buildJsonObject {
                            put(
                                "scope",
                                "Loopback component host with pre-authorized fixtures; " +
                                    "not production HTTP or payment-provider qualification",
                            )
                            put("requests", 100)
                            put("concurrency", 8)
                            put("accepted", 50)
                            put("rejected", 50)
                            put("load_ms", loadMillis)
                            put("restore_ms", restoreMillis)
                            put("rpo", "zero for quiesced workload; all 50 records and settlement states verified")
                            put("backup", "pg_dump custom format to pg_restore in separate PostgreSQL container")
                            put("restored_snapshot_sha256_base64url", beforeDigest)
                            put(
                                "failure",
                                "primary stopped; authorization returned 503; " +
                                    "SQL failure metric incremented; metrics endpoint stayed available",
                            )
                            put(
                                "load_handler_p95_ms",
                                loadDurations[(loadDurations.size * 95 + 99) / 100 - 1] / 1_000_000,
                            )
                            put("metrics", metrics)
                        }
                    val output = evidenceDir("intent-operations.json")
                    Files.createDirectories(output.parent)
                    Files.writeString(output, report.toString())
                    Files.writeString(output.resolveSibling("intent-metrics.prom"), metrics)
                } finally {
                    server.stop(0)
                    workers.shutdownNow()
                    callers.shutdownNow()
                    Files.deleteIfExists(dump)
                }
            }
        }
    }
}
