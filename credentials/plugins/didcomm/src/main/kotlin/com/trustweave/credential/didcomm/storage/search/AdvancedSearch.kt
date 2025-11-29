package com.trustweave.credential.didcomm.storage.search

import com.trustweave.credential.didcomm.models.DidCommMessage

/**
 * Advanced search interface for DIDComm messages.
 *
 * Provides full-text search, faceted search, and complex query capabilities.
 */
interface AdvancedSearch {
    /**
     * Full-text search across message content.
     *
     * @param query Search query string
     * @param limit Maximum number of results
     * @param offset Pagination offset
     * @return List of matching messages
     */
    suspend fun fullTextSearch(
        query: String,
        limit: Int = 100,
        offset: Int = 0
    ): List<DidCommMessage>

    /**
     * Faceted search with aggregations.
     *
     * @param query Search query
     * @param facets Facets to compute
     * @return Faceted search results
     */
    suspend fun facetedSearch(
        query: SearchQuery,
        facets: List<Facet>
    ): FacetedSearchResult

    /**
     * Complex query with boolean operators.
     *
     * @param query Complex query
     * @param limit Maximum number of results
     * @param offset Pagination offset
     * @return List of matching messages
     */
    suspend fun complexQuery(
        query: ComplexQuery,
        limit: Int = 100,
        offset: Int = 0
    ): List<DidCommMessage>
}

/**
 * Search query with filters and sorting.
 */
data class SearchQuery(
    val text: String? = null,
    val filters: Map<String, Any> = emptyMap(),
    val sort: SortOrder? = null
)

/**
 * Sort order for search results.
 */
data class SortOrder(
    val field: String,
    val direction: SortDirection = SortDirection.DESC
)

enum class SortDirection {
    ASC, DESC
}

/**
 * Facet definition.
 */
data class Facet(
    val field: String,
    val type: FacetType
)

enum class FacetType {
    TERMS,  // Count distinct values
    RANGE,  // Range aggregations
    DATE    // Date range aggregations
}

/**
 * Faceted search result.
 */
data class FacetedSearchResult(
    val results: List<DidCommMessage>,
    val facets: Map<String, FacetResult>
)

/**
 * Facet result.
 */
data class FacetResult(
    val values: Map<String, Int> // value -> count
)

/**
 * Complex query with boolean operators.
 */
data class ComplexQuery(
    val conditions: List<QueryCondition>,
    val operator: BooleanOperator = BooleanOperator.AND
)

enum class BooleanOperator {
    AND, OR, NOT
}

/**
 * Query condition.
 */
data class QueryCondition(
    val field: String,
    val operator: ComparisonOperator,
    val value: Any
)

enum class ComparisonOperator {
    EQ, NE, GT, GTE, LT, LTE, LIKE, IN, BETWEEN
}

