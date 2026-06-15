package org.trustweave.credential.avpauth.engine

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import org.trustweave.credential.avpauth.state.ConsumptionLedger
import org.trustweave.credential.avpauth.state.DailyBudgetLedger
import org.trustweave.credential.avpauth.state.NonceStore
import org.trustweave.credential.avpmicro.AvpMicro
import org.trustweave.credential.avpmicro.verification.PaymentVerificationResult
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

sealed class AuthorizationVerdict {
    data class Allow(val payer: String, val payee: String, val amount: String) : AuthorizationVerdict()
    data class Reject(val reason: String, val detail: String) : AuthorizationVerdict()
}

/** Stateful rejection reasons; the stateless ones come from VerificationFailure. */
enum class StatefulRejection { NONCE_REUSE, DOUBLE_SPEND, DAILY_LIMIT_EXCEEDED }

class AuthorizationEngine(
    private val clock: () -> Instant = Instant::now,
    private val nonces: NonceStore = NonceStore(),
    private val consumption: ConsumptionLedger = ConsumptionLedger(),
    private val daily: DailyBudgetLedger = DailyBudgetLedger(),
) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private fun lockFor(credentialId: String) = locks.computeIfAbsent(credentialId) { Mutex() }

    suspend fun decide(authorization: JsonObject, quote: JsonObject? = null): AuthorizationVerdict {
        val now = clock()

        // 1. stateless gate (proofs + spending constraints + quote binding); reuse the parsed view
        val result = AvpMicro.verifyPayment(authorization, now, quote = quote)
        if (result is PaymentVerificationResult.Invalid)
            return AuthorizationVerdict.Reject(result.reason.name, result.detail)
        val view = (result as PaymentVerificationResult.Valid).view
        val sa = view.spendingAuthority

        // 2-5. stateful checks + atomic commit, serialized per credential
        return lockFor(sa.credentialId).withLock {
            if (nonces.seen(sa.credentialId, view.nonce))
                return@withLock AuthorizationVerdict.Reject(StatefulRejection.NONCE_REUSE.name, "nonce already presented")
            if (consumption.consumed(view.id))
                return@withLock AuthorizationVerdict.Reject(StatefulRejection.DOUBLE_SPEND.name, "authorization already consumed")
            sa.dailyLimit?.let { limit ->
                val prior = daily.spentToday(view.payer, sa.credentialId, now)
                if (prior.add(view.amount) > limit)
                    return@withLock AuthorizationVerdict.Reject(StatefulRejection.DAILY_LIMIT_EXCEEDED.name, "daily limit $limit exceeded")
            }
            // commit
            nonces.record(sa.credentialId, view.nonce)
            consumption.consume(view.id)
            daily.add(view.payer, sa.credentialId, view.amount, now)
            AuthorizationVerdict.Allow(view.payer, view.payee, view.amount.toPlainString())
        }
    }
}
