package org.trustweave.credential.didcomm.crypto.interop

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.didcommx.didcomm.diddoc.DIDDoc
import org.didcommx.didcomm.diddoc.DIDDocResolver
import org.trustweave.did.model.DidDocument
import java.util.Optional

/**
 * Adapts a suspend TrustWeave DID resolver to didcomm-java's synchronous [DIDDocResolver].
 *
 * **Threading:** didcomm-java invokes [resolve] from its own threads. This implementation uses
 * [runBlocking] on [dispatcher] (default [Dispatchers.IO]). The supplied suspend resolver must:
 * - **Not** call back into didcomm pack/unpack on the same thread (deadlock risk).
 * - Complete within [resolveTimeoutMs] or resolution fails with [IllegalStateException].
 *
 * @param resolveTimeoutMs Maximum time for a single DID resolution; prevents hung resolvers from blocking crypto indefinitely.
 */
class BlockingDidDocResolver(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val resolveTimeoutMs: Long = 30_000L,
    private val suspendResolve: suspend (String) -> DidDocument?,
) : DIDDocResolver {

    init {
        require(resolveTimeoutMs > 0) { "resolveTimeoutMs must be positive" }
    }

    override fun resolve(did: String): Optional<DIDDoc> =
        runBlocking(dispatcher) {
            try {
                withTimeout(resolveTimeoutMs) {
                    val doc = suspendResolve(did) ?: return@withTimeout Optional.empty()
                    Optional.of(TrustWeaveDidDocMapper.toDidComm(doc))
                }
            } catch (_: TimeoutCancellationException) {
                throw IllegalStateException(
                    "DID resolution timed out after ${resolveTimeoutMs}ms for: $did. " +
                        "Ensure resolveDid completes quickly and does not deadlock with DIDComm operations.",
                )
            }
        }
}
