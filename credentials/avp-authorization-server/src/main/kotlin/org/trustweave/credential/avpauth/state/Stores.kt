package org.trustweave.credential.avpauth.state

import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

/** Replay guard: a (credentialId, nonce) pair may be presented at most once. */
class NonceStore {
    private val recorded = ConcurrentHashMap.newKeySet<String>()
    private fun k(credentialId: String, nonce: String) = "$credentialId$nonce"
    fun seen(credentialId: String, nonce: String) = recorded.contains(k(credentialId, nonce))
    fun record(credentialId: String, nonce: String) { recorded.add(k(credentialId, nonce)) }
}

/** Single-use: an authorization id may be consumed at most once. */
class ConsumptionLedger {
    private val used = ConcurrentHashMap.newKeySet<String>()
    fun consumed(authorizationId: String) = used.contains(authorizationId)
    fun consume(authorizationId: String) { used.add(authorizationId) }
}

/** Rolling daily spend per (agent, credential), bucketed by UTC date. */
class DailyBudgetLedger {
    private val spend = ConcurrentHashMap<String, BigDecimal>()
    private fun utcDate(at: Instant) = at.atZone(ZoneOffset.UTC).toLocalDate().toString()
    private fun k(agent: String, cred: String, at: Instant) = "$agent$cred${utcDate(at)}"
    fun spentToday(agent: String, cred: String, at: Instant): BigDecimal =
        spend[k(agent, cred, at)] ?: BigDecimal.ZERO
    fun add(agent: String, cred: String, amount: BigDecimal, at: Instant) {
        spend.merge(k(agent, cred, at), amount, BigDecimal::add)
    }
}
