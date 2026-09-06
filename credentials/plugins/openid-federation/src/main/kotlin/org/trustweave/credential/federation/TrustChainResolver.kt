package org.trustweave.credential.federation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Resolves OpenID Federation 1.0 trust chains via HTTP.
 *
 * A trust chain is an ordered sequence of Entity Statement JWTs that establishes
 * a verifiable path of trust from a leaf entity to a known trust anchor:
 *
 * ```
 * [leaf Entity Configuration, intermediate Subordinate Statement?, ..., trust anchor Subordinate Statement, anchor Entity Configuration]
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
 * each statement's signature is verified with its superior's endorsed keys and the anchor is pinned.
 */
class TrustChainResolver(
    /**
     * Defaults to the SSRF-guarded client, because every URL this resolver fetches is
     * attacker-influenced: `authority_hints` comes from the leaf entity's own JWT, and each
     * authority's `federation_fetch_endpoint` is read out of a statement that was itself just
     * fetched. A bare client here would let a federation peer steer requests at loopback, private
     * ranges, or a cloud metadata endpoint. Inject your own only if you have your own egress control.
     */
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val maxChainLength: Int = 5,
    private val clockSkewSeconds: Long = 30L,
    /** Cap on a single fetched statement; a compromised authority must not exhaust memory. */
    private val maxResponseBytes: Int = DEFAULT_MAX_RESPONSE_BYTES,
    /** Public trust-anchor keys provisioned independently of the presented chain. */
    private val trustedAnchorKeys: Map<String, FederationJwkSet> = emptyMap(),
) {
    init {
        require(maxChainLength in 1..100)
        require(clockSkewSeconds in 0..300)
        require(maxResponseBytes in 1..(16 * 1024 * 1024))
    }

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
            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .build()
            httpClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) {
                    "Failed to fetch entity configuration for $entityId: HTTP ${response.code}"
                }
                response.body?.let { readBounded(it, url) }
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
            visited = mutableSetOf(),
            remainingEdges = intArrayOf(64),
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
     * 3. Verify the leaf self-signature, subordinate signatures against superior-endorsed keys,
     *    issuer/subject links, and the final anchor configuration against configured anchor keys.
     * 4. Constraint propagation: `max_path_length` from any intermediate is not exceeded.
     *
     * @param chain The trust chain to verify.
     * @return `true` if all checks pass, `false` otherwise.
     */
    fun verifyChain(chain: TrustChain): Boolean {
        if (chain.statements.isEmpty() || chain.statements.size > maxChainLength + 2) return false
        val anchorKeys = trustedAnchorKeys[chain.trustAnchorId] ?: return false
        val parsed = chain.statements.map { jwtProcessor.parse(it) ?: return false }
        val now = System.currentTimeMillis() / 1000L
        if (parsed.any {
                it.iat < 0 || it.iat > now + clockSkewSeconds || it.exp <= it.iat || it.exp <= now - clockSkewSeconds
            }
        ) {
            return false
        }
        val leaf = parsed.first()
        val anchor = parsed.last()
        if (leaf.iss != chain.leafEntityId || leaf.sub != leaf.iss) return false
        if (anchor.iss != chain.trustAnchorId || anchor.sub != anchor.iss) return false
        if (!jwtProcessor.verify(chain.statements.first(), leaf.jwks)) return false
        if (!jwtProcessor.verify(chain.statements.last(), anchorKeys)) return false
        for (i in 0 until parsed.lastIndex) {
            if (parsed[i].iss != parsed[i + 1].sub) return false
            if (!jwtProcessor.verify(chain.statements[i], parsed[i + 1].jwks)) return false
        }
        // At index j, j-1 intermediate entities are subordinate to the statement's subject.
        for ((index, statement) in parsed.withIndex()) {
            val limit = statement.constraints?.maxPathLength ?: continue
            if (limit < 0 || maxOf(0, minOf(index - 1, parsed.size - 3)) > limit) return false
        }
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
        visited: MutableSet<String>,
        remainingEdges: IntArray,
    ): TrustChainResolutionResult {
        if (depth > maxChainLength || !visited.add(entityId)) {
            return TrustChainResolutionResult.Failure(
                reason = "Exceeded maximum chain length of $maxChainLength",
                entityId = entityId,
            )
        }

        val leafJwt =
            runCatching { fetchEntityConfiguration(entityId) }.getOrElse { ex ->
                if (ex is kotlinx.coroutines.CancellationException) throw ex
                return TrustChainResolutionResult.Failure(
                    reason = "Could not fetch entity configuration for $entityId: ${ex.message}",
                    entityId = entityId,
                )
            }

        val leafStatement =
            jwtProcessor.parse(leafJwt)
                ?: return TrustChainResolutionResult.Failure(
                    reason = "Could not parse entity configuration JWT for $entityId",
                    entityId = entityId,
                )

        if (leafStatement.iss != entityId || leafStatement.sub != entityId || !jwtProcessor.verify(leafJwt, leafStatement.jwks)) {
            return TrustChainResolutionResult.Failure("Invalid entity configuration", entityId)
        }
        val hints = leafStatement.authorityHints
        if (hints.isNullOrEmpty()) {
            return TrustChainResolutionResult.Failure(
                reason = "No authority_hints found in entity configuration for $entityId",
                entityId = entityId,
            )
        }

        val currentStatements = if (accumulatedStatements.isEmpty()) listOf(leafJwt) else accumulatedStatements
        for (hint in hints) {
            if (remainingEdges[0]-- <= 0) break
            val subordinateJwt =
                runCatching {
                    fetchSubordinateStatement(authorityId = hint, subjectId = entityId)
                }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull() ?: continue
            if (hint in trustedAnchorIds) {
                if (hint !in trustedAnchorKeys) continue
                val anchorJwt =
                    runCatching {
                        fetchEntityConfiguration(
                            hint,
                        )
                    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
                        ?: continue
                val chain =
                    TrustChain(
                        statements = currentStatements + subordinateJwt + anchorJwt,
                        trustAnchorId = hint,
                        leafEntityId = checkNotNull(jwtProcessor.parse(currentStatements.first())).sub,
                    )
                if (verifyChain(chain)) {
                    return TrustChainResolutionResult.Success(chain, System.currentTimeMillis() / 1000L)
                }
            } else {
                val result = resolveInternal(hint, trustedAnchorIds, depth + 1, currentStatements + subordinateJwt, visited, remainingEdges)
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
    private suspend fun fetchSubordinateStatement(
        authorityId: String,
        subjectId: String,
    ): String =
        withContext(Dispatchers.IO) {
            val authorityConfigJwt = fetchEntityConfiguration(authorityId)
            val authorityConfig =
                jwtProcessor.parse(authorityConfigJwt)
                    ?: error("Could not parse entity configuration for authority $authorityId")

            val fetchEndpoint =
                authorityConfig.metadata
                    ?.federationEntity
                    ?.federationFetchEndpoint
                    ?: error("No federation_fetch_endpoint found for $authorityId")

            val url = subordinateStatementUrl(fetchEndpoint, subjectId)
            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .build()
            httpClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) {
                    "Failed to fetch subordinate statement for $subjectId from $authorityId: HTTP ${response.code}"
                }
                response.body?.let { readBounded(it, url) }
                    ?: error("Empty response body fetching subordinate statement for $subjectId")
            }
        }

    private fun readBounded(
        body: okhttp3.ResponseBody,
        url: Any,
    ): String {
        // readNBytes(limit + 1) so "exactly at the limit" is accepted and one byte over is caught,
        // without ever materialising the whole body.
        val bytes = body.byteStream().readNBytes(maxResponseBytes + 1)
        if (bytes.size > maxResponseBytes) {
            throw IllegalStateException(
                "Federation statement exceeds maximum allowed size ($maxResponseBytes bytes) at: $url",
            )
        }
        return String(bytes, Charsets.UTF_8)
    }

    public companion object {
        /** 1 MiB. Entity statements are JWTs; anything near this is already pathological. */
        public const val DEFAULT_MAX_RESPONSE_BYTES: Int = 1 * 1024 * 1024

        /** The client this resolver uses unless one is injected. */
        public fun defaultHttpClient(): OkHttpClient =
            org.trustweave.core.net
                .ssrfGuardedOkHttpClient()

        /**
         * Builds `{fetchEndpoint}?sub={subjectId}` with the subject properly encoded.
         *
         * `subjectId` is an entity identifier taken from an untrusted statement and is itself a URL.
         * String concatenation let it append its own query parameters, or cut the request short with
         * a fragment; going through [okhttp3.HttpUrl] keeps it inside the one parameter it belongs in.
         */
        public fun subordinateStatementUrl(
            fetchEndpoint: String,
            subjectId: String,
        ): okhttp3.HttpUrl {
            val base =
                fetchEndpoint.toHttpUrlOrNull()
                    ?: error("federation_fetch_endpoint is not a valid URL: $fetchEndpoint")
            return base.newBuilder().addQueryParameter("sub", subjectId).build()
        }
    }
}
