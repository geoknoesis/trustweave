package org.trustweave.credential.avpauth.engine

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import org.trustweave.credential.avpauth.state.ConsumptionLedger
import org.trustweave.credential.avpauth.state.DailyBudgetLedger
import org.trustweave.credential.avpauth.state.NonceStore
import org.trustweave.credential.avpmicro.AvpMicro
import org.trustweave.credential.avpmicro.model.AuthorizationView
import org.trustweave.credential.avpmicro.verification.PaymentVerificationResult
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

sealed class AuthorizationVerdict {
    data class Allow(val payer: String, val payee: String, val amount: String) : AuthorizationVerdict()
    data class Reject(val reason: String, val detail: String) : AuthorizationVerdict()
}

class AuthorizationEngine(
    private val clock: () -> Instant = Instant::now,
    private val nonces: NonceStore = NonceStore(),
    private val consumption: ConsumptionLedger = ConsumptionLedger(),
    private val daily: DailyBudgetLedger = DailyBudgetLedger(),
) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private fun lockFor(credentialId: String) = locks.computeIfAbsent(credentialId) { Mutex() }

    suspend fun decide(authorization: JsonObject): AuthorizationVerdict {
        val now = clock()

        // 1. stateless gate (proofs + spending constraints)
        when (val r = AvpMicro.verifyPayment(authorization, now)) {
            is PaymentVerificationResult.Invalid ->
                return AuthorizationVerdict.Reject(r.reason.name, r.detail)
            is PaymentVerificationResult.Valid -> Unit
        }

        val view = AuthorizationView.from(authorization)
        val sa = view.spendingAuthority

        // 2-5. stateful checks + atomic commit, serialized per credential
        return lockFor(sa.credentialId).withLock {
            if (nonces.seen(sa.credentialId, view.nonce))
                return@withLock AuthorizationVerdict.Reject("NONCE_REUSE", "nonce already presented")
            if (consumption.consumed(view.id))
                return@withLock AuthorizationVerdict.Reject("DOUBLE_SPEND", "authorization already consumed")
            sa.dailyLimit?.let { limit ->
                val prior = daily.spentToday(view.payer, sa.credentialId, now)
                if (prior.add(view.amount) > limit)
                    return@withLock AuthorizationVerdict.Reject("DAILY_LIMIT_EXCEEDED", "daily limit $limit exceeded")
            }
            // commit
            nonces.record(sa.credentialId, view.nonce)
            consumption.consume(view.id)
            daily.add(view.payer, sa.credentialId, view.amount, now)
            AuthorizationVerdict.Allow(view.payer, view.payee, view.amount.toPlainString())
        }
    }
}
