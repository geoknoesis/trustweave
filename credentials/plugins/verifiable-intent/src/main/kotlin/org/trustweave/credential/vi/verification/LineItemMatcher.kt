package org.trustweave.credential.vi.verification

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.trustweave.credential.vi.crypto.Disclosures
import org.trustweave.credential.vi.model.Constraint

/** Assign cart quantities to authorized requirements without reusing a requirement's capacity. */
internal object LineItemMatcher {
    private const val MAX_ENTRIES = 128
    private const val SAFE_TOTAL = Long.MAX_VALUE / 4

    fun check(
        constraint: Constraint.LineItems,
        fulfillment: JsonObject,
        disclosures: Map<String, String>,
    ): String? =
        try {
            validate(constraint, fulfillment, disclosures)
            null
        } catch (invalid: IllegalArgumentException) {
            invalid.message ?: "Invalid line items"
        } catch (overflow: ArithmeticException) {
            "Line-item quantity overflow"
        }

    private fun string(
        obj: JsonObject,
        key: String,
    ): String? = (obj[key] as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotBlank() }?.content

    private fun productId(item: JsonObject): String =
        if ("id" in item) {
            string(item, "id") ?: throw IllegalArgumentException("Product id must be a non-empty string")
        } else {
            string(item, "sku") ?: throw IllegalArgumentException("Product needs an id or sku")
        }

    private fun quantity(obj: JsonObject): Long =
        (obj["quantity"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
            ?: throw IllegalArgumentException("Line-item quantity must be an integer")

    private fun validate(
        c: Constraint.LineItems,
        fulfillment: JsonObject,
        disclosures: Map<String, String>,
    ) {
        require(c.matchMode in setOf("minimum", "exact")) { "Invalid line-item match_mode" }
        require(c.items.size in 1..MAX_ENTRIES) { "Line-item requirements must contain 1..128 entries" }
        val ids = mutableSetOf<String>()
        val limits = mutableListOf<Long>()
        val choices = mutableListOf<Set<String>>()
        for (entry in c.items) {
            val id = string(entry, "id") ?: throw IllegalArgumentException("Line-item requirement needs an id")
            require(ids.add(id)) { "Duplicate line-item requirement id" }
            val limit = quantity(entry)
            require(limit in 1..SAFE_TOTAL) { "Line-item limit must be positive and within supported range" }
            limits += limit
            val alternatives =
                entry["acceptable_items"] as? JsonArray
                    ?: throw IllegalArgumentException("acceptable_items must be an array")
            require(alternatives.size <= MAX_ENTRIES) { "Too many acceptable items" }
            val resolved =
                alternatives
                    .map { element ->
                        var item = element as? JsonObject ?: throw IllegalArgumentException("Acceptable item must be an object")
                        if ("..." in item) {
                            val hash = string(item, "...") ?: throw IllegalArgumentException("Invalid item disclosure reference")
                            val raw = disclosures[hash] ?: throw IllegalArgumentException("Missing acceptable-item disclosure")
                            require(Disclosures.hash(raw) == hash) { "Item disclosure hash mismatch" }
                            item = Disclosures.parse(raw)?.value as? JsonObject
                                ?: throw IllegalArgumentException("Invalid acceptable-item disclosure")
                        }
                        require(string(item, "title") != null) { "Acceptable item needs a title" }
                        productId(item)
                    }.toSet()
            choices += resolved // An explicitly empty array is a wildcard requirement.
        }
        val cart = fulfillment["line_items"] as? JsonArray ?: throw IllegalArgumentException("Missing line_items array")
        require(cart.size in 1..MAX_ENTRIES) { "Cart must contain 1..128 entries" }
        val amounts = linkedMapOf<String, Long>()
        for (element in cart) {
            val item = element as? JsonObject ?: throw IllegalArgumentException("Cart item must be an object")
            val id = productId(item)
            val count = quantity(item)
            require(count >= 0) { "Cart quantity must not be negative" }
            amounts[id] = Math.addExact(amounts[id] ?: 0, count)
        }
        val total = amounts.values.fold(0L, Math::addExact)
        require(total <= SAFE_TOTAL) { "Cart quantity exceeds supported range" }
        // Feasible circulation with lower bounds: every cart quantity must be assigned;
        // exact mode additionally assigns at least one unit to every requirement.
        val source = 0
        val sink = 1
        val productStart = 2
        val requirementStart = productStart + amounts.size
        val superSource = requirementStart + limits.size
        val superSink = superSource + 1
        val capacity = Array(superSink + 1) { LongArray(superSink + 1) }
        val balance = LongArray(superSink + 1)

        fun edge(
            from: Int,
            to: Int,
            lower: Long,
            upper: Long,
        ) {
            require(lower <= upper) { "Unsatisfied line-item requirement" }
            capacity[from][to] += upper - lower
            balance[from] -= lower
            balance[to] += lower
        }
        for ((offset, entry) in amounts.entries.withIndex()) {
            val product = productStart + offset
            edge(source, product, entry.value, entry.value)
            for ((index, allowed) in choices.withIndex()) {
                if (allowed.isEmpty() || entry.key in allowed) edge(product, requirementStart + index, 0, total)
            }
        }
        for ((index, limit) in limits.withIndex()) {
            edge(requirementStart + index, sink, if (c.matchMode == "exact") 1 else 0, limit)
        }
        edge(sink, source, 0, SAFE_TOTAL)
        var required = 0L
        for (node in 0 until superSource) {
            if (balance[node] > 0) {
                capacity[superSource][node] = balance[node]
                required += balance[node]
            } else {
                capacity[node][superSink] = -balance[node]
            }
        }
        var delivered = 0L
        while (delivered < required) {
            val parent = IntArray(capacity.size) { -1 }
            val queue = java.util.ArrayDeque<Int>()
            queue.add(superSource)
            parent[superSource] = superSource
            while (queue.isNotEmpty() && parent[superSink] < 0) {
                val from = queue.removeFirst()
                for (to in capacity.indices) {
                    if (parent[to] < 0 && capacity[from][to] > 0) {
                        parent[to] = from
                        queue.add(to)
                    }
                }
            }
            require(parent[superSink] >= 0) { "Cart exceeds authorized quantities or omits exact requirements" }
            var flow = required - delivered
            var node = superSink
            while (node != superSource) {
                flow = minOf(flow, capacity[parent[node]][node])
                node = parent[node]
            }
            node = superSink
            while (node != superSource) {
                val from = parent[node]
                capacity[from][node] -= flow
                capacity[node][from] += flow
                node = from
            }
            delivered += flow
        }
    }
}
