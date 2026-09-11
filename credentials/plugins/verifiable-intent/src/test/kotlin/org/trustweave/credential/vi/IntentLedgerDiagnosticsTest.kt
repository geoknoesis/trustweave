package org.trustweave.credential.vi

import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.trustweave.credential.vi.verification.IntentLedgerDiagnostics
import org.trustweave.credential.vi.verification.IntentLedgerHealth
import org.trustweave.credential.vi.verification.PostgresIntentLedger
import java.sql.SQLException
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class IntentLedgerDiagnosticsTest {
    private val reserve = IntentLedgerDiagnostics.Operation.RESERVE

    @Test
    fun `outcomes preserve results exceptions and privacy`() {
        val diagnostics = IntentLedgerDiagnostics()
        assertTrue(diagnostics.observe(reserve) { true })
        assertFalse(diagnostics.observe(reserve) { false })
        val errors =
            listOf(
                SQLException("password=secret database.internal"),
                IllegalArgumentException("private-mandate"),
                IllegalStateException("private-provider"),
                CancellationException("private-nonce"),
            )
        for (error in errors) {
            val actual = assertThrows(error.javaClass) { diagnostics.observe(reserve) { throw error } }
            assertSame(error, actual)
        }
        val text = diagnostics.prometheus()
        for (outcome in listOf("success", "rejected", "invalid", "storage_failure", "failure", "cancelled")) {
            assertTrue(text.contains("trustweave_intent_operations_total{operation=\"reserve\",outcome=\"$outcome\"} 1\n"))
        }
        assertTrue(text.contains("trustweave_intent_operation_seconds_count{operation=\"reserve\"} 6\n"))
        assertTrue(text.contains("trustweave_intent_in_flight{operation=\"reserve\"} 0\n"))
        assertFalse(text.contains("private"))
        assertFalse(text.contains("secret"))
        assertFalse(text.contains("database.internal"))
    }

    @Test
    fun `concurrent operations retain bounded series and coherent cumulative histograms`() {
        val diagnostics = IntentLedgerDiagnostics()
        val initialSeries = diagnostics.prometheus().lines().count { it.isNotBlank() && !it.startsWith("#") }
        val workers = Executors.newFixedThreadPool(8)
        try {
            workers
                .invokeAll(
                    (1..8).map {
                        Callable { repeat(10_000) { diagnostics.observe(reserve) { true } } }
                    },
                ).forEach { it.get(30, TimeUnit.SECONDS) }
            val text = diagnostics.prometheus()
            assertEquals(initialSeries, text.lines().count { it.isNotBlank() && !it.startsWith("#") })
            assertTrue(text.length < 12_000)
            assertTrue(text.contains("trustweave_intent_operations_total{operation=\"reserve\",outcome=\"success\"} 80000\n"))
            val buckets =
                text
                    .lines()
                    .filter { it.startsWith("trustweave_intent_operation_seconds_bucket{operation=\"reserve\"") }
                    .map { it.substringAfterLast(' ').toLong() }
            assertEquals(80_000L, buckets.last())
            assertTrue(buckets.zipWithNext().all { (left, right) -> left <= right })
        } finally {
            workers.shutdownNow()
        }
    }

    @Test
    fun `in flight work is visible while export does not wait for database work`() {
        val diagnostics = IntentLedgerDiagnostics()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val worker = Executors.newSingleThreadExecutor()
        try {
            val task =
                worker.submit {
                    diagnostics.observe(reserve) {
                        entered.countDown()
                        release.await(10, TimeUnit.SECONDS)
                    }
                }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertTrue(diagnostics.prometheus().contains("trustweave_intent_in_flight{operation=\"reserve\"} 1\n"))
            release.countDown()
            task.get(5, TimeUnit.SECONDS)
            assertTrue(diagnostics.prometheus().contains("trustweave_intent_in_flight{operation=\"reserve\"} 0\n"))
        } finally {
            release.countDown()
            worker.shutdownNow()
        }
    }

    @Test
    fun `failed health polling withdraws stale gauges and preserves unknown ages`() {
        val diagnostics = IntentLedgerDiagnostics()
        val operation = IntentLedgerDiagnostics.Operation.HEALTH
        assertTrue(diagnostics.prometheus().contains("trustweave_intent_health_poll_success 0\n"))
        assertFalse(diagnostics.prometheus().contains("trustweave_intent_reconciliation_pending "))
        diagnostics.observe(operation) { IntentLedgerHealth(3, 1, 1, null) }
        assertTrue(diagnostics.prometheus().contains("trustweave_intent_oldest_pending_known 0\n"))
        assertFalse(diagnostics.prometheus().contains("trustweave_intent_oldest_pending_age_seconds "))
        assertThrows(SQLException::class.java) { diagnostics.observe(operation) { throw SQLException("secret") } }
        assertTrue(diagnostics.prometheus().contains("trustweave_intent_health_poll_success 0\n"))
        assertFalse(diagnostics.prometheus().contains("trustweave_intent_reconciliation_pending "))
        diagnostics.observe(operation) { IntentLedgerHealth(0, 1, 2, null) }
        assertTrue(diagnostics.prometheus().contains("trustweave_intent_health_poll_success 1\n"))
        assertTrue(diagnostics.prometheus().contains("trustweave_intent_oldest_pending_known 1\n"))
        assertTrue(diagnostics.prometheus().contains("trustweave_intent_oldest_pending_age_seconds 0\n"))
    }

    @Test
    fun `actual ledger connection failures are measured before a connection exists`() {
        val source = org.postgresql.ds.PGSimpleDataSource()
        val failure = SQLException("jdbc:private?password=secret")
        val unavailable =
            object : javax.sql.DataSource by source {
                override fun getConnection(): java.sql.Connection = throw failure
            }
        val ledger = PostgresIntentLedger(unavailable)
        assertSame(failure, assertThrows(SQLException::class.java) { ledger.initializeSchema() })
        assertSame(failure, assertThrows(SQLException::class.java) { ledger.healthSnapshot() })
        assertSame(
            failure,
            assertThrows(SQLException::class.java) {
                ledger.reconcile("secret", "secret", org.trustweave.credential.vi.verification.SettlementOutcome.SETTLED, "secret")
            },
        )
        val text = ledger.diagnostics.prometheus()
        for (operation in listOf("migrate", "health", "reconcile")) {
            assertTrue(text.contains("trustweave_intent_operations_total{operation=\"$operation\",outcome=\"storage_failure\"} 1\n"))
        }
        assertFalse(text.contains("secret"))
    }
}
