package org.trustweave.credential.didcomm.crypto.secret

import org.trustweave.credential.didcomm.crypto.BlockingLookupGuard

/**
 * Bridge used by the DIDComm [org.didcommx.didcomm.secret.SecretResolver]s on a cache miss.
 *
 * didcomm-java calls `findKey` synchronously from inside pack/unpack, so a miss cannot suspend.
 * The supported path is to call `preload(...)` from suspend code before packing, so `findKey` is a
 * pure cache read. A miss that reaches this bridge is a fallback: it is logged at WARN, runs on a
 * small dedicated pool (never the caller's dispatcher, never the shared IO pool), is bounded in
 * concurrency, and times out after [BlockingLookupGuard.DEFAULT_TIMEOUT_MS] by default
 * (`-Dtrustweave.didcomm.fallbackLookup.timeoutMs` / `.maxConcurrent` to tune). See
 * [BlockingLookupGuard].
 */
internal object BlockingSecretLookup {
    fun <T> lookup(
        what: String,
        block: suspend () -> T,
    ): T = BlockingLookupGuard.run("secret $what", block = block)
}
