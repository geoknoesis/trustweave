package org.trustweave.credential.didcomm.storage.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.trustweave.credential.didcomm.models.DidCommMessage
import javax.sql.DataSource

/**
 * PostgreSQL full-text search implementation.
 *
 * Uses PostgreSQL tsvector/tsquery for efficient full-text search.
 */
class PostgresFullTextSearch
    @JvmOverloads
    constructor(
        private val dataSource: DataSource,
        /**
         * Whether to create the `search_vector` column, GIN index and the trigger that tokenises message
         * bodies. The trigger copies the plaintext `body` into a searchable index, which defeats at-rest
         * encryption of message content, so it is **opt-out**: pass `false` when the storage encrypts
         * messages (encrypted rows never carry a body and are never indexed by the trigger regardless).
         * When `false`, [fullTextSearch] throws [UnsupportedOperationException].
         */
        private val enableFullTextIndex: Boolean = true,
    ) : AdvancedSearch {
        private val json = Json { ignoreUnknownKeys = true }

        init {
            if (enableFullTextIndex) createFullTextIndex()
        }

        override suspend fun fullTextSearch(
            query: String,
            limit: Int,
            offset: Int,
        ): List<DidCommMessage> =
            withContext(Dispatchers.IO) {
                if (!enableFullTextIndex) {
                    throw UnsupportedOperationException("Full-text indexing is disabled for this search instance")
                }
                dataSource.connection.use { conn ->
                    conn
                        .prepareStatement(
                            """
                SELECT message_json, encrypted_data, key_version, iv, is_encrypted
                FROM didcomm_messages
                WHERE is_encrypted IS NOT TRUE AND search_vector @@ plainto_tsquery('english', ?)
                ORDER BY ts_rank(search_vector, plainto_tsquery('english', ?)) DESC
                LIMIT ? OFFSET ?
            """,
                        ).use { stmt ->
                            stmt.setString(1, query)
                            stmt.setString(2, query)
                            stmt.setInt(3, limit)
                            stmt.setInt(4, offset)
                            stmt.executeQuery().use { rs ->
                                buildList {
                                    while (rs.next()) {
                                        val isEncrypted = rs.getBoolean("is_encrypted")
                                        // Note: Decryption would be handled by storage layer
                                        val messageJson = rs.getString("message_json")
                                        if (messageJson != null) {
                                            add(json.decodeFromString(DidCommMessage.serializer(), messageJson))
                                        }
                                    }
                                }
                            }
                        }
                }
            }

        override suspend fun facetedSearch(
            query: SearchQuery,
            facets: List<Facet>,
        ): FacetedSearchResult =
            withContext(Dispatchers.IO) {
                // Get results
                val results =
                    if (query.text != null) {
                        fullTextSearch(query.text, 100, 0)
                    } else {
                        emptyList()
                    }

                // Compute facets
                val facetResults =
                    facets.associate { facet ->
                        facet.field to computeFacet(facet, query)
                    }

                FacetedSearchResult(
                    results = results,
                    facets = facetResults,
                )
            }

        override suspend fun complexQuery(
            query: ComplexQuery,
            limit: Int,
            offset: Int,
        ): List<DidCommMessage> =
            withContext(Dispatchers.IO) {
                val built = buildWhere(query.conditions, query.operator)
                val sql = """
            SELECT message_json, encrypted_data, key_version, iv, is_encrypted
            FROM didcomm_messages
            ${built.sql}
            ORDER BY created_time DESC
            LIMIT ? OFFSET ?
        """
                dataSource.connection.use { conn ->
                    conn.prepareStatement(sql).use { stmt ->
                        built.params.forEachIndexed { i, value ->
                            val position = i + 1
                            val array = built.arrays[position]
                            if (array != null) {
                                stmt.setArray(position, conn.createArrayOf("VARCHAR", array.toTypedArray()))
                            } else {
                                stmt.setObject(position, value)
                            }
                        }
                        stmt.setInt(built.params.size + 1, limit)
                        stmt.setInt(built.params.size + 2, offset)

                        stmt.executeQuery().use { rs ->
                            buildList {
                                while (rs.next()) {
                                    val messageJson = rs.getString("message_json")
                                    if (messageJson != null) {
                                        add(json.decodeFromString(DidCommMessage.serializer(), messageJson))
                                    }
                                }
                            }
                        }
                    }
                }
            }

        private fun createFullTextIndex() {
            dataSource.connection.use { conn ->
                try {
                    // Add search_vector column if it doesn't exist
                    conn.createStatement().execute(
                        """
                    ALTER TABLE didcomm_messages
                    ADD COLUMN IF NOT EXISTS search_vector tsvector
                """,
                    )

                    // Create GIN index for full-text search
                    conn.createStatement().execute(
                        """
                    CREATE INDEX IF NOT EXISTS idx_messages_search_vector
                    ON didcomm_messages USING GIN(search_vector)
                """,
                    )

                    // Create trigger to update search vector
                    conn.createStatement().execute(
                        """
                    CREATE OR REPLACE FUNCTION update_message_search_vector()
                    RETURNS TRIGGER AS $$
                    BEGIN
                        IF COALESCE(NEW.is_encrypted, FALSE) THEN
                            NEW.search_vector := NULL;
                        ELSE
                            NEW.search_vector :=
                                to_tsvector('english', COALESCE(NEW.type, '')) ||
                                to_tsvector('english', COALESCE(NEW.body::text, ''));
                        END IF;
                        RETURN NEW;
                    END;
                    $$ LANGUAGE plpgsql;
                """,
                    )

                    conn.createStatement().execute(
                        """
                    DROP TRIGGER IF EXISTS message_search_vector_update ON didcomm_messages;
                    CREATE TRIGGER message_search_vector_update
                    BEFORE INSERT OR UPDATE ON didcomm_messages
                    FOR EACH ROW EXECUTE FUNCTION update_message_search_vector();
                """,
                    )
                } catch (e: Exception) {
                    // Index may already exist
                }
            }
        }

        private suspend fun computeFacet(
            facet: Facet,
            query: SearchQuery,
        ): FacetResult {
            // Compute facet aggregations
            // Implementation depends on facet type
            return FacetResult(emptyMap())
        }

        /** A WHERE clause with its positional (`?`) parameters, in order. */
        internal class BuiltWhere(
            val sql: String,
            /** Scalar parameters by position; array parameters are in [arrays]. */
            val params: List<Any?>,
            val arrays: Map<Int, List<Any?>> = emptyMap(),
        )

        internal fun buildWhere(
            conditions: List<QueryCondition>,
            operator: BooleanOperator,
        ): BuiltWhere {
            // Encrypted rows hold opaque blind indexes in the queryable columns and cannot be decoded
            // here, so they are never returned by this class.
            val guard = "is_encrypted IS NOT TRUE"
            if (conditions.isEmpty()) return BuiltWhere("WHERE $guard", emptyList())

            val joiner =
                when (operator) {
                    BooleanOperator.AND -> " AND "
                    BooleanOperator.OR -> " OR "
                    BooleanOperator.NOT -> " AND NOT "
                }
            val params = mutableListOf<Any?>()
            val arrays = linkedMapOf<Int, List<Any?>>()
            val fragments =
                conditions.map { condition ->
                    val column = requireAllowedColumn(condition.field)
                    when (condition.operator) {
                        ComparisonOperator.BETWEEN -> {
                            val values = condition.value as? List<*>
                            require(values != null && values.size == 2) { "BETWEEN requires exactly two values" }
                            params.add(values[0])
                            params.add(values[1])
                            "($column BETWEEN ? AND ?)"
                        }
                        ComparisonOperator.IN -> {
                            val values = condition.value as? List<*>
                            require(values != null && values.isNotEmpty()) { "IN requires a non-empty list" }
                            // An array parameter is bound at its own position once all scalars are placed.
                            params.add(arrayPlaceholder)
                            arrays[params.size] = values
                            "($column = ANY(?))"
                        }
                        else -> {
                            params.add(condition.value)
                            "($column ${comparison(condition.operator)} ?)"
                        }
                    }
                }
            // Array placeholders are bound via setArray, not setObject.
            val scalar = params.map { if (it === arrayPlaceholder) null else it }
            return BuiltWhere("WHERE ($guard) AND (${fragments.joinToString(joiner)})", scalar, arrays)
        }

        private val arrayPlaceholder = Any()

        private fun comparison(op: ComparisonOperator): String =
            when (op) {
                ComparisonOperator.EQ -> "="
                ComparisonOperator.NE -> "!="
                ComparisonOperator.GT -> ">"
                ComparisonOperator.GTE -> ">="
                ComparisonOperator.LT -> "<"
                ComparisonOperator.LTE -> "<="
                ComparisonOperator.LIKE -> "LIKE"
                ComparisonOperator.IN, ComparisonOperator.BETWEEN -> error("handled by the caller")
            }

        private val allowedColumns =
            setOf(
                "id",
                "type",
                "from_did",
                "created_time",
                "expires_time",
                "thid",
                "pthid",
                "archived",
            )

        /** Column names are interpolated into SQL, so only an exact allowlist entry is accepted. */
        private fun requireAllowedColumn(field: String): String {
            require(field in allowedColumns) { "Field '$field' is not queryable" }
            return field
        }
    }
