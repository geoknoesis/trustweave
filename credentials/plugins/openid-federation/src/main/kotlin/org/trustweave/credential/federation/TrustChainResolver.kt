package org.trustweave.credential.federation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Resolves OpenID Federation 1.0 trust chains via HTTP.
 *
 * A trust chain is an ordered sequence of Entity Statement JWTs that establishes
 * a verifiable path of trust from a leaf entity to a known trust anchor:
 *
 * ```
 * [leaf Entity Configuration, intermediate Subordinate Statement?, ..., trust anchor Subordinate Statement]
 * ```
 *
 * ### Resolution algorithm
 * 1. Fetch `{entityId}/.well-known/openid-federation` to obtain the leaf's self-signed
 *    Entity Configuration JWT.
 * 2. Parse `authority_hints` from the leaf JWT to identify parent entities.
 * 3. For each hint:
 *    - If the hint is in [trustedAnchorIds], fetch the hint's Entity Configuration to
 *      discover its `federation_fetch_endpoint`, then fetch
 *      `{fetch_endpoint}?sub={entityId}` to obtain the Subordinate Statement.
 *    - Otherwise, recurse up the hierarchy (bounded by [maxChainLength]).
 * 4. Return [TrustChainResolutionResult.Success] as soon as a path to a trusted anchor
 *    is found, or [TrustChainResolutionResult.Failure] when no path exists.
 *
 * ### Verification
 * Call [verifyChain] on a resolved chain before trusting it. Verification checks
 * expiry of all statements (with [clockSkewSeconds] tolerance) and validates that
 * each statement's `jwks` field matches the key used to sign the next statement.
 */
class TrustChainResolver(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val maxChainLength: Int = 5,
    private val clockSkewSeconds: Long = 30L,
) {

    private val jwtProcessor = EntityStatementJwtProcessor(httpClient)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fetches the Entity Configuration JWT for [entityId] from the well-known URL.
     *
     * @param entityId The entity identifier URI.
     * @return Raw compact-serialized JWT string.
     * @throws RuntimeException if the HTTP request fails or returns a non-2xx status.
     */
    suspend fun fetchEntityConfiguration(entityId: String): String =
        withContext(Dispatchers.IO) {
            val url = EntityConfigurationEndpoint.getUrl(entityId)
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) {
                    "Failed to fetch entity configuration for $entityId: HTTP ${response.code}"
                }
                response.body?.string()
                    ?: error("Empty response body fetching entity configuration for $entityId")
            }
        }

    /**
     * Resolves a trust chain from [entityId] to one of [trustedAnchorIds].
     *
     * @param entityId The leaf entity whose chain to resolve.
     * @param trustedAnchorIds Set of trusted trust anchor entity identifiers.
     * @return [TrustChainResolutionResult.Success] containing the ordered JWT list, or
     *   [TrustChainResolutionResult.Failure] with a reason string.
     */
    suspend fun resolve(
        entityId: String,
        trustedAnchorIds: Set<String>,
    ): TrustChainResolutionResult =
        resolveInternal(
            entityId = entityId,
            trustedAnchorIds = trustedAnchorIds,
            depth = 0,
            accumulatedStatements = emptyList(),
        )

    /**
     * Parses an Entity Statement JWT payload without verifying the signature.
     *
     * Returns `null` if the JWT cannot be parsed.
     *
     * @param jwt Compact serialized JWT string.
     */
    fun parseEntityStatement(jwt: String): EntityStatement? = jwtProcessor.parse(jwt)

    /**
     * Verifies a complete [TrustChain].
     *
     * Performs the following checks:
     * 1. The chain is non-empty.
     * 2. No statement has expired (using [clockSkewSeconds] tolerance).
     * 3. Each statement's `jwks` contains the key that signed the next statement in the chain.
     *    (The final statement is the trust anchor's self-signed Entity Configuration or
     *    Subordinate Statement, whose verification key must be pre-configured externally.)
     * 4. Constraint propagation: `max_path_length` from any intermediate is not exceeded.
     *
     * @param chain The trust chain to verify.
     * @return `true` if all checks pass, `false` otherwise.
     */
    fun verifyChain(chain: TrustChain): Boolean {
        if (chain.statements.isEmpty()) return false

        val nowSeconds = System.currentTimeMillis() / 1000L
        val parsedStatements = chain.statements.mapNotNull { jwtProcessor.parse(it) }

        if (parsedStatements.size != chain.statements.size) return false

        // Check expiry on every statement
        val allValid = parsedStatements.all { stmt ->
            stmt.exp + clockSkewSeconds >= nowSeconds
        }
        if (!allValid) return false

        // Verify that each statement's subject key set covers the key used to
        // sign the next statement in the chain.
        for (i in 0 until chain.statements.size - 1) {
            val current = parsedStatements[i]
            val nextJwt = chain.statements[i + 1]
            // The next JWT must be verifiable with the keys declared in the current statement's jwks.
            if (!jwtProcessor.verify(nextJwt, current.jwks)) return false
        }

        // Validate max_path_length constraints
        val intermediateCount = chain.statements.size - 2  // leaf + trust anchor not counted
        val maxPathLength = parsedStatements.mapNotNull { it.constraints?.maxPathLength }.minOrNull()
        if (maxPathLength != null && intermediateCount > maxPathLength) return false

        return true
    }

    // -------------------------------------------------------------------------
    // Private
    // -------------------------------------------------------------------------

    private suspend fun resolveInternal(
        entityId: String,
        trustedAnchorIds: Set<String>,
        depth: Int,
        accumulatedStatements: List<String>,
    ): TrustChainResolutionResult {
        if (depth > maxChainLength) {
            return TrustChainResolutionResult.Failure(
                reason = "Exceeded maximum chain length of $maxChainLength",
                entityId = entityId,
            )
        }

        val leafJwt = runCatching { fetchEntityConfiguration(entityId) }.getOrElse { ex ->
            return TrustChainResolutionResult.Failure(
                reason = "Could not fetch entity configuration for $entityId: ${ex.message}",
                entityId = entityId,
            )
        }

        val leafStatement = jwtProcessor.parse(leafJwt)
            ?: return TrustChainResolutionResult.Failure(
                reason = "Could not parse entity configuration JWT for $entityId",
                entityId = entityId,
            )

        val hints = leafStatement.authorityHints
        if (hints.isNullOrEmpty()) {
            return TrustChainResolutionResult.Failure(
                reason = "No authority_hints found in entity configuration for $entityId",
                entityId = entityId,
            )
        }

        val currentStatements = accumulatedStatements + leafJwt

        for (hint in hints) {
            if (hint in trustedAnchorIds) {
                // Attempt to fetch the Subordinate Statement from this trust anchor
                val subordinateJwt = runCatching {
                    fetchSubordinateStatement(authorityId = hint, subjectId = entityId)
                }.getOrNull() ?: continue

                val chain = TrustChain(
                    statements = currentStatements + subordinateJwt,
                    trustAnchorId = hint,
                    leafEntityId = entityId,
                )
                return TrustChainResolutionResult.Success(
                    chain = chain,
                    verifiedAt = System.currentTimeMillis() / 1000L,
                )
            } else {
                // Recurse: try to find a trusted anchor above this hint
                val result = resolveInternal(
                    entityId = hint,
                    trustedAnchorIds = trustedAnchorIds,
                    depth = depth + 1,
                    accumulatedStatements = currentStatements,
                )
                if (result is TrustChainResolutionResult.Success) return result
            }
        }

        return TrustChainResolutionResult.Failure(
            reason = "No path to a trusted anchor found from $entityId",
            entityId = entityId,
        )
    }

    /**
     * Fetches the Subordinate Statement issued by [authorityId] about [subjectId].
     *
     * The authority's `federation_fetch_endpoint` is discovered from its own
     * Entity Configuration.
     */
    private suspend fun fetchSubordinateStatement(authorityId: String, subjectId: String): String =
        withContext(Dispatchers.IO) {
            val authorityConfigJwt = fetchEntityConfiguration(authorityId)
            val authorityConfig = jwtProcessor.parse(authorityConfigJwt)
                ?: error("Could not parse entity configuration for authority $authorityId")

            val fetchEndpoint = authorityConfig.metadata
                ?.federationEntity
                ?.federationFetchEndpoint
                ?: error("No federation_fetch_endpoint found for $authorityId")

            val url = "$fetchEndpoint?sub=${subjectId}"
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) {
                    "Failed to fetch subordinate statement for $subjectId from $authorityId: HTTP ${response.code}"
                }
                response.body?.string()
                    ?: error("Empty response body fetching subordinate statement for $subjectId")
            }
        }
}
