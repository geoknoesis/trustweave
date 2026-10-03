package org.trustweave.wallet.database

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.trustweave.wallet.CredentialFilter
import java.sql.PreparedStatement

// SQL construction and parameter binding of [DatabaseWallet]'s query and paging paths.
// Internal; behaviour identical to the inline code they were extracted from.

/**
 * `SELECT` for [DatabaseWallet.query]. Tag/collection filters are pushed down to SQL because tags
 * and collections are wallet-level metadata stored in the credential_tags / credential_collections
 * tables, keyed by the database credential id; they cannot be evaluated against the credential
 * JSON in memory. A credential must carry ALL requested tags and belong to ALL requested
 * collections (AND, consistent with predicate chaining).
 */
internal fun buildTagCollectionQuerySql(
    requestedTags: Collection<String>,
    requestedCollections: Collection<String>,
): String =
    buildString {
        append("SELECT credential_data FROM credentials WHERE wallet_id = ? AND archived = FALSE")
        if (requestedTags.isNotEmpty()) {
            val placeholders = requestedTags.joinToString(", ") { "?" }
            append(
                " AND id IN (SELECT credential_id FROM credential_tags WHERE tag IN ($placeholders)" +
                    " GROUP BY credential_id HAVING COUNT(DISTINCT tag) = ?)",
            )
        }
        if (requestedCollections.isNotEmpty()) {
            val placeholders = requestedCollections.joinToString(", ") { "?" }
            append(
                " AND id IN (SELECT credential_id FROM credential_collections WHERE collection_id IN ($placeholders)" +
                    " GROUP BY credential_id HAVING COUNT(DISTINCT collection_id) = ?)",
            )
        }
        append(" ORDER BY id")
    }

/** Binds the parameters of [buildTagCollectionQuerySql] in placeholder order. */
internal fun bindTagCollectionQuery(
    stmt: PreparedStatement,
    walletId: String,
    requestedTags: Collection<String>,
    requestedCollections: Collection<String>,
) {
    var index = 1
    stmt.setString(index++, walletId)
    requestedTags.forEach { stmt.setString(index++, it) }
    if (requestedTags.isNotEmpty()) {
        stmt.setInt(index++, requestedTags.size)
    }
    requestedCollections.forEach { stmt.setString(index++, it) }
    if (requestedCollections.isNotEmpty()) {
        stmt.setInt(index++, requestedCollections.size)
    }
}

/** Extra `AND ...` clause (with leading space, or empty) and its string parameters for one page. */
internal class PageQuery(
    val extraSql: String,
    val values: List<String>,
)

/**
 * Builds the keyset-paging condition for [DatabaseWallet.pageRecords]: the cursor, plus on
 * PostgreSQL the JSON parts of [filter] pushed into the query (containment index). H2 scans only
 * the bounded page; remaining status/expiry filters are evaluated after the result set is closed.
 */
internal fun buildPageQuery(
    after: String?,
    filter: CredentialFilter?,
    postgres: Boolean,
): PageQuery {
    val predicates = mutableListOf<String>()
    val values = mutableListOf<String>()
    if (after != null) {
        predicates.add("id > ?")
        values.add(after)
    }
    if (postgres && filter != null) {
        fun contains(documents: List<JsonObject>) {
            if (documents.isEmpty()) return
            predicates.add(
                documents.joinToString(" OR ", "(", ")") { "CAST(credential_data AS jsonb) @> CAST(? AS jsonb)" },
            )
            values.addAll(documents.map { it.toString() })
        }
        filter.issuer?.let { issuer ->
            contains(
                listOf(
                    buildJsonObject { put("issuer", issuer) },
                    buildJsonObject { putJsonObject("issuer") { put("id", issuer) } },
                ),
            )
        }
        filter.type?.let { types ->
            contains(
                types.map { type ->
                    buildJsonObject { putJsonArray("type") { add(JsonPrimitive(type)) } }
                },
            )
        }
        filter.subjectId?.let { subject ->
            contains(
                listOf(
                    buildJsonObject { putJsonObject("credentialSubject") { put("id", subject) } },
                ),
            )
        }
    }
    val extra = predicates.joinToString(" AND ").let { if (it.isEmpty()) "" else " AND $it" }
    return PageQuery(extra, values)
}
