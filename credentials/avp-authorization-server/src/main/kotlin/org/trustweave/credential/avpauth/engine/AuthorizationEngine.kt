package org.trustweave.credential.avpauth.engine

import kotlinx.serialization.json.JsonObject
import org.trustweave.credential.avpauth.state.Admission
import org.trustweave.credential.avpauth.state.AdmissionRequest
import org.trustweave.credential.avpauth.state.AuthorizationStore
import org.trustweave.credential.avpauth.state.InMemoryAuthorizationStore
import org.trustweave.credential.avpmicro.AvpMicro
import org.trustweave.credential.avpmicro.verification.PaymentVerificationResult
import java.time.Instant

sealed class AuthorizationVerdict {
    data class Allow(
        val payer: String,
        val payee: String,
        val amount: String,
    ) : AuthorizationVerdict()

    data class Reject(
        val reason: String,
        val detail: String,
    ) : AuthorizationVerdict()
}

/**
 * Stateless verification followed by one atomic stateful admission.
 *
 * The stateless gate — proofs, spending constraints, quote binding — is pure, so it runs first and
 * cheaply. Everything that depends on what this deployment has already seen is then decided by
 * [store] in a single check-and-record, because checking replay, single-use and daily spend apart
 * from recording them lets two concurrent presentations of one authorization both pass.
 *
 * **The default [store] is in-process.** [InMemoryAuthorizationStore] enforces its guarantees
 * inside this JVM only: a restart forgets every nonce, and two replicas enforce two separate sets
 * of limits. Pass a `PostgresAuthorizationStore` for any deployment that is not exactly one
 * process. That is a correctness decision, not a scaling one.
 *
 * @param clockSkewSeconds tolerance the stateless gate allows on expiry, mirrored here so a record
 *   is retained for as long as the authorization it describes is still presentable.
 */
class AuthorizationEngine
    @JvmOverloads
    constructor(
        private val clock: () -> Instant = Instant::now,
        private val store: AuthorizationStore = InMemoryAuthorizationStore(),
        private val clockSkewSeconds: Long = DEFAULT_CLOCK_SKEW_SECONDS,
    ) {
        suspend fun decide(
            authorization: JsonObject,
            quote: JsonObject? = null,
        ): AuthorizationVerdict {
            val now = clock()

            // 1. stateless gate (proofs + spending constraints + quote binding); reuse the parsed view
            val result = AvpMicro.verifyPayment(authorization, now, quote = quote)
            if (result is PaymentVerificationResult.Invalid) {
                return AuthorizationVerdict.Reject(result.reason.name, result.detail)
            }
            val view = (result as PaymentVerificationResult.Valid).view
            val sa = view.spendingAuthority

            // 2. stateful gate: refuse, or admit and record, as one atomic step
            val admission =
                store.admit(
                    AdmissionRequest(
                        credentialId = sa.credentialId,
                        nonce = view.nonce,
                        authorizationId = view.id,
                        payer = view.payer,
                        amount = view.amount,
                        dailyLimit = sa.dailyLimit,
                        at = now,
                        retainUntil = retainUntil(view.expires, sa.validUntil, now),
                    ),
                )
            return when (admission) {
                is Admission.Admitted ->
                    AuthorizationVerdict.Allow(view.payer, view.payee, view.amount.toPlainString())
                is Admission.Refused ->
                    AuthorizationVerdict.Reject(admission.rejection.name, admission.detail)
            }
        }

        /**
         * The instant past which this authorization can no longer be validly presented.
         *
         * The stateless gate admits an authorization until its own `expires`, or failing that its
         * credential's `validUntil`, plus the clock skew it tolerates. Retaining the record to
         * exactly that point is what makes the replay guarantee complete rather than approximate:
         * drop it sooner and the same authorization becomes presentable twice.
         *
         * When neither bound exists, the authorization never stops being presentable. That is
         * returned as [Instant.MAX] and the store refuses it, rather than either store pretending
         * to remember it forever.
         */
        private fun retainUntil(
            expires: Instant?,
            validUntil: Instant?,
            now: Instant,
        ): Instant {
            val bound = expires ?: validUntil ?: return Instant.MAX
            val skewed = bound.plusSeconds(clockSkewSeconds)
            // A record is never retained for less than the moment it was written: an already
            // expired authorization is refused by the stateless gate before reaching the store.
            return if (skewed.isBefore(now)) now else skewed
        }

        companion object {
            /** The conventional JWT clock-skew tolerance, matching the stateless verifier's default. */
            const val DEFAULT_CLOCK_SKEW_SECONDS: Long = 300
        }
    }
