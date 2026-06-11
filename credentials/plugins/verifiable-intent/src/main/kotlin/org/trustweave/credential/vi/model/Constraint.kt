package org.trustweave.credential.vi.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

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
    public data class AllowedMerchants(override val raw: JsonObject, val allowed: List<JsonObject>) : Constraint() {
        override val type: String get() = TYPE
        public companion object { public const val TYPE: String = "mandate.checkout.allowed_merchants" }
    }

    /** `mandate.checkout.line_items` — `items` of `{id, acceptable_items, quantity}`; `match_mode`. */
    public data class LineItems(
        override val raw: JsonObject,
        val items: List<JsonObject>,
        val matchMode: String,
    ) : Constraint() {
        override val type: String get() = TYPE
        public companion object { public const val TYPE: String = "mandate.checkout.line_items" }
    }

    /** `mandate.payment.allowed_payees` — `allowed` holds payee objects (often SD refs). */
    public data class AllowedPayees(override val raw: JsonObject, val allowed: List<JsonObject>) : Constraint() {
        override val type: String get() = TYPE
        public companion object { public const val TYPE: String = "mandate.payment.allowed_payees" }
    }

    /** `mandate.payment.amount_range` — per-transaction bounds in integer minor units. */
    public data class AmountRange(
        override val raw: JsonObject,
        val currency: String,
        val min: Int?,
        val max: Int?,
    ) : Constraint() {
        override val type: String get() = TYPE
        public companion object { public const val TYPE: String = "mandate.payment.amount_range" }
    }

    /** `mandate.payment.budget` — cumulative spend cap (network-enforced, stateful). */
    public data class Budget(
        override val raw: JsonObject,
        val currency: String,
        val max: Int,
        val min: Int?,
    ) : Constraint() {
        override val type: String get() = TYPE
        public companion object { public const val TYPE: String = "mandate.payment.budget" }
    }

    /** `mandate.payment.reference` — links payment mandate to the checkout disclosure. */
    public data class Reference(
        override val raw: JsonObject,
        val conditionalTransactionId: String,
    ) : Constraint() {
        override val type: String get() = TYPE
        public companion object { public const val TYPE: String = "mandate.payment.reference" }
    }

    /** `mandate.payment.recurrence` — merchant-initiated subscription terms (network-enforced). */
    public data class Recurrence(override val raw: JsonObject) : Constraint() {
        override val type: String get() = TYPE
        public companion object { public const val TYPE: String = "mandate.payment.recurrence" }
    }

    /** `mandate.payment.agent_recurrence` — agent-managed recurring terms (network-enforced). */
    public data class AgentRecurrence(override val raw: JsonObject) : Constraint() {
        override val type: String get() = TYPE
        public companion object { public const val TYPE: String = "mandate.payment.agent_recurrence" }
    }

    /** Any constraint type not in the registry; fields preserved verbatim in [raw]. */
    public data class Unknown(override val raw: JsonObject, override val type: String) : Constraint()

    public companion object {
        /**
         * Parses a constraint object into its typed form, falling back to [Unknown] for
         * unrecognized `type` values. Mirrors `models/constraints.py::parse_constraint`.
         */
        public fun parse(obj: JsonObject): Constraint {
            val type = obj["type"]?.jsonPrimitive?.contentOrNullSafe().orEmpty()
            return when (type) {
                AllowedMerchants.TYPE -> AllowedMerchants(obj, obj.objectList("allowed"))
                LineItems.TYPE -> LineItems(obj, obj.objectList("items"), obj.string("match_mode") ?: "minimum")
                AllowedPayees.TYPE -> AllowedPayees(obj, obj.objectList("allowed"))
                AmountRange.TYPE -> AmountRange(obj, obj.string("currency") ?: "USD", obj.intOrNullSafe("min"), obj.intOrNullSafe("max"))
                Budget.TYPE -> Budget(obj, obj.string("currency") ?: "USD", obj.intOrNullSafe("max") ?: 0, obj.intOrNullSafe("min"))
                Reference.TYPE -> Reference(obj, obj.string("conditional_transaction_id").orEmpty())
                Recurrence.TYPE -> Recurrence(obj)
                AgentRecurrence.TYPE -> AgentRecurrence(obj)
                else -> Unknown(obj, type)
            }
        }
    }
}

private fun JsonPrimitive.contentOrNullSafe(): String? =
    runCatching { content }.getOrNull()

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNullSafe()

private fun JsonObject.intOrNullSafe(key: String): Int? = this[key]?.let {
    runCatching { it.jsonPrimitive.intOrNull }.getOrNull()
}

private fun JsonObject.objectList(key: String): List<JsonObject> =
    (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
