package org.trustweave.credential.vi.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * A Verifiable Intent machine-enforceable constraint carried in an open (autonomous) L2 mandate.
 *
 * Eight registered types plus [Unknown]. Each constraint preserves its full source [raw] object so
 * that unrecognized fields survive a parse→serialize round-trip (the spec requires
 * forward-compatibility: "Parsers MUST preserve any fields in a constraint object that they do not
 * recognize").
 */
public sealed class Constraint {
    public abstract val type: String

    /** The unparsed source object — preserves unknown fields for forward compatibility. */
    public abstract val raw: JsonObject

    /** `mandate.checkout.allowed_merchants` — `allowed` holds merchant objects (often SD refs). */
    public data class AllowedMerchants(
        override val raw: JsonObject,
        val allowed: List<JsonObject>,
    ) : Constraint() {
        override val type: String get() = TYPE

        public companion object {
            public const val TYPE: String = "mandate.checkout.allowed_merchants"
        }
    }

    /** `mandate.checkout.line_items` — `items` of `{id, acceptable_items, quantity}`; `match_mode`. */
    public data class LineItems(
        override val raw: JsonObject,
        val items: List<JsonObject>,
        val matchMode: String,
    ) : Constraint() {
        override val type: String get() = TYPE

        public companion object {
            public const val TYPE: String = "mandate.checkout.line_items"
        }
    }

    /** `mandate.payment.allowed_payees` — `allowed` holds payee objects (often SD refs). */
    public data class AllowedPayees(
        override val raw: JsonObject,
        val allowed: List<JsonObject>,
    ) : Constraint() {
        override val type: String get() = TYPE

        public companion object {
            public const val TYPE: String = "mandate.payment.allowed_payees"
        }
    }

    /** `mandate.payment.amount_range` — per-transaction bounds in integer minor units. */
    public data class AmountRange(
        override val raw: JsonObject,
        val currency: String,
        val min: Long?,
        val max: Long?,
    ) : Constraint() {
        override val type: String get() = TYPE

        public companion object {
            public const val TYPE: String = "mandate.payment.amount_range"
        }
    }

    /** `mandate.payment.budget` — cumulative spend cap (network-enforced, stateful). */
    public data class Budget(
        override val raw: JsonObject,
        val currency: String,
        val max: Long,
        val min: Long?,
    ) : Constraint() {
        override val type: String get() = TYPE

        public companion object {
            public const val TYPE: String = "mandate.payment.budget"
        }
    }

    /** `mandate.payment.reference` — links payment mandate to the checkout disclosure. */
    public data class Reference(
        override val raw: JsonObject,
        val conditionalTransactionId: String,
    ) : Constraint() {
        override val type: String get() = TYPE

        public companion object {
            public const val TYPE: String = "mandate.payment.reference"
        }
    }

    /** `mandate.payment.recurrence` — merchant-initiated subscription terms (network-enforced). */
    public data class Recurrence(
        override val raw: JsonObject,
    ) : Constraint() {
        override val type: String get() = TYPE

        public companion object {
            public const val TYPE: String = "mandate.payment.recurrence"
        }
    }

    /** `mandate.payment.agent_recurrence` — agent-managed recurring terms (network-enforced). */
    public data class AgentRecurrence(
        override val raw: JsonObject,
    ) : Constraint() {
        override val type: String get() = TYPE

        public companion object {
            public const val TYPE: String = "mandate.payment.agent_recurrence"
        }
    }

    /** Any constraint type not in the registry; fields preserved verbatim in [raw]. */
    public data class Unknown(
        override val raw: JsonObject,
        override val type: String,
    ) : Constraint()

    /**
     * A registered constraint type whose own fields cannot be read — e.g. an `amount_range`
     * whose `max` is not a whole number of minor units.
     *
     * Kept distinct from [Unknown]: an unrecognized *type* may be safely ignored by a lenient
     * verifier, but a recognized constraint the verifier cannot evaluate must always fail
     * closed — otherwise a bound the issuer declared silently stops binding.
     */
    public data class Malformed(
        override val raw: JsonObject,
        override val type: String,
        val reason: String,
    ) : Constraint()

    public companion object {
        /**
         * Parses a constraint object into its typed form, falling back to [Unknown] for
         * unrecognized `type` values. Mirrors `models/constraints.py::parse_constraint`.
         */
        public fun parse(obj: JsonObject): Constraint {
            val type =
                obj.string("type")
                    ?: return Malformed(obj, "<invalid>", "type must be a string")
            val listField =
                when (type) {
                    AllowedMerchants.TYPE, AllowedPayees.TYPE -> "allowed"
                    LineItems.TYPE -> "items"
                    else -> null
                }
            if (listField != null) {
                val values = obj[listField] as? JsonArray
                if (values == null || values.any { it !is JsonObject }) {
                    return Malformed(obj, type, "$listField must be an array of objects")
                }
            }
            return when (type) {
                AllowedMerchants.TYPE -> AllowedMerchants(obj, obj.objectList("allowed"))
                LineItems.TYPE ->
                    if ("match_mode" in obj && obj.string("match_mode") == null) {
                        Malformed(obj, type, "match_mode must be a string")
                    } else {
                        LineItems(obj, obj.objectList("items"), obj.string("match_mode") ?: "minimum")
                    }
                AllowedPayees.TYPE -> AllowedPayees(obj, obj.objectList("allowed"))
                AmountRange.TYPE -> parseAmountRange(obj, type)
                Budget.TYPE -> parseBudget(obj, type)
                Reference.TYPE -> Reference(obj, obj.string("conditional_transaction_id").orEmpty())
                Recurrence.TYPE -> Recurrence(obj)
                AgentRecurrence.TYPE -> AgentRecurrence(obj)
                else -> Unknown(obj, type)
            }
        }
    }
}

/**
 * An `amount_range` whose declared bounds cannot be read is not an amount range - the ceiling the
 * issuer signed would silently stop binding.
 */
private fun parseAmountRange(
    obj: JsonObject,
    type: String,
): Constraint {
    val currency = obj.string("currency")
    if (currency == null || !Regex("[A-Z]{3}").matches(currency)) {
        return Constraint.Malformed(obj, type, "currency must be an uppercase three-letter string")
    }
    val unreadable = obj.unreadableBound("min") ?: obj.unreadableBound("max")
    if (unreadable != null) return Constraint.Malformed(obj, type, unreadable)
    return Constraint.AmountRange(
        obj,
        currency,
        obj.longOrNullSafe("min"),
        obj.longOrNullSafe("max"),
    )
}

/**
 * A budget without a readable cap is not a budget. Rebuilding a missing or unreadable `max`
 * as 0 (or dropping it) would silently replace the issuer's cap with something else.
 */
private fun parseBudget(
    obj: JsonObject,
    type: String,
): Constraint {
    val currency = obj.string("currency")
    if (currency == null || !Regex("[A-Z]{3}").matches(currency)) {
        return Constraint.Malformed(obj, type, "currency must be an uppercase three-letter string")
    }
    val unreadable = obj.unreadableBound("min") ?: obj.unreadableBound("max")
    if (unreadable != null) return Constraint.Malformed(obj, type, unreadable)
    val max =
        obj.longOrNullSafe("max")
            ?: return Constraint.Malformed(obj, type, "budget declares no 'max'")
    return Constraint.Budget(obj, currency, max, obj.longOrNullSafe("min"))
}

private fun JsonPrimitive.contentOrNullSafe(): String? = runCatching { content }.getOrNull()

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.longOrNullSafe(key: String): Long? =
    this[key]?.let {
        runCatching { it.jsonPrimitive.longOrNull }.getOrNull()
    }

/**
 * Non-null when [key] is present but cannot be read as a whole number of minor units.
 *
 * Distinguishes "the issuer declared no bound" from "the issuer declared a bound this parser
 * cannot read". Collapsing the two lets a decimal, an exponent form, or a value outside the
 * parsed range turn a declared ceiling into no ceiling at all.
 */
private fun JsonObject.unreadableBound(key: String): String? {
    val element = this[key] ?: return null
    val value = (element as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
    return if (value == null || value < 0) "'$key' must be a non-negative integer number of minor units" else null
}

private fun JsonObject.objectList(key: String): List<JsonObject> =
    (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
