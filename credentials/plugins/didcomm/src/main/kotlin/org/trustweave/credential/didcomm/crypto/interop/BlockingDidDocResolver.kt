package org.trustweave.credential.didcomm.crypto.interop

import kotlinx.coroutines.CoroutineDispatcher
import org.didcommx.didcomm.diddoc.DIDDoc
import org.didcommx.didcomm.diddoc.DIDDocResolver
import org.trustweave.credential.didcomm.crypto.BlockingLookupGuard
import org.trustweave.did.model.DidDocument
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * Adapts a suspend TrustWeave DID resolver to didcomm-java's synchronous [DIDDocResolver].
 *
 * **Preload is the supported path.** Call [preload] from suspend code with the DIDs a pack/unpack
 * will touch; [resolve] then answers from that short-lived cache without blocking.
 * [org.trustweave.credential.didcomm.crypto.DidCommCryptoDidcomm] does this for the DIDs it knows.
 *
 * **Fallback:** a DID that was not preloaded is resolved by blocking the calling thread. That
 * fallback is logged at WARN, runs on a small dedicated pool (not `Dispatchers.IO`), is bounded in
 * concurrency and times out after [resolveTimeoutMs] (default 5s; see
 * [BlockingLookupGuard] for the JVM-wide limits). The supplied suspend resolver must not call back
 * into didcomm pack/unpack (deadlock risk).
 *
 * @param dispatcher Dispatcher the fallback runs on; defaults to the dedicated bounded pool.
 * @param resolveTimeoutMs Maximum time for a single fallback DID resolution.
 * @param preloadTtlMs How long a preloaded document stays valid.
 */
class BlockingDidDocResolver(
    private val dispatcher: CoroutineDispatcher = BlockingLookupGuard.dispatcher,
    private val resolveTimeoutMs: Long = BlockingLookupGuard.DEFAULT_TIMEOUT_MS,
    private val preloadTtlMs: Long = DEFAULT_PRELOAD_TTL_MS,
    private val suspendResolve: suspend (String) -> DidDocument?,
) : DIDDocResolver {
    init {
        require(resolveTimeoutMs > 0) { "resolveTimeoutMs must be positive" }
        require(preloadTtlMs > 0) { "preloadTtlMs must be positive" }
    }

    private class Preloaded(
        val doc: Optional<DIDDoc>,
        val expiresAtNanos: Long,
    )

    private val preloaded = ConcurrentHashMap<String, Preloaded>()

    /** Resolves [dids] without blocking and keeps the documents for [preloadTtlMs]. */
    suspend fun preload(dids: Collection<String>) {
        for (did in dids.toSet()) {
            val doc = suspendResolve(did)?.let { TrustWeaveDidDocMapper.toDidComm(it) }
            preloaded[did] = Preloaded(Optional.ofNullable(doc), System.nanoTime() + preloadTtlMs * 1_000_000L)
        }
    }

    override fun resolve(did: String): Optional<DIDDoc> {
        preloaded[did]?.let { entry ->
            if (System.nanoTime() - entry.expiresAtNanos < 0) return entry.doc
            preloaded.remove(did, entry)
        }
        return BlockingLookupGuard.run("DID document $did", resolveTimeoutMs, dispatcher) {
            val doc = suspendResolve(did)
            Optional.ofNullable(doc?.let { TrustWeaveDidDocMapper.toDidComm(it) })
        }
    }

    companion object {
        const val DEFAULT_PRELOAD_TTL_MS: Long = 30_000L
    }
}
