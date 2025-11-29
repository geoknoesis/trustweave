package com.trustweave.credential.didcomm.storage.search

import com.trustweave.credential.didcomm.models.DidCommMessage
import com.trustweave.credential.didcomm.storage.database.PostgresDidCommMessageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.sql.Connection
import javax.sql.DataSource

/**
 * PostgreSQL full-text search implementation.
 *
 * Uses PostgreSQL tsvector/tsquery for efficient full-text search.
 */
class PostgresFullTextSearch(
    private val dataSource: DataSource
) : AdvancedSearch {

    private val json = Json { ignoreUnknownKeys = true }

    init {
        createFullTextIndex()
    }

    override suspend fun fullTextSearch(
        query: String,
        limit: Int,
        offset: Int
    ): List<DidCommMessage> = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                SELECT message_json, encrypted_data, key_version, iv, is_encrypted
                FROM didcomm_messages
                WHERE search_vector @@ plainto_tsquery('english', ?)
                ORDER BY ts_rank(search_vector, plainto_tsquery('english', ?)) DESC
                LIMIT ? OFFSET ?
            """).use { stmt ->
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
        facets: List<Facet>
    ): FacetedSearchResult = withContext(Dispatchers.IO) {
        // Get results
        val results = if (query.text != null) {
            fullTextSearch(query.text, 100, 0)
        } else {
            emptyList()
        }

        // Compute facets
        val facetResults = facets.associate { facet ->
            facet.field to computeFacet(facet, query)
        }

        FacetedSearchResult(
            results = results,
            facets = facetResults
        )
    }

    override suspend fun complexQuery(
        query: ComplexQuery,
        limit: Int,
        offset: Int
    ): List<DidCommMessage> = withContext(Dispatchers.IO) {
        val sql = buildComplexQuery(query, limit, offset)
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                // Set parameters based on conditions
                query.conditions.forEachIndexed { index, condition ->
                    setConditionParameter(stmt, index + 1, condition)
                }
                stmt.setInt(query.conditions.size + 1, limit)
                stmt.setInt(query.conditions.size + 2, offset)

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
                conn.createStatement().execute("""
                    ALTER TABLE didcomm_messages
                    ADD COLUMN IF NOT EXISTS search_vector tsvector
                """)

                // Create GIN index for full-text search
                conn.createStatement().execute("""
                    CREATE INDEX IF NOT EXISTS idx_messages_search_vector
                    ON didcomm_messages USING GIN(search_vector)
                """)

                // Create trigger to update search vector
                conn.createStatement().execute("""
                    CREATE OR REPLACE FUNCTION update_message_search_vector()
                    RETURNS TRIGGER AS $$
                    BEGIN
                        NEW.search_vector :=
                            to_tsvector('english', COALESCE(NEW.type, '')) ||
                            to_tsvector('english', COALESCE(NEW.body::text, ''));
                        RETURN NEW;
                    END;
                    $$ LANGUAGE plpgsql;
                """)

                conn.createStatement().execute("""
                    DROP TRIGGER IF EXISTS message_search_vector_update ON didcomm_messages;
                    CREATE TRIGGER message_search_vector_update
                    BEFORE INSERT OR UPDATE ON didcomm_messages
                    FOR EACH ROW EXECUTE FUNCTION update_message_search_vector();
                """)
            } catch (e: Exception) {
                // Index may already exist
            }
        }
    }

    private suspend fun computeFacet(
        facet: Facet,
        query: SearchQuery
    ): FacetResult {
        // Compute facet aggregations
        // Implementation depends on facet type
        return FacetResult(emptyMap())
    }

    private fun buildComplexQuery(query: ComplexQuery, limit: Int, offset: Int): String {
        val conditions = query.conditions.mapIndexed { index, condition ->
            buildConditionSQL(condition, index + 1)
        }

        val operator = when (query.operator) {
            BooleanOperator.AND -> " AND "
            BooleanOperator.OR -> " OR "
            BooleanOperator.NOT -> " AND NOT "
        }

        val whereClause = if (conditions.isNotEmpty()) {
            "WHERE ${conditions.joinToString(operator)}"
        } else {
            ""
        }

        return """
            SELECT message_json, encrypted_data, key_version, iv, is_encrypted
            FROM didcomm_messages
            $whereClause
            ORDER BY created_time DESC
            LIMIT ? OFFSET ?
        """
    }

    private fun buildConditionSQL(condition: QueryCondition, paramIndex: Int): String {
        val field = condition.field
        val operator = when (condition.operator) {
            ComparisonOperator.EQ -> "= ?"
            ComparisonOperator.NE -> "!= ?"
            ComparisonOperator.GT -> "> ?"
            ComparisonOperator.GTE -> ">= ?"
            ComparisonOperator.LT -> "< ?"
            ComparisonOperator.LTE -> "<= ?"
            ComparisonOperator.LIKE -> "LIKE ?"
            ComparisonOperator.IN -> "IN (?)"
            ComparisonOperator.BETWEEN -> "BETWEEN ? AND ?"
        }
        return "$field $operator"
    }

    private fun setConditionParameter(
        stmt: java.sql.PreparedStatement,
        index: Int,
        condition: QueryCondition
    ) {
        when (condition.operator) {
            ComparisonOperator.BETWEEN -> {
                val values = condition.value as? List<*>
                stmt.setObject(index, values?.get(0))
                stmt.setObject(index + 1, values?.get(1))
            }
            ComparisonOperator.IN -> {
                val values = condition.value as? List<*>
                stmt.setArray(index, (stmt.connection as Connection).createArrayOf("VARCHAR", values?.toTypedArray()))
            }
            else -> {
                stmt.setObject(index, condition.value)
            }
        }
    }
}

