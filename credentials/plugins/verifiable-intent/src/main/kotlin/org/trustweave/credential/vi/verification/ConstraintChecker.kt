package org.trustweave.credential.vi.verification

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import org.trustweave.credential.vi.crypto.Disclosures
import org.trustweave.credential.vi.crypto.contentOrNull
import org.trustweave.credential.vi.model.Constraint

/** PERMISSIVE skips unknown constraint types; STRICT fails on them. */
public enum class StrictnessMode { PERMISSIVE, STRICT }

public data class ConstraintCheckResult(
    val satisfied: Boolean,
    val violations: List<String> = emptyList(),
    val checked: List<String> = emptyList(),
    val skipped: List<String> = emptyList(),
)

/**
 * Validates that an L3 fulfillment satisfies the L2 open-mandate constraints. Ports
 * `verification/constraint_checker.py`.
 *
 * Implemented in full: `amount_range` (integer minor-unit bounds + currency). `allowed_payees` /
 * `allowed_merchants` honor the reference behavior of skipping when the allowlist is entirely SD
 * references (resolved out-of-band). `budget` / `recurrence` / `agent_recurrence` / `reference` are
 * acknowledged as network/integrity-enforced. `line_items` deep-matching is a documented TODO.
 *
 * Unknown types: rejected when [isOpenMandate] (an unevaluable constraint leaves authority unbounded)
 * or under [StrictnessMode.STRICT]; otherwise skipped.
 */
public object ConstraintChecker {

    public fun check(
        constraints: List<Constraint>,
        fulfillment: JsonObject,
        mode: StrictnessMode = StrictnessMode.PERMISSIVE,
        isOpenMandate: Boolean = false,
        disclosuresByHash: Map<String, String> = emptyMap(),
    ): ConstraintCheckResult {
        var satisfied = true
        val violations = mutableListOf<String>()
        val checked = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        for (c in constraints) {
            when (c) {
                is Constraint.AmountRange -> {
                    checked += c.type
                    checkAmount(c, fulfillment)?.let { satisfied = false; violations += it }
                }
                is Constraint.AllowedPayees -> {
                    checked += c.type
                    matchAllowlist(c.allowed, fulfillment["payee"] as? JsonObject, disclosuresByHash, isOpenMandate)
                        ?.let { satisfied = false; violations += it }
                }
                is Constraint.AllowedMerchants -> {
                    checked += c.type
                    matchAllowlist(c.allowed, fulfillment["merchant"] as? JsonObject, disclosuresByHash, isOpenMandate)
                        ?.let { satisfied = false; violations += it }
                }
                is Constraint.LineItems -> checked += c.type // TODO: port acceptable-id + quantity-cap matching
                is Constraint.Reference,
                is Constraint.Budget,
                is Constraint.Recurrence,
                is Constraint.AgentRecurrence,
                -> checked += c.type // integrity-/network-enforced; acknowledged here
                is Constraint.Unknown -> {
                    if (isOpenMandate || mode == StrictnessMode.STRICT) {
                        satisfied = false
                        violations += "Unknown constraint type: ${c.type}"
                    } else {
                        skipped += c.type
                    }
                }
            }
        }
        return ConstraintCheckResult(satisfied, violations, checked, skipped)
    }

    private fun checkAmount(c: Constraint.AmountRange, fulfillment: JsonObject): String? {
        val pa = fulfillment["payment_amount"] as? JsonObject ?: return "Missing or invalid payment_amount in fulfillment"
        val amount = pa["amount"]?.let { runCatching { it.intValue() }.getOrNull() }
            ?: return "Missing/invalid amount in fulfillment payment_amount"
        c.min?.let { if (amount < it) return "Amount below minimum: $amount < $it ${c.currency}" }
        c.max?.let { if (amount > it) return "Amount exceeds maximum: $amount > $it ${c.currency}" }
        val currency = pa["currency"]?.contentOrNull() ?: c.currency
        if (currency != c.currency) return "Currency mismatch: expected ${c.currency}, got $currency"
        return null
    }

    /**
     * Returns a violation string, or null if the target is allowed.
     *
     * Allowlist entries are either inline objects or SD-references (`{"...": digest}`) whose payee/
     * merchant value is disclosed separately. References are resolved against [disclosuresByHash]
     * (the L2 disclosures the verifier holds). If the target matches a resolvable entry it is
     * allowed. If it matches none and some entries could not be resolved, the allowlist cannot be
     * fully evaluated: for an [isOpenMandate] this would leave payee/merchant authority unbounded —
     * and an agent could disable the constraint just by withholding the disclosures — so we fail
     * closed rather than pass vacuously.
     */
    private fun matchAllowlist(
        allowed: List<JsonObject>,
        target: JsonObject?,
        disclosuresByHash: Map<String, String>,
        isOpenMandate: Boolean,
    ): String? {
        if (target == null) return "Missing target object in fulfillment"

        val candidates = mutableListOf<JsonObject>()
        var unresolvedRefs = 0
        for (entry in allowed) {
            val ref = entry["..."]?.contentOrNull()
            when {
                ref != null -> {
                    val disclosed = disclosuresByHash[ref]?.let { Disclosures.parse(it)?.value as? JsonObject }
                    if (disclosed != null) candidates += disclosed else unresolvedRefs++
                }
                entry["id"] != null || entry["name"] != null -> candidates += entry
            }
        }

        if (candidates.any { matches(it, target) }) return null // explicitly allowed

        if (unresolvedRefs > 0) {
            // Non-empty allowlist that could not be fully evaluated (entries withheld).
            return if (isOpenMandate) {
                "Allowlist could not be evaluated against the presented disclosures " +
                    "($unresolvedRefs undisclosed entr${if (unresolvedRefs == 1) "y" else "ies"})"
            } else {
                null
            }
        }
        if (candidates.isEmpty()) return null // empty allowlist constrains nothing
        return "Target not in allowlist (id=${target["id"]?.contentOrNull()})"
    }

    private fun matches(candidate: JsonObject, target: JsonObject): Boolean {
        val cid = candidate["id"]?.contentOrNull()
        val tid = target["id"]?.contentOrNull()
        if (!cid.isNullOrEmpty() && !tid.isNullOrEmpty()) return cid == tid
        val cName = candidate["name"]?.contentOrNull()
        val cSite = candidate["website"]?.contentOrNull()
        return !cName.isNullOrEmpty() && cName == target["name"]?.contentOrNull() &&
            !cSite.isNullOrEmpty() && cSite == target["website"]?.contentOrNull()
    }
}

private fun kotlinx.serialization.json.JsonElement.intValue(): Int =
    (this as kotlinx.serialization.json.JsonPrimitive).let { it.intOrNull ?: it.int }
