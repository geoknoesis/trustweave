package org.trustweave.credential.proof

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.PriorityQueue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Single-use guard for the SD-JWT Key Binding JWT `nonce` (the verifier's challenge).
 *
 * By default a KB-JWT is only bound to a nonce the verifier *expects* (`expectedChallenge`) and to a
 * freshness window; the same signed presentation can still be shown twice inside that window. A
 * verifier that issues one nonce per transaction can close that gap by supplying a store:
 *
 * ```kotlin
 * val options = VerificationOptions(
 *     expectedChallenge = nonceIssuedForThisTransaction,
 *     additionalOptions = mapOf(PresentationNonceStore.OPTION_KEY to InMemoryPresentationNonceStore()),
 * )
 * ```
 *
 * The nonce is consumed only after the presentation has passed every other check (KB-JWT signature,
 * `sd_hash`, freshness, expected challenge and domain), so a request that was going to be rejected
 * anyway cannot burn a nonce. A second presentation carrying the same nonce is rejected as a replay.
 * When a store is configured the proof MUST carry a non-blank nonce (SD-JWT-VC KB-JWT `nonce`, or the
 * `challenge` of a Linked Data proof; other proof types are rejected as unable to honour the guard),
 * and presentation-proof verification must be enabled (the nonce is untrustworthy otherwise). The
 * string passed to [consume] is `"<scopeLength>:<scope>:<nonce>"`, scoping single-use per verifier/audience
 * (see [SCOPE_OPTION_KEY]). A full or failing store makes verification fail with an Invalid result.
 *
 * Opt-in and additive: with no store configured nothing changes.
 */
public fun interface PresentationNonceStore {
    /**
     * Atomically marks [nonce] as used.
     *
     * @return `true` the first time the nonce is seen; `false` if it was already consumed (a replay).
     */
    public suspend fun consume(nonce: String): Boolean

    public companion object {
        /** Key under `VerificationOptions.additionalOptions` that carries the [PresentationNonceStore]. */
        public const val OPTION_KEY: String = "kbJwtNonceStore"

        /**
         * Optional key under `VerificationOptions.additionalOptions` (a `String`) naming this verifier /
         * audience. The string handed to [consume] is scoped by it (falling back to `expectedDomain`,
         * then empty), so a nonce is single-use per (scope, nonce) rather than globally.
         */
        public const val SCOPE_OPTION_KEY: String = "kbJwtNonceScope"
    }
}

/**
 * Bounded in-memory [PresentationNonceStore]; per-process, so use a shared implementation when more
 * than one verifier instance can receive the same transaction.
 *
 * Nonces are remembered for [ttl], which must be at least the KB-JWT freshness window
 * (`kbJwtMaxAge` + clock skew, 10 minutes + 5 minutes by default) or an expired entry would allow a
 * still-fresh KB-JWT to be replayed. When [capacity] unexpired nonces are held, new nonces are
 * refused with [IllegalStateException] rather than evicting one that is still protecting a
 * transaction.
 */
public class InMemoryPresentationNonceStore
    @JvmOverloads
    constructor(
        private val ttl: Duration = 30.minutes,
        private val capacity: Int = 100_000,
        private val clock: Clock = Clock.System,
    ) : PresentationNonceStore {
        init {
            require(ttl.isPositive()) { "ttl must be positive" }
            require(capacity > 0) { "capacity must be positive" }
        }

        private data class Entry(
            val nonce: String,
            val expiresAt: Instant,
        )

        private val expiryById = HashMap<String, Instant>()
        private val byExpiry = PriorityQueue<Entry>(compareBy { it.expiresAt })

        override suspend fun consume(nonce: String): Boolean =
            synchronized(this) {
                val now = clock.now()
                while (true) {
                    val head = byExpiry.peek() ?: break
                    if (head.expiresAt > now) break
                    byExpiry.poll()
                    if (expiryById[head.nonce] == head.expiresAt) expiryById.remove(head.nonce)
                }
                if (expiryById.containsKey(nonce)) return false
                check(expiryById.size < capacity) {
                    "KB-JWT nonce store holds $capacity unexpired nonces; refusing new ones until some expire"
                }
                val expiresAt = now + ttl
                expiryById[nonce] = expiresAt
                byExpiry.add(Entry(nonce, expiresAt))
                true
            }
    }
