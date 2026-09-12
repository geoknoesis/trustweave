package org.trustweave.core.telemetry

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Library-level telemetry: what the SDK did, how it ended, and how long it took.
 *
 * Instrumentation used to stop at the HTTP boundary. A host running the embedded servers got
 * spans, metrics and a request id, but the work underneath — resolving a DID, signing with a KMS,
 * reading a wallet, verifying a credential — emitted nothing, so a failed verification could not
 * be joined to a cause. This is the missing half.
 *
 * It deliberately depends on nothing but the Kotlin standard library. The core modules must not
 * acquire an OpenTelemetry or Ktor dependency because a host wants tracing; `:observability`
 * bridges these events onto whatever the host actually runs.
 *
 * ## Installing
 *
 * ```kotlin
 * Telemetry.install(sink)          // process-wide, typically once at startup
 * ```
 *
 * Nothing is recorded until a host installs a sink, and the default sink costs one volatile read
 * per operation.
 *
 * ## Correlating
 *
 * A correlation id travels in the coroutine context, so a library event can be joined to the
 * request that caused it without threading a parameter through every public signature:
 *
 * ```kotlin
 * withContext(TelemetryContext(requestId)) { credentialService.verify(credential) }
 * ```
 */
public object Telemetry {
    @Volatile
    private var sink: TelemetrySink = TelemetrySink.None

    /** Replaces the process-wide sink. Returns the previous one so a host can restore it. */
    public fun install(sink: TelemetrySink): TelemetrySink {
        val previous = this.sink
        this.sink = sink
        return previous
    }

    /** Restores the no-op sink. */
    public fun uninstall(): TelemetrySink = install(TelemetrySink.None)

    /** The installed sink. Callers normally use [measure] rather than this. */
    public fun sink(): TelemetrySink = sink

    /** True when a host has installed something; lets hot paths skip building attributes. */
    public fun isInstalled(): Boolean = sink !== TelemetrySink.None

    /**
     * Times [block], reports the outcome, and returns its result.
     *
     * Cancellation is reported as [Outcome.CANCELLED] and rethrown, never as a failure — a
     * cancelled caller is not a failed operation, and conflating the two is what makes
     * cancellation bugs hard to see. A thrown [TelemetrySink] error is swallowed: telemetry
     * must not be able to fail an operation that otherwise succeeded.
     */
    public suspend fun <T> measure(
        operation: Operation,
        attributes: Map<String, String> = emptyMap(),
        block: suspend () -> T,
    ): T {
        if (!isInstalled()) return block()
        val correlationId = coroutineContext[TelemetryContext]?.correlationId
        val started = System.nanoTime()
        try {
            val result = block()
            emit(operation, Outcome.SUCCESS, started, null, correlationId, attributes)
            return result
        } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
            emit(operation, Outcome.CANCELLED, started, "cancelled", correlationId, attributes)
            throw cancelled
        } catch (failure: Throwable) {
            emit(operation, Outcome.FAILURE, started, reasonOf(failure), correlationId, attributes)
            throw failure
        }
    }

    /**
     * Reports an operation that completed normally but did not succeed — a verification that
     * returned invalid, a resolution that returned a failure result. These never throw, so
     * [measure] alone would record them as successes.
     */
    public suspend fun rejected(
        operation: Operation,
        reason: String,
        attributes: Map<String, String> = emptyMap(),
    ) {
        if (!isInstalled()) return
        emit(
            operation = operation,
            outcome = Outcome.REJECTED,
            startedNanos = System.nanoTime(),
            reason = reason,
            correlationId = coroutineContext[TelemetryContext]?.correlationId,
            attributes = attributes,
        )
    }

    private fun emit(
        operation: Operation,
        outcome: Outcome,
        startedNanos: Long,
        reason: String?,
        correlationId: String?,
        attributes: Map<String, String>,
    ) {
        val event =
            TelemetryEvent(
                operation = operation,
                outcome = outcome,
                durationNanos = (System.nanoTime() - startedNanos).coerceAtLeast(0),
                reason = reason,
                correlationId = correlationId,
                attributes = attributes,
            )
        try {
            sink.record(event)
        } catch (_: Throwable) {
            // A sink that throws has already lost the event; it must not also lose the operation.
        }
    }

    /**
     * A stable, low-cardinality reason code.
     *
     * The exception's message is deliberately not used: it can carry a DID, a file path, a key
     * identifier or a provider's raw error, and reason codes end up as metric labels.
     */
    private fun reasonOf(failure: Throwable): String = failure.javaClass.simpleName
}

/** What the SDK was doing. Kept coarse: these become metric labels. */
public enum class Operation {
    DID_RESOLVE,
    DID_CREATE,
    DID_UPDATE,
    DID_DEACTIVATE,
    KMS_GENERATE_KEY,
    KMS_SIGN,
    KMS_VERIFY,
    WALLET_STORE,
    WALLET_GET,
    WALLET_QUERY,
    WALLET_DELETE,
    CREDENTIAL_ISSUE,
    CREDENTIAL_VERIFY,
    CREDENTIAL_VERIFY_PRESENTATION,
    CREDENTIAL_REVOCATION_CHECK,
}

/**
 * How it ended.
 *
 * [REJECTED] is separate from [FAILURE] on purpose: a credential that fails verification is the
 * library working correctly, and a host that pages on it will page on ordinary traffic.
 */
public enum class Outcome { SUCCESS, REJECTED, FAILURE, CANCELLED }

/**
 * One completed library operation.
 *
 * @param reason stable low-cardinality code, never a raw message — these become metric labels.
 * @param attributes bounded, low-cardinality, and never secrets, key material or subject data.
 */
public data class TelemetryEvent(
    val operation: Operation,
    val outcome: Outcome,
    val durationNanos: Long,
    val reason: String? = null,
    val correlationId: String? = null,
    val attributes: Map<String, String> = emptyMap(),
)

/** Receives library events. Implementations must not throw and must not block. */
public fun interface TelemetrySink {
    public fun record(event: TelemetryEvent)

    public companion object {
        /** The default: records nothing. */
        public val None: TelemetrySink = TelemetrySink { }
    }
}

/**
 * Carries a host's correlation id down into library calls.
 *
 * The embedded servers put their `X-Request-ID` here, so a library event can be joined to the
 * request that caused it.
 */
public class TelemetryContext(
    public val correlationId: String,
) : AbstractCoroutineContextElement(TelemetryContext) {
    public companion object Key : CoroutineContext.Key<TelemetryContext>

    override fun toString(): String = "TelemetryContext($correlationId)"
}
