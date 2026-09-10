package org.trustweave.observability

import com.zaxxer.hikari.HikariDataSource
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.sql.Connection
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

/** Fixed labels only: request paths, identities, tokens and exception text never enter telemetry. */
public enum class HostKind { STATUS_LIST, VC_API, OID4VCI, DID_REGISTRAR, TRUST_REGISTRY, AVP_AUTHORIZATION }

public enum class HostPhase { VERIFY, SIGN, PROVIDER, DATABASE_ACQUIRE }

public enum class HostOutcome { SUCCESS, CLIENT_ERROR, SERVER_ERROR, CANCELLED, REJECTED }

/**
 * Host-owned telemetry and optional admission control. No exporter or global SDK is installed.
 * Queue timing starts at the application boundary; it does not measure the Netty socket queue.
 * Pass a bounded asynchronous OpenTelemetry SDK to export spans. Close that SDK in the host.
 */
public class HostTelemetry
    @JvmOverloads
    constructor(
        private val openTelemetry: OpenTelemetry = OpenTelemetry.noop(),
        maxConcurrentRequests: Int = Int.MAX_VALUE,
        private val maxQueuedRequests: Int = 0,
        private val queueTimeoutMillis: Long = 1000,
    ) {
        init {
            require(maxConcurrentRequests > 0)
            require(maxQueuedRequests >= 0)
            require(queueTimeoutMillis in 1..60_000)
        }

        private val tracer = openTelemetry.getTracer("org.trustweave.host", "0.7.0")
        private val permits = Semaphore(maxConcurrentRequests)
        private val active = AtomicInteger()
        private val queued = AtomicInteger()
        private val lock = Any()
        private val requestCounts = Array(HostKind.entries.size) { LongArray(HostOutcome.entries.size) }
        private val durations = Array(HostKind.entries.size) { Histogram() }
        private val queueDurations = Array(HostKind.entries.size) { Histogram() }
        private val phaseDurations = Array(HostPhase.entries.size) { Histogram() }
        private val phaseFailures = LongArray(HostPhase.entries.size)

        internal suspend fun request(
            kind: HostKind,
            parent: Context,
            status: () -> Int,
            onSpan: (Span) -> Unit,
            onRejected: suspend () -> Unit,
            block: suspend () -> Unit,
        ) {
            val started = System.nanoTime()
            val span =
                tracer
                    .spanBuilder("trustweave.${kind.label()}")
                    .setParent(parent)
                    .setSpanKind(SpanKind.SERVER)
                    .startSpan()
            var acquired = false
            var activeCounted = false
            var outcome = HostOutcome.SERVER_ERROR
            var queueElapsed = 0.0
            try {
                onSpan(span)
                acquired = permits.tryAcquire()
                if (!acquired) {
                    val waiting = queued.incrementAndGet()
                    try {
                        if (waiting <= maxQueuedRequests) {
                            val completed =
                                withTimeoutOrNull(queueTimeoutMillis) {
                                    permits.acquire()
                                    acquired = true
                                    true
                                } == true
                            if (!completed && acquired) {
                                permits.release()
                                acquired = false
                            }
                        }
                    } finally {
                        queued.decrementAndGet()
                    }
                }
                queueElapsed = secondsSince(started)
                if (!acquired) {
                    outcome = HostOutcome.REJECTED
                    span.setAttribute("http.response.status_code", 503)
                    onRejected()
                    return
                }
                active.incrementAndGet()
                activeCounted = true
                withContext(parent.with(span).asContextElement()) { block() }
                val responseStatus = status()
                span.setAttribute("http.response.status_code", responseStatus.toLong())
                outcome =
                    when {
                        responseStatus >= 500 -> HostOutcome.SERVER_ERROR
                        responseStatus >= 400 -> HostOutcome.CLIENT_ERROR
                        else -> HostOutcome.SUCCESS
                    }
            } catch (cancelled: CancellationException) {
                outcome = HostOutcome.CANCELLED
                throw cancelled
            } catch (failure: Throwable) {
                span.setAttribute("http.response.status_code", 500)
                throw failure
            } finally {
                if (acquired) {
                    if (activeCounted) active.decrementAndGet()
                    permits.release()
                }
                if (queueElapsed == 0.0) queueElapsed = secondsSince(started)
                synchronized(lock) {
                    requestCounts[kind.ordinal][outcome.ordinal]++
                    durations[kind.ordinal].record(secondsSince(started))
                    queueDurations[kind.ordinal].record(queueElapsed)
                }
                span.setAttribute("trustweave.outcome", outcome.label())
                if (outcome == HostOutcome.SERVER_ERROR || outcome == HostOutcome.REJECTED) span.setStatus(StatusCode.ERROR)
                span.end()
            }
        }

        /** Child spans preserve coroutine context without recording operation arguments or exception details. */
        public suspend fun <T> phase(
            phase: HostPhase,
            block: suspend () -> T,
        ): T {
            val started = System.nanoTime()
            val span =
                tracer
                    .spanBuilder("trustweave.${phase.label()}")
                    .setSpanKind(
                        if (phase ==
                            HostPhase.PROVIDER
                        ) {
                            SpanKind.CLIENT
                        } else {
                            SpanKind.INTERNAL
                        },
                    ).startSpan()
            var failed = false
            try {
                return withContext(Context.current().with(span).asContextElement()) { block() }
            } catch (cancelled: CancellationException) {
                span.setAttribute("trustweave.outcome", "cancelled")
                throw cancelled
            } catch (failure: Exception) {
                failed = true
                span.setStatus(StatusCode.ERROR)
                throw failure
            } finally {
                synchronized(lock) {
                    phaseDurations[phase.ordinal].record(secondsSince(started))
                    if (failed) phaseFailures[phase.ordinal]++
                }
                span.end()
            }
        }

        /** Wraps connection acquisition only; returned connections and their transaction semantics are unchanged. */
        public fun observeDataSource(delegate: DataSource): DataSource =
            object : DataSource by delegate {
                override fun getConnection(): Connection = acquire { delegate.connection }

                override fun getConnection(
                    username: String?,
                    password: String?,
                ): Connection = acquire { delegate.getConnection(username, password) }
            }

        private fun acquire(block: () -> Connection): Connection {
            val started = System.nanoTime()
            val span = tracer.spanBuilder("trustweave.database_acquire").startSpan()
            var failed = false
            try {
                return span.makeCurrent().use { block() }
            } catch (failure: Exception) {
                failed = true
                span.setStatus(StatusCode.ERROR)
                throw failure
            } finally {
                synchronized(lock) {
                    phaseDurations[HostPhase.DATABASE_ACQUIRE.ordinal].record(secondsSince(started))
                    if (failed) phaseFailures[HostPhase.DATABASE_ACQUIRE.ordinal]++
                }
                span.end()
            }
        }

        /** Fixed-size snapshot. Optional Hikari state uses in-memory MXBean counters and never opens a connection. */
        public fun prometheus(pool: DataSource? = null): String =
            buildString {
                append("# HELP trustweave_host_active_requests Admitted requests currently executing.\n")
                append("# TYPE trustweave_host_active_requests gauge\ntrustweave_host_active_requests ${active.get()}\n")
                append("# HELP trustweave_host_queued_requests Requests waiting for admission.\n")
                append("# TYPE trustweave_host_queued_requests gauge\ntrustweave_host_queued_requests ${queued.get()}\n")
                synchronized(lock) {
                    append("# HELP trustweave_host_requests_total Completed request outcomes including rejection and cancellation.\n")
                    append("# TYPE trustweave_host_requests_total counter\n")
                    for (kind in HostKind.entries) {
                        for (outcome in HostOutcome.entries) {
                            append(
                                "trustweave_host_requests_total{host=\"${kind.label()}\",outcome=\"${outcome.label()}\"} ${requestCounts[kind.ordinal][outcome.ordinal]}\n",
                            )
                        }
                    }
                    append("# HELP trustweave_host_request_seconds Admission plus application handler duration in seconds.\n")
                    append("# TYPE trustweave_host_request_seconds histogram\n")
                    for (kind in HostKind.entries) {
                        durations[kind.ordinal].write(
                            this,
                            "trustweave_host_request_seconds",
                            "host=\"${kind.label()}\"",
                        )
                    }
                    append("# HELP trustweave_host_queue_seconds Time spent obtaining admission in seconds.\n")
                    append("# TYPE trustweave_host_queue_seconds histogram\n")
                    for (kind in HostKind.entries) {
                        queueDurations[kind.ordinal].write(
                            this,
                            "trustweave_host_queue_seconds",
                            "host=\"${kind.label()}\"",
                        )
                    }
                    append("# HELP trustweave_host_phase_seconds Measured operation phase duration in seconds.\n")
                    append("# TYPE trustweave_host_phase_seconds histogram\n")
                    for (phase in HostPhase.entries) {
                        phaseDurations[phase.ordinal].write(
                            this,
                            "trustweave_host_phase_seconds",
                            "phase=\"${phase.label()}\"",
                        )
                    }
                    append("# HELP trustweave_host_phase_failures_total Operation phase failures excluding cancellation.\n")
                    append("# TYPE trustweave_host_phase_failures_total counter\n")
                    for (phase in HostPhase.entries) {
                        append(
                            "trustweave_host_phase_failures_total{phase=\"${phase.label()}\"} ${phaseFailures[phase.ordinal]}\n",
                        )
                    }
                }
                val hikari = pool as? HikariDataSource
                val bean = hikari?.hikariPoolMXBean
                append("# HELP trustweave_host_pool_available Whether a live Hikari pool is available for telemetry.\n")
                append(
                    "# TYPE trustweave_host_pool_available gauge\ntrustweave_host_pool_available ${if (bean != null && !hikari.isClosed) 1 else 0}\n",
                )
                if (bean != null && !hikari.isClosed) {
                    for ((name, value) in mapOf(
                        "active" to bean.activeConnections,
                        "idle" to bean.idleConnections,
                        "waiting" to bean.threadsAwaitingConnection,
                        "capacity" to hikari.maximumPoolSize,
                    )) {
                        append("# HELP trustweave_host_pool_$name Hikari pool $name connections.\n")
                        append("# TYPE trustweave_host_pool_$name gauge\ntrustweave_host_pool_$name $value\n")
                    }
                }
            }

        private class Histogram {
            private val buckets = LongArray(BOUNDS.size)
            private var count = 0L
            private var sum = 0.0

            fun record(seconds: Double) {
                count++
                sum += seconds
                BOUNDS.forEachIndexed { i, bound -> if (seconds <= bound) buckets[i]++ }
            }

            fun write(
                out: StringBuilder,
                name: String,
                labels: String,
            ) {
                BOUNDS.forEachIndexed { i, bound -> out.append("${name}_bucket{$labels,le=\"$bound\"} ${buckets[i]}\n") }
                out.append("${name}_bucket{$labels,le=\"+Inf\"} $count\n${name}_count{$labels} $count\n${name}_sum{$labels} $sum\n")
            }
        }

        private companion object {
            val BOUNDS = doubleArrayOf(0.001, 0.01, 0.1, 0.5, 1.0, 5.0, 30.0)

            fun secondsSince(started: Long): Double = (System.nanoTime() - started) / 1_000_000_000.0

            fun Enum<*>.label(): String = name.lowercase(java.util.Locale.ROOT)
        }
    }
