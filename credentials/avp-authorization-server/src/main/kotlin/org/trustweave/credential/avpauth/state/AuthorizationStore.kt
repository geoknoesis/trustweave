package org.trustweave.credential.avpauth.state

import java.math.BigDecimal
import java.time.Instant

/**
 * Why an authorization was refused by the stateful gate.
 *
 * The stateless reasons come from `VerificationFailure`; these are the ones that depend on what
 * this deployment has already seen.
 */
enum class StatefulRejection {
    /** This (credential, nonce) pair has been presented before. */
    NONCE_REUSE,

    /** This authorization id has already been consumed. */
    DOUBLE_SPEND,

    /** Admitting this amount would carry the payer past the credential's daily limit. */
    DAILY_LIMIT_EXCEEDED,

    /**
     * The authorization can never stop being replayable, so no store can promise to remember it.
     *
     * Reached when neither the authorization's `expires` nor the credential's `validUntil` bounds
     * how long the authorization stays presentable, or when the bound is further out than the
     * store's retention ceiling. Refusing is the only honest answer: admitting it would mean
     * promising replay protection the store would later drop.
     */
    UNBOUNDED_LIFETIME,

    /**
     * The store could not decide. Never a retry — an uncertain commit is a denial.
     *
     * Raised when the durable store is unreachable or its transaction failed, and when an
     * in-memory store is at capacity and cannot record another authorization. Forgetting a nonce
     * to make room would silently re-open replay, so the store refuses instead.
     */
    STORE_UNAVAILABLE,
}

/** The outcome of a single atomic check-and-record. */
sealed interface Admission {
    /** The authorization was admitted and every guarantee it consumed has been recorded. */
    data object Admitted : Admission

    /** The authorization was refused and nothing was recorded. */
    data class Refused(
        val rejection: StatefulRejection,
        val detail: String,
    ) : Admission
}

/**
 * One authorization, reduced to what the stateful gate needs.
 *
 * @param retainUntil the instant after which this authorization can no longer be validly
 *   presented, so the store may forget it. Derived from the authorization's own expiry, never
 *   from a store-side default — a store that forgets a nonce while the authorization is still
 *   presentable has silently stopped preventing replay.
 */
data class AdmissionRequest(
    val credentialId: String,
    val nonce: String,
    val authorizationId: String,
    val payer: String,
    val amount: BigDecimal,
    val dailyLimit: BigDecimal?,
    val at: Instant,
    val retainUntil: Instant,
)

/**
 * Replay prevention, single-use enforcement and daily spend caps, as one atomic decision.
 *
 * The three guarantees are inseparable: checking them apart from recording them lets two
 * concurrent presentations of the same authorization both pass. So there is one method, it does
 * the whole check-and-record, and an implementation that cannot make it atomic is not a valid
 * implementation of this interface.
 *
 * ## What a deployment has to decide
 *
 * These guarantees are only as wide as the store behind them. [InMemoryAuthorizationStore] holds
 * them inside one process: correct for a single instance, and enforcing nothing at all across two
 * replicas or across a restart. [PostgresAuthorizationStore] holds them in a shared database, so
 * every instance authorizing the same credentials enforces one set of limits.
 *
 * Anything more than one process needs the durable store. This is not a performance choice.
 *
 * ## Failure
 *
 * Implementations fail closed. An admission that cannot be recorded is refused, never admitted
 * and never retried as a fresh authorization — a retried uncertain commit is exactly how a
 * single-use authorization gets used twice.
 */
interface AuthorizationStore {
    /**
     * Atomically refuses, or admits and records.
     *
     * A refusal records nothing: a refused authorization has not been presented successfully, so
     * its nonce stays unconsumed and its budget untouched.
     */
    suspend fun admit(request: AdmissionRequest): Admission

    /**
     * Drops records that can no longer affect a decision, and reports how many went.
     *
     * Implementations sweep on their own as well; this exists so a host can run the sweep on its
     * own schedule, and so the sweep is observable.
     */
    suspend fun purgeExpired(now: Instant): Int = 0
}
