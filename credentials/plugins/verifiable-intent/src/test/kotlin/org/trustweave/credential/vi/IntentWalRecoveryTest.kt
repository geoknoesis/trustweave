package org.trustweave.credential.vi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.MountableFile
import org.trustweave.credential.vi.crypto.sha256B64Url
import org.trustweave.credential.vi.verification.BudgetReservation
import org.trustweave.credential.vi.verification.PostgresIntentLedger
import org.trustweave.credential.vi.verification.SettlementOutcome
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Physical base backup + archived WAL. Every container and record belongs to this test. */
@Timeout(300)
class IntentWalRecoveryTest {
    private val reportJson = Json { prettyPrint = true }

    private fun hash(value: String) = sha256B64Url(value.toByteArray())

    private fun source(
        url: String,
        username: String,
        secret: String,
    ) = PGSimpleDataSource().apply {
        setURL(url)
        user = username
        password = secret
        connectTimeout = 2
        socketTimeout = 10
    }

    private fun scalar(
        source: PGSimpleDataSource,
        query: String,
    ): String =
        source.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.queryTimeout = 10
                statement.executeQuery(query).use { rows ->
                    check(rows.next())
                    rows.getString(1)
                }
            }
        }

    private fun snapshot(source: PGSimpleDataSource): String =
        hash(
            buildString {
                source.connection.use { connection ->
                    for (table in listOf("vi_budget_accounts", "vi_budget_reservations")) {
                        appendLine(table)
                        connection.createStatement().use { statement ->
                            statement.executeQuery("SELECT row_to_json(t)::text FROM $table t ORDER BY row_to_json(t)::text").use { rows ->
                                while (rows.next()) appendLine(rows.getString(1))
                            }
                        }
                    }
                }
            },
        )

    private fun command(
        container: GenericContainer<*>,
        vararg command: String,
    ): String {
        val result = container.execInContainer(*command)
        assertEquals(0, result.exitCode, result.stderr)
        return result.stdout
    }

    @Test
    fun `crash restart and archived WAL restore preserve authorization and settlement at target`() {
        PostgreSQLContainer<Nothing>("postgres:16-alpine")
            .apply {
                withCommand(
                    "postgres",
                    "-c",
                    "archive_mode=on",
                    "-c",
                    "archive_command=mkdir -p /tmp/tw-archive && " +
                        "(test ! -f /tmp/tw-archive/%f && cp %p /tmp/tw-archive/%f || cmp -s %p /tmp/tw-archive/%f)",
                )
            }.use { primary ->
                primary.start()
                val source = source(primary.jdbcUrl, primary.username, primary.password)
                val ledger = PostgresIntentLedger(source)
                ledger.initializeSchema()
                val policy = BudgetReservation("USD", 100, 10, 10)

                fun reserve(id: String) = ledger.reserve(hash("wal"), hash(id), hash(id), policy)
                assertTrue(reserve("before-backup"))
                val backupDigest = snapshot(source)
                command(
                    primary,
                    "pg_basebackup",
                    "-h",
                    "127.0.0.1",
                    "-U",
                    primary.username,
                    "-D",
                    "/tmp/tw-base",
                    "--checkpoint=fast",
                    "--wal-method=stream",
                    "--no-password",
                )
                command(primary, "pg_verifybackup", "/tmp/tw-base")
                // Corrupt a same-length manifest-covered file in the disposable backup, then restore it.
                command(primary, "sh", "-ec", "cp /tmp/tw-base/PG_VERSION /tmp/tw-version; printf '15\\n' > /tmp/tw-base/PG_VERSION")
                val corrupted = primary.execInContainer("pg_verifybackup", "/tmp/tw-base")
                assertTrue(corrupted.exitCode != 0 && corrupted.stderr.contains("checksum mismatch"), corrupted.stderr)
                command(primary, "mv", "/tmp/tw-version", "/tmp/tw-base/PG_VERSION")
                command(primary, "pg_verifybackup", "/tmp/tw-base")
                scalar(source, "SELECT pg_switch_wal()") // The target WAL must be newer than all WAL shipped in the base backup.
                assertTrue(reserve("post-backup-settled"))
                assertTrue(reserve("post-backup-released"))
                assertTrue(reserve("post-backup-pending"))
                assertTrue(ledger.reconcile("wal", "post-backup-settled", SettlementOutcome.SETTLED, "journal:settled"))
                assertTrue(ledger.reconcile("wal", "post-backup-released", SettlementOutcome.RELEASED, "journal:not-executed"))
                val targetDigest = snapshot(source)
                assertFalse(backupDigest == targetDigest)
                val targetSegment = scalar(source, "SELECT pg_walfile_name(pg_create_restore_point('trustweave_qualified'))")
                assertTrue(targetSegment.matches(Regex("[0-9A-F]{24}")))
                assertTrue(reserve("after-target"))
                val latestDigest = snapshot(source)
                scalar(source, "SELECT pg_switch_wal()")
                val deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos()
                while (scalar(source, "SELECT coalesce(last_archived_wal >= '$targetSegment', false) FROM pg_stat_archiver") != "t") {
                    check(System.nanoTime() < deadline) { "Target WAL was not archived" }
                    Thread.sleep(100)
                }
                val crashStart = System.nanoTime()
                primary.dockerClient
                    .killContainerCmd(primary.containerId)
                    .withSignal("KILL")
                    .exec()
                primary.dockerClient.startContainerCmd(primary.containerId).exec()
                // Docker can assign a different ephemeral host port when restarting the container.
                val restartedPort =
                    primary.dockerClient
                        .inspectContainerCmd(primary.containerId)
                        .exec()
                        .networkSettings.ports.bindings[
                        com.github.dockerjava.api.model.ExposedPort
                            .tcp(5432),
                    ]!![0]
                        .hostPortSpec
                        .toInt()
                source.setURL("jdbc:postgresql://${primary.host}:$restartedPort/${primary.databaseName}")
                val restartDeadline = System.nanoTime() + Duration.ofSeconds(30).toNanos()
                while (runCatching { scalar(source, "SELECT 1") }.getOrNull() != "1") {
                    check(System.nanoTime() < restartDeadline) { "Crash recovery did not become available" }
                    Thread.sleep(100)
                }
                assertEquals(latestDigest, snapshot(source))
                assertFalse(reserve("after-target"))
                val crashMs = (System.nanoTime() - crashStart) / 1_000_000
                command(primary, "tar", "-C", "/tmp", "-cf", "/tmp/tw-recovery.tar", "tw-base", "tw-archive")
                val archive = Files.createTempFile("trustweave-wal-", ".tar")
                val reportRoot = Path.of("build/reports/reliability")
                Files.createDirectories(reportRoot)
                try {
                    primary.copyFileFromContainer("/tmp/tw-recovery.tar", archive.toString())

                    fun recovery(missingWal: Boolean): GenericContainer<Nothing> =
                        GenericContainer<Nothing>("postgres:16-alpine").apply {
                            val logPath = reportRoot.resolve(if (missingWal) "wal-missing.log" else "wal-recovery.log")
                            Files.writeString(logPath, "")
                            withLogConsumer { frame ->
                                Files.writeString(logPath, frame.utf8String, java.nio.file.StandardOpenOption.APPEND)
                            }
                            withExposedPorts(5432)
                            withCopyFileToContainer(MountableFile.forHostPath(archive), "/tmp/tw-recovery.tar")
                            val remove = if (missingWal) "rm /tmp/tw-archive/$targetSegment; " else ""
                            val launch =
                                if (missingWal) {
                                    "set +e; gosu postgres postgres -D /tmp/tw-base -c listen_addresses='*'; " +
                                        "echo RECOVERY_EXIT=${'$'}?; exec tail -f /dev/null"
                                } else {
                                    "exec gosu postgres postgres -D /tmp/tw-base -c listen_addresses='*'"
                                }
                            withCreateContainerCmdModifier { it.withEntrypoint("sh", "-ec") }
                            withCommand(
                                *arrayOf(
                                    """
                                    tar -C /tmp -xf /tmp/tw-recovery.tar
                                    ${remove}cat >> /tmp/tw-base/postgresql.auto.conf <<'CONFIG'
                                    restore_command = 'cp /tmp/tw-archive/%f %p'
                                    recovery_target_name = 'trustweave_qualified'
                                    recovery_target_action = 'promote'
                                    archive_mode = 'off'
                                    CONFIG
                                    touch /tmp/tw-base/recovery.signal
                                    chown -R postgres:postgres /tmp/tw-base /tmp/tw-archive
                                    $launch
                                    """.trimIndent(),
                                ),
                            )
                            waitingFor(
                                Wait
                                    .forLogMessage(
                                        if (missingWal) {
                                            ".*RECOVERY_EXIT=1.*\\n"
                                        } else {
                                            ".*database system is ready to accept connections.*\\n"
                                        },
                                        1,
                                    ).withStartupTimeout(Duration.ofSeconds(45)),
                            )
                            withStartupAttempts(1)
                        }
                    val restoreStart = System.nanoTime()
                    recovery(false).use { restored ->
                        restored.start()
                        val restoredSource =
                            source(
                                "jdbc:postgresql://${restored.host}:${restored.getMappedPort(5432)}/${primary.databaseName}",
                                primary.username,
                                primary.password,
                            )
                        assertEquals("f", scalar(restoredSource, "SELECT pg_is_in_recovery()"))
                        assertEquals(targetDigest, snapshot(restoredSource))
                        val recovered = PostgresIntentLedger(restoredSource)
                        assertEquals(2L, recovered.healthSnapshot().pending)
                        assertEquals(1L, recovered.healthSnapshot().settled)
                        assertEquals(1L, recovered.healthSnapshot().released)
                        for (id in listOf("before-backup", "post-backup-settled", "post-backup-released", "post-backup-pending")) {
                            assertFalse(recovered.reserve(hash("wal"), hash(id), hash("fresh-$id"), policy))
                            assertFalse(recovered.reserve(hash("different"), hash("different-$id"), hash(id), policy))
                        }
                        assertTrue(recovered.reconcile("wal", "post-backup-released", SettlementOutcome.RELEASED, "journal:not-executed"))
                        assertFalse(recovered.reconcile("wal", "post-backup-released", SettlementOutcome.SETTLED, "conflict"))
                        assertTrue(recovered.reserve(hash("wal"), hash("valid-new"), hash("valid-new"), policy))
                        assertEquals(
                            "5:40",
                            scalar(
                                restoredSource,
                                "SELECT occurrence_count || ':' || spent FROM vi_budget_accounts WHERE scope='${hash("wal")}'",
                            ),
                        )
                        Files.writeString(reportRoot.resolve("wal-recovery.log"), restored.logs)
                    }
                    val restoreMs = (System.nanoTime() - restoreStart) / 1_000_000
                    // A missing post-backup WAL segment must not yield a writable, silently stale primary.
                    recovery(true).use { broken ->
                        runCatching { broken.start() }
                        val logs = broken.logs
                        Files.writeString(reportRoot.resolve("wal-missing.log"), logs)
                        assertTrue(logs.contains("recovery ended before configured recovery target was reached"), logs)
                        assertFalse(logs.contains("database system is ready to accept connections"), logs)
                    }
                    Files.writeString(
                        reportRoot.resolve("wal-recovery.json"),
                        reportJson.encodeToString(
                            buildJsonObject {
                                put("base_backup_verified", true)
                                put("corrupt_backup_rejected", true)
                                put("base_digest", backupDigest)
                                put("target_digest", targetDigest)
                                put("latest_digest", latestDigest)
                                put("target_segment", targetSegment)
                                put("crash_restart_ms", crashMs)
                                put("restore_and_assertions_ms", restoreMs)
                                put("post_backup_records_recovered", 3)
                                put("after_target_records_excluded", 1)
                                put("missing_wal_failed_closed", true)
                                put(
                                    "scope",
                                    "Local component PITR and crash recovery; no production RPO/RTO or external payment-journal claim",
                                )
                            },
                        ),
                    )
                } finally {
                    Files.deleteIfExists(archive)
                }
            }
    }
}
