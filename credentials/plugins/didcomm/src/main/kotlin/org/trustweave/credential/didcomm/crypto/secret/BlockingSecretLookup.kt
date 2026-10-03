package org.trustweave.credential.didcomm.crypto.secret

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Bridge used by the DIDComm [org.didcommx.didcomm.secret.SecretResolver]s on a cache miss.
 *
 * didcomm-java calls `findKey` synchronously from inside pack/unpack, so a miss cannot suspend.
 * The preferred path is to call `preload(...)` from suspend code before packing, so `findKey` is a
 * pure cache read. When that was not done, the lookup runs as `runBlocking(Dispatchers.IO)` with
 * a timeout: the suspend work never runs on (and so can never deadlock) the caller's dispatcher,
 * and a hung key store fails loudly instead of pinning the thread forever.
 */
internal object BlockingSecretLookup {
    const val TIMEOUT_MS = 30_000L

    fun <T> lookup(
        what: String,
        block: suspend () -> T,
    ): T =
        runBlocking(Dispatchers.IO) {
            try {
                withTimeout(TIMEOUT_MS) { block() }
            } catch (e: TimeoutCancellationException) {
                throw IllegalStateException("DIDComm secret lookup for '$what' timed out after ${TIMEOUT_MS}ms", e)
            }
        }
}
