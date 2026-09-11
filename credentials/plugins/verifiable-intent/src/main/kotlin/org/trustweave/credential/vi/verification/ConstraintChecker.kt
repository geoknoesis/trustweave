package org.trustweave.credential.vi.verification

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
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
 * Enforces non-negative integer amount bounds and currency, and resolves disclosed allowlists.
 * Budget and recurrence require external state: open mandates and STRICT checks fail closed.
 * Reference integrity is checked separately by ChainVerifier. Line-item matching enforces
 * per-requirement capacities and exact coverage, including overlapping alternatives.
 *
 * The standalone matcher assumes fulfillment data has been authenticated by the caller.
 * ChainVerifier requires CheckoutTrust to authenticate merchant JWT/cart data before checking checkout constraints.
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
                    checkAmount(c, fulfillment)?.let {
                        satisfied = false
                        violations += it
                    }
                }
                is Constraint.AllowedPayees -> {
                    checked += c.type
                    matchAllowlist(c.allowed, fulfillment["payee"] as? JsonObject, disclosuresByHash, isOpenMandate)
                        ?.let {
                            satisfied = false
                            violations += it
                        }
                }
                is Constraint.AllowedMerchants -> {
                    checked += c.type
                    matchAllowlist(c.allowed, fulfillment["merchant"] as? JsonObject, disclosuresByHash, isOpenMandate)
                        ?.let {
                            satisfied = false
                            violations += it
                        }
                }
                is Constraint.LineItems -> {
                    checked += c.type
                    LineItemMatcher.check(c, fulfillment, disclosuresByHash)?.let {
                        satisfied = false
                        violations += it
                    }
                }
                is Constraint.Reference -> skipped += c.type // ChainVerifier independently checks cross-mandate integrity.
                is Constraint.Budget,
                is Constraint.Recurrence,
                is Constraint.AgentRecurrence,
                -> {
                    if (isOpenMandate || mode == StrictnessMode.STRICT) {
                        satisfied = false
                        violations += "Constraint ${c.type} requires external enforcement that this verifier cannot establish"
                    } else {
                        skipped += c.type
                    }
                }
                is Constraint.Malformed -> {
                    // A recognized constraint the verifier cannot evaluate always fails closed,
                    // regardless of strictness: the issuer declared a bound that would
                    // otherwise stop binding.
                    satisfied = false
                    violations += "Malformed constraint ${c.type}: ${c.reason}"
                }
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

    private fun checkAmount(
        c: Constraint.AmountRange,
        fulfillment: JsonObject,
    ): String? {
        val pa = fulfillment["payment_amount"] as? JsonObject ?: return "Missing or invalid payment_amount in fulfillment"
        val amount =
            pa["amount"]?.let { runCatching { it.longValue() }.getOrNull() }
                ?: return "Missing/invalid amount in fulfillment payment_amount"
        if (amount < 0) return "Payment amount must be non-negative"
        c.min?.let { if (amount < it) return "Amount below minimum: $amount < $it ${c.currency}" }
        c.max?.let { if (amount > it) return "Amount exceeds maximum: $amount > $it ${c.currency}" }
        val currency =
            (pa["currency"] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
                ?: return "Missing or invalid payment currency"
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
                    val disclosed =
                        disclosuresByHash[ref]
                            ?.takeIf {
                                Disclosures.hash(
                                    it,
                                ) == ref
                            }?.let { Disclosures.parse(it)?.value as? JsonObject }
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
        if (candidates.isEmpty()) return "Allowlist contains no valid permitted targets"
        return "Target not in allowlist (id=${target["id"]?.contentOrNull()})"
    }

    private fun matches(
        candidate: JsonObject,
        target: JsonObject,
    ): Boolean {
        fun text(
            obj: JsonObject,
            field: String,
        ): String? = (obj[field] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString && it.content.isNotBlank() }?.content
        val cid = text(candidate, "id")
        if ("id" in candidate) return cid != null && cid == text(target, "id")
        val cName = text(candidate, "name")
        val cSite = text(candidate, "website")
        return !cName.isNullOrEmpty() &&
            cName == text(target, "name") &&
            !cSite.isNullOrEmpty() &&
            cSite == text(target, "website")
    }
}

private fun kotlinx.serialization.json.JsonElement.longValue(): Long =
    (this as kotlinx.serialization.json.JsonPrimitive).let {
        require(!it.isString) { "Amount must be a JSON number" }
        it.longOrNull ?: it.long
    }
