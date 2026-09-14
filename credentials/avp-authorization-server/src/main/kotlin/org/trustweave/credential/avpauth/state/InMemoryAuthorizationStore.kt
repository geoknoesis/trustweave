package org.trustweave.credential.avpauth.state

import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

/** Separator that cannot appear in a DID, a URN or a nonce, so composed keys cannot collide. */
private const val SEPARATOR = "\u001F"

/**
 * Single-process replay, single-use and daily-spend enforcement, bounded and self-evicting.
 *
 * **This enforces nothing across processes.** Its state lives in this JVM's heap, so a restart
 * forgets every nonce it has seen, and two replicas each enforce their own separate limits —
 * which, for a daily cap, means a payer can spend the cap once per replica. Use
 * [PostgresAuthorizationStore] for anything that is not exactly one process, and use this one for
 * tests, development and single-instance deployments that have accepted both properties.
 *
 * ## Why it is bounded
 *
 * The keys are caller-supplied — a nonce, an authorization id, a payer — so a caller decides how
 * many distinct entries this store accumulates. Two things keep that finite:
 *
 * - Every record carries the instant after which the authorization it describes can no longer be
 *   validly presented, taken from the authorization itself. Past that, the record cannot change a
 *   decision and is swept.
 * - [maxEntries] caps each of the three record sets. At the cap, after a sweep, an admission is
 *   **refused** rather than admitted with an evicted record. Evicting a live nonce to make room
 *   would turn memory pressure into a replay window, so capacity exhaustion is a denial.
 *
 * @param maxEntries per-set ceiling on retained records. The default holds roughly a day of
 *   sustained traffic at ten authorizations a second.
 * @param retentionCeiling the furthest out a record will be retained. An authorization that would
 *   need to be remembered longer is refused rather than half-remembered.
 */
class InMemoryAuthorizationStore
    @JvmOverloads
    constructor(
        private val maxEntries: Int = 1_000_000,
        private val retentionCeiling: Duration = Duration.ofDays(90),
    ) : AuthorizationStore {
        init {
            require(maxEntries > 0) { "maxEntries must be positive" }
            require(!retentionCeiling.isNegative && !retentionCeiling.isZero) { "retentionCeiling must be positive" }
        }

        /** Key to the epoch-milli instant after which the record may be dropped. */
        private val nonces = ConcurrentHashMap<String, Long>()
        private val consumed = ConcurrentHashMap<String, Long>()
        private val spend = ConcurrentHashMap<String, DailyEntry>()

        private class DailyEntry(
            val amount: BigDecimal,
            val expiresAtMillis: Long,
        )

        /**
         * Serializes admission per credential without growing a lock per credential.
         *
         * A map of mutexes keyed by credential id is itself an unbounded, caller-keyed map; a
         * fixed stripe array gives the same per-credential serialization and never grows. Every
         * key one admission touches derives from the same credential, so one stripe covers the
         * whole check-and-record.
         */
        private val stripes = Array(64) { Any() }

        private fun stripeFor(credentialId: String) = stripes[(credentialId.hashCode() and 0x7fffffff) % stripes.size]

        private fun nonceKey(
            credentialId: String,
            nonce: String,
        ) = credentialId + SEPARATOR + nonce

        private fun spendKey(
            payer: String,
            credentialId: String,
            at: Instant,
        ) = payer + SEPARATOR + credentialId + SEPARATOR + at.atZone(ZoneOffset.UTC).toLocalDate()

        /** A day's spend stops mattering once that UTC day is over. */
        private fun endOfUtcDay(at: Instant): Long =
            at
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()

        override suspend fun admit(request: AdmissionRequest): Admission {
            if (request.retainUntil.isAfter(request.at.plus(retentionCeiling))) {
                return Admission.Refused(
                    StatefulRejection.UNBOUNDED_LIFETIME,
                    "authorization stays presentable past this store's ${retentionCeiling.toDays()}-day retention ceiling",
                )
            }
            val now = request.at.toEpochMilli()
            val retainUntil = request.retainUntil.toEpochMilli()
            val nonce = nonceKey(request.credentialId, request.nonce)
            val daily = spendKey(request.payer, request.credentialId, request.at)
            val dailyExpiry = endOfUtcDay(request.at)

            synchronized(stripeFor(request.credentialId)) {
                if (live(nonces[nonce], now)) {
                    return Admission.Refused(StatefulRejection.NONCE_REUSE, "nonce already presented")
                }
                if (live(consumed[request.authorizationId], now)) {
                    return Admission.Refused(StatefulRejection.DOUBLE_SPEND, "authorization already consumed")
                }
                val prior = spend[daily]?.takeIf { it.expiresAtMillis > now }?.amount ?: BigDecimal.ZERO
                request.dailyLimit?.let { limit ->
                    if (prior.add(request.amount) > limit) {
                        return Admission.Refused(StatefulRejection.DAILY_LIMIT_EXCEEDED, "daily limit $limit exceeded")
                    }
                }
                // Room is checked for all three before any is written, so a partial commit at the
                // capacity edge cannot leave a recorded nonce beside an unrecorded spend.
                if (!hasRoom(nonces, nonce, now) ||
                    !hasRoom(consumed, request.authorizationId, now) ||
                    !hasDailyRoom(daily, now)
                ) {
                    return Admission.Refused(
                        StatefulRejection.STORE_UNAVAILABLE,
                        "authorization state is at capacity; refusing rather than forgetting a live record",
                    )
                }
                nonces[nonce] = retainUntil
                consumed[request.authorizationId] = retainUntil
                spend[daily] = DailyEntry(prior.add(request.amount), dailyExpiry)
                return Admission.Admitted
            }
        }

        private fun live(
            expiresAtMillis: Long?,
            now: Long,
        ) = expiresAtMillis != null && expiresAtMillis > now

        private fun hasRoom(
            target: ConcurrentHashMap<String, Long>,
            key: String,
            now: Long,
        ): Boolean {
            if (target.containsKey(key) || target.size < maxEntries) return true
            target.values.removeIf { it <= now }
            return target.size < maxEntries
        }

        private fun hasDailyRoom(
            key: String,
            now: Long,
        ): Boolean {
            if (spend.containsKey(key) || spend.size < maxEntries) return true
            spend.values.removeIf { it.expiresAtMillis <= now }
            return spend.size < maxEntries
        }

        override suspend fun purgeExpired(now: Instant): Int {
            val cutoff = now.toEpochMilli()
            val before = nonces.size + consumed.size + spend.size
            nonces.values.removeIf { it <= cutoff }
            consumed.values.removeIf { it <= cutoff }
            spend.values.removeIf { it.expiresAtMillis <= cutoff }
            return before - (nonces.size + consumed.size + spend.size)
        }

        /** Retained record counts, for a host metric or a test. Never the records themselves. */
        fun retained(): RetainedRecords = RetainedRecords(nonces.size, consumed.size, spend.size)

        /** How many records each set currently holds. */
        data class RetainedRecords(
            val nonces: Int,
            val consumptions: Int,
            val dailySpend: Int,
        )
    }
