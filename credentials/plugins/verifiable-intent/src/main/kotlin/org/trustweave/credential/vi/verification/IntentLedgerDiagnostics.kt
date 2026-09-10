package org.trustweave.credential.vi.verification

import kotlinx.coroutines.CancellationException

/**
 * Bounded, process-local ledger metrics. Only fixed operation and outcome sets are retained;
 * no identifiers, request samples, exception text or database access occur during export.
 * Share one instance across ledger objects representing the same monitored database.
 * Hosts periodically call healthSnapshot using a monitoring role and expose prometheus on
 * their protected metrics endpoint. This class starts no threads or network listeners.
 */
public class IntentLedgerDiagnostics {
    private val lock = Any()
    private val counts = Array(Operation.entries.size) { LongArray(Outcome.entries.size) }
    private val inFlight = LongArray(Operation.entries.size)
    private val buckets = Array(Operation.entries.size) { LongArray(bounds.size + 1) }
    private val sums = DoubleArray(Operation.entries.size)
    private var health: IntentLedgerHealth? = null
    private var healthSuccess = false
    private var healthTimestamp = 0L

    internal enum class Operation { MIGRATE, RESERVE, RECONCILE, HEALTH }

    private enum class Outcome { SUCCESS, REJECTED, INVALID, STORAGE_FAILURE, FAILURE, CANCELLED }

    internal fun <T> observe(
        operation: Operation,
        action: () -> T,
    ): T {
        val started = System.nanoTime()
        synchronized(lock) { inFlight[operation.ordinal]++ }
        var outcome = Outcome.FAILURE
        try {
            val result = action()
            outcome = if (result == false) Outcome.REJECTED else Outcome.SUCCESS
            if (result is IntentLedgerHealth) {
                synchronized(lock) {
                    health = result
                    healthSuccess = true
                    healthTimestamp = System.currentTimeMillis() / 1000
                }
            }
            return result
        } catch (cancelled: CancellationException) {
            outcome = Outcome.CANCELLED
            throw cancelled
        } catch (failure: java.sql.SQLException) {
            outcome = Outcome.STORAGE_FAILURE
            throw failure
        } catch (invalid: IllegalArgumentException) {
            outcome = Outcome.INVALID
            throw invalid
        } finally {
            val seconds = (System.nanoTime() - started).coerceAtLeast(0).toDouble() / 1e9
            synchronized(lock) {
                val index = operation.ordinal
                inFlight[index]--
                counts[index][outcome.ordinal]++
                for (i in bounds.indices) if (seconds <= bounds[i]) buckets[index][i]++
                buckets[index][bounds.size]++
                sums[index] += seconds
                if (operation == Operation.HEALTH && outcome != Outcome.SUCCESS) healthSuccess = false
            }
        }
    }

    /** Prometheus text format 0.0.4. Constant-size snapshots remain available during database loss. */
    public fun prometheus(): String =
        synchronized(lock) {
            buildString {
                appendLine("# HELP trustweave_intent_operations_total Completed ledger operations by bounded outcome.")
                appendLine("# TYPE trustweave_intent_operations_total counter")
                for (operation in Operation.entries) {
                    for (outcome in Outcome.entries) {
                        appendLine(
                            "trustweave_intent_operations_total{operation=\"${operation.name.lowercase()}\"," +
                                "outcome=\"${outcome.name.lowercase()}\"} ${counts[operation.ordinal][outcome.ordinal]}",
                        )
                    }
                }
                appendLine("# HELP trustweave_intent_in_flight Operations currently executing, including connection and lock waits.")
                appendLine("# TYPE trustweave_intent_in_flight gauge")
                for (operation in Operation.entries) {
                    appendLine("trustweave_intent_in_flight{operation=\"${operation.name.lowercase()}\"} ${inFlight[operation.ordinal]}")
                }
                appendLine("# HELP trustweave_intent_operation_seconds Ledger duration including connection and transaction completion.")
                appendLine("# TYPE trustweave_intent_operation_seconds histogram")
                for (operation in Operation.entries) {
                    val name = operation.name.lowercase()
                    val index = operation.ordinal
                    for (i in bounds.indices) {
                        appendLine(
                            "trustweave_intent_operation_seconds_bucket{operation=\"$name\",le=\"${bounds[i]}\"} ${buckets[index][i]}",
                        )
                    }
                    appendLine("trustweave_intent_operation_seconds_bucket{operation=\"$name\",le=\"+Inf\"} ${buckets[index][bounds.size]}")
                    appendLine("trustweave_intent_operation_seconds_count{operation=\"$name\"} ${buckets[index][bounds.size]}")
                    appendLine("trustweave_intent_operation_seconds_sum{operation=\"$name\"} ${sums[index]}")
                }

                fun gauge(
                    name: String,
                    help: String,
                    value: Long,
                ) {
                    appendLine("# HELP $name $help")
                    appendLine("# TYPE $name gauge")
                    appendLine("$name $value")
                }
                gauge(
                    "trustweave_intent_health_poll_success",
                    "Whether the latest health poll succeeded; zero before first poll.",
                    if (healthSuccess) 1 else 0,
                )
                gauge(
                    "trustweave_intent_health_last_success_timestamp_seconds",
                    "Unix timestamp of last successful health poll; zero before first poll.",
                    healthTimestamp,
                )
                // Do not report stale pending counts as current health after a failed poll.
                if (healthSuccess) {
                    health?.let { snapshot ->
                        gauge("trustweave_intent_reconciliation_pending", "Pending reservations at last successful poll.", snapshot.pending)
                        gauge("trustweave_intent_reconciliation_settled", "Settled reservations at last successful poll.", snapshot.settled)
                        gauge(
                            "trustweave_intent_reconciliation_released",
                            "Released reservations at last successful poll.",
                            snapshot.released,
                        )
                        val known = snapshot.pending == 0L || snapshot.oldestPendingEpochSeconds != null
                        gauge(
                            "trustweave_intent_oldest_pending_known",
                            "Whether pending age is known; zero for undated legacy reservations.",
                            if (known) 1 else 0,
                        )
                        if (known) {
                            val age = snapshot.oldestPendingEpochSeconds?.let { (healthTimestamp - it).coerceAtLeast(0) } ?: 0
                            gauge(
                                "trustweave_intent_oldest_pending_age_seconds",
                                "Oldest pending age measured at last successful poll.",
                                age,
                            )
                        }
                    }
                }
            }
        }

    private companion object {
        val bounds = doubleArrayOf(0.01, 0.1, 1.0, 5.0, 10.0)
    }
}
