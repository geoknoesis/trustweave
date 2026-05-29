package org.trustweave.integrations.entra

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Clock
import java.time.Instant

/**
 * OAuth2 `client_credentials` token client for the Entra Verified ID Request Service.
 *
 * Caches the bearer token until it nears expiry (with a [refreshSkew] safety margin).
 * Concurrent callers share a single inflight refresh via [Mutex] so we never hammer
 * AAD with parallel token requests for the same tenant.
 *
 * Reference: https://learn.microsoft.com/entra/identity-platform/v2-oauth2-client-creds-grant-flow
 */
class EntraTokenClient(
    private val config: EntraConfig,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val clock: Clock = Clock.systemUTC(),
    private val refreshSkew: java.time.Duration = java.time.Duration.ofSeconds(60),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    @Volatile
    private var cached: CachedToken? = null

    /**
     * Returns a valid bearer token. Refreshes if the cached token is missing or
     * within [refreshSkew] of expiry.
     */
    suspend fun getAccessToken(): String {
        val now = clock.instant()
        cached?.let { if (it.expiresAt.isAfter(now.plus(refreshSkew))) return it.accessToken }

        return mutex.withLock {
            val nowInner = clock.instant()
            cached?.let { if (it.expiresAt.isAfter(nowInner.plus(refreshSkew))) return@withLock it.accessToken }
            fetchToken().also { cached = it }.accessToken
        }
    }

    /**
     * Force-invalidate the cached token (useful for tests and 401 retry paths).
     */
    fun invalidate() {
        cached = null
    }

    /**
     * Snapshot of the current cache for assertions in tests.
     */
    internal fun snapshot(): CachedToken? = cached

    private suspend fun fetchToken(): CachedToken = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("grant_type", "client_credentials")
            .add("client_id", config.clientId)
            .add("client_secret", config.clientSecret)
            .add("scope", config.scope)
            .build()

        val request = Request.Builder()
            .url(config.tokenEndpointUrl)
            .post(form)
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val parsed = runCatching { json.decodeFromString<EntraTokenError>(body) }.getOrNull()
                throw EntraException.AuthenticationFailed(
                    message = "AAD token endpoint returned ${response.code}: ${parsed?.error ?: "unknown"}" +
                        (parsed?.errorDescription?.let { " — $it" } ?: ""),
                    context = buildMap {
                        put("httpStatus", response.code)
                        parsed?.error?.let { put("error", it) }
                        parsed?.errorDescription?.let { put("error_description", it) }
                        parsed?.correlationId?.let { put("correlation_id", it) }
                    },
                )
            }
            val parsed = runCatching { json.decodeFromString<EntraTokenResponse>(body) }
                .getOrElse {
                    throw EntraException.AuthenticationFailed(
                        message = "Failed to parse AAD token response: ${it.message}",
                        cause = it,
                    )
                }
            CachedToken(
                accessToken = parsed.accessToken,
                expiresAt = clock.instant().plusSeconds(parsed.expiresInSeconds),
            )
        }
    }

    internal data class CachedToken(
        val accessToken: String,
        val expiresAt: Instant,
    )
}
