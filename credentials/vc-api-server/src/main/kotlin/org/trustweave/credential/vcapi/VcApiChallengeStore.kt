package org.trustweave.credential.vcapi

import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Server-side, single-use presentation challenges for `POST /presentations/verify`.
 *
 * A verifier [issue]s a challenge, hands it to the holder, and the holder signs it into the
 * presentation. When the presentation comes back, the challenge is [consume]d exactly once, so the
 * same signed presentation cannot be shown a second time.
 */
interface VcApiChallengeStore {
    /** Mints a new unguessable challenge and remembers it until it is consumed or expires. */
    fun issue(): IssuedChallenge

    /**
     * Atomically removes [challenge].
     *
     * @return `true` only the first time, and only for a challenge that [issue] produced and that has
     *   not expired; `false` for an unknown, expired or already consumed challenge.
     */
    fun consume(challenge: String): Boolean
}

/** A challenge minted by a [VcApiChallengeStore] and the instant it stops being accepted. */
data class IssuedChallenge(
    val challenge: String,
    val expiresAt: Instant,
)

/**
 * Bounded in-memory [VcApiChallengeStore]; per-process, so use a shared implementation when more
 * than one instance can receive the same transaction.
 *
 * Challenges live for [ttl]. When [capacity] unexpired challenges are held, expired ones are purged
 * first, and if the store is still full [issue] throws [IllegalStateException] rather than evicting a
 * challenge that is still protecting a transaction.
 */
class InMemoryVcApiChallengeStore
    @JvmOverloads
    constructor(
        private val ttl: Duration = 5.minutes,
        private val capacity: Int = 10_000,
        private val clock: Clock = Clock.System,
    ) : VcApiChallengeStore {
        init {
            require(ttl.isPositive()) { "ttl must be positive" }
            require(capacity > 0) { "capacity must be positive" }
        }

        private val random = SecureRandom()
        private val entries = ConcurrentHashMap<String, Instant>()

        override fun issue(): IssuedChallenge {
            val now = clock.now()
            synchronized(entries) {
                if (entries.size >= capacity) purgeExpired(now)
                check(entries.size < capacity) { "Challenge store is full" }
                val bytes = ByteArray(32).also { random.nextBytes(it) }
                val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
                val expiresAt = now + ttl
                entries[challenge] = expiresAt
                return IssuedChallenge(challenge, expiresAt)
            }
        }

        override fun consume(challenge: String): Boolean {
            val expiresAt = entries.remove(challenge) ?: return false
            return clock.now() < expiresAt
        }

        private fun purgeExpired(now: Instant) {
            entries.entries.removeIf { it.value <= now }
        }
    }

/**
 * How `POST /presentations/verify` treats holder binding and challenges.
 *
 * The defaults are the safe ones: the presentation proof must be made by the holder, every presented
 * credential must be about the holder, and the presentation must carry a challenge this server issued
 * (`POST /presentations/challenge`), which is spent by the verification.
 *
 * @param enforceHolderBinding Require credentialSubject.id to equal the presentation holder and the
 *   proof key to belong to the holder. Pass `false` for bearer credentials or third-party subjects.
 * @param challengeStore Where challenges are issued and consumed. `null` turns the one-time challenge
 *   off, and a caller-chosen challenge is then only compared with the presentation, as before.
 * @param requireChallenge With a [challengeStore], reject a verify request that carries no challenge.
 */
class VcApiVerificationPolicy
    @JvmOverloads
    constructor(
        val enforceHolderBinding: Boolean = true,
        val challengeStore: VcApiChallengeStore? = InMemoryVcApiChallengeStore(),
        val requireChallenge: Boolean = true,
    )
