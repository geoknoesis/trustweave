package org.trustweave.observability

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import org.trustweave.core.telemetry.Operation
import org.trustweave.core.telemetry.Outcome
import org.trustweave.core.telemetry.Telemetry
import org.trustweave.core.telemetry.TelemetryEvent
import org.trustweave.core.telemetry.TelemetrySink
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLongArray

/**
 * Bridges library events onto the host's OpenTelemetry and Prometheus surfaces.
 *
 * [org.trustweave.core.telemetry.Telemetry] is deliberately dependency-free so the core modules do
 * not acquire OpenTelemetry; this is the adapter that gives those events somewhere to go. Install
 * it once, alongside [HostObservability]:
 *
 * ```kotlin
 * val library = LibraryTelemetry(openTelemetry)
 * Telemetry.install(library)
 * // library.prometheus() is appended to the host scrape by HostObservability
 * ```
 *
 * Each event becomes a short span carrying the correlation id the host put in the coroutine
 * context, so a library operation joins the request that caused it, plus counters and a duration
 * histogram per operation. Attributes arrive already bounded and secret-free from the library
 * side; nothing here adds an identifier of its own.
 */
public class LibraryTelemetry
    @JvmOverloads
    constructor(
        openTelemetry: OpenTelemetry = OpenTelemetry.noop(),
        private val emitSpans: Boolean = true,
    ) : TelemetrySink {
        private val tracer = openTelemetry.getTracer("org.trustweave.library")

        // One counter per (operation, outcome) and one histogram per operation. Both dimensions
        // are closed enums, so the label space cannot grow at runtime.
        private val counts = AtomicLongArray(Operation.entries.size * Outcome.entries.size)
        private val durations = Array(Operation.entries.size) { Histogram() }
        private val lock = Any()

        override fun record(event: TelemetryEvent) {
            counts.incrementAndGet(event.operation.ordinal * Outcome.entries.size + event.outcome.ordinal)
            synchronized(lock) {
                durations[event.operation.ordinal].observe(event.durationNanos / 1_000_000_000.0)
            }
            if (!emitSpans) return

            // A zero-length span placed at the end of the operation: the library already measured
            // the duration, and re-timing it here would double-count.
            val span =
                tracer
                    .spanBuilder(event.operation.label())
                    .setSpanKind(SpanKind.INTERNAL)
                    .setStartTimestamp(
                        System.currentTimeMillis() * 1_000_000L - event.durationNanos,
                        TimeUnit.NANOSECONDS,
                    ).startSpan()
            try {
                span.setAttribute(OPERATION, event.operation.label())
                span.setAttribute(OUTCOME, event.outcome.name.lowercase())
                event.reason?.let { span.setAttribute(REASON, it) }
                event.correlationId?.let { span.setAttribute(CORRELATION, it) }
                event.attributes.forEach { (key, value) -> span.setAttribute(AttributeKey.stringKey(key), value) }
                if (event.outcome == Outcome.FAILURE) {
                    span.setStatus(StatusCode.ERROR, event.reason ?: "failure")
                }
            } finally {
                span.end()
            }
        }

        /** Prometheus exposition for the library surface; append to the host scrape. */
        public fun prometheus(): String =
            buildString {
                append("# HELP trustweave_library_operations_total Completed SDK operations by outcome.\n")
                append("# TYPE trustweave_library_operations_total counter\n")
                for (operation in Operation.entries) {
                    for (outcome in Outcome.entries) {
                        val value = counts.get(operation.ordinal * Outcome.entries.size + outcome.ordinal)
                        append(
                            "trustweave_library_operations_total{operation=\"${operation.label()}\"," +
                                "outcome=\"${outcome.name.lowercase()}\"} $value\n",
                        )
                    }
                }
                append("# HELP trustweave_library_operation_seconds SDK operation duration in seconds.\n")
                append("# TYPE trustweave_library_operation_seconds histogram\n")
                synchronized(lock) {
                    for (operation in Operation.entries) {
                        durations[operation.ordinal].write(
                            this,
                            "trustweave_library_operation_seconds",
                            "operation=\"${operation.label()}\"",
                        )
                    }
                }
            }

        /** Fixed bucket histogram; the same shape the host surface already exposes. */
        private class Histogram {
            private val bounds = doubleArrayOf(0.005, 0.025, 0.1, 0.5, 1.0, 5.0, 30.0)
            private val buckets = LongArray(bounds.size + 1)
            private var count = 0L
            private var sum = 0.0

            fun observe(seconds: Double) {
                var index = bounds.size
                for (i in bounds.indices) {
                    if (seconds <= bounds[i]) {
                        index = i
                        break
                    }
                }
                buckets[index]++
                count++
                sum += seconds
            }

            fun write(
                target: StringBuilder,
                name: String,
                labels: String,
            ) {
                var cumulative = 0L
                for (i in bounds.indices) {
                    cumulative += buckets[i]
                    target.append("$name{$labels,le=\"${bounds[i]}\"} $cumulative\n")
                }
                cumulative += buckets[bounds.size]
                target.append("$name{$labels,le=\"+Inf\"} $cumulative\n")
                target.append("${name}_sum{$labels} $sum\n")
                target.append("${name}_count{$labels} $count\n")
            }
        }

        private companion object {
            val OPERATION: AttributeKey<String> = AttributeKey.stringKey("trustweave.operation")
            val OUTCOME: AttributeKey<String> = AttributeKey.stringKey("trustweave.outcome")
            val REASON: AttributeKey<String> = AttributeKey.stringKey("trustweave.reason")
            val CORRELATION: AttributeKey<String> = AttributeKey.stringKey("trustweave.request_id")

            fun Operation.label(): String = name.lowercase()
        }
    }
