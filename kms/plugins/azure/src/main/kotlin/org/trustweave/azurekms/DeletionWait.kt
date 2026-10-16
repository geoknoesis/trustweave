package org.trustweave.azurekms

import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs the blocking [wait] for at most [timeoutMillis].
 *
 * Returns `true` when it completed and `false` when the time ran out. A timeout is deliberately a
 * return value, not an exception: `withTimeout` throws a `CancellationException` subtype that a
 * caller's cancellation handler would rethrow as if the caller itself had been cancelled. Genuine
 * cancellation of the calling coroutine still propagates as a `CancellationException`.
 */
internal suspend fun awaitCompletion(
    timeoutMillis: Long,
    wait: () -> Unit,
): Boolean =
    withTimeoutOrNull(timeoutMillis) {
        runInterruptible { wait() }
        true
    } ?: false
