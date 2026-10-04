package org.trustweave.kms.cyberark

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * Factory for creating CyberArk Conjur HTTP clients.
 *
 * Handles authentication (API key, username/password) and client configuration.
 */
object ConjurClientFactory {
    private val logger = LoggerFactory.getLogger(ConjurClientFactory::class.java)

    /** Conjur access tokens are valid for 8 minutes unless the server is configured otherwise. */
    private const val DEFAULT_TOKEN_LIFETIME_MILLIS = 8L * 60 * 1000

    /**
     * Creates an HTTP client for CyberArk Conjur API.
     *
     * With API-key authentication the Conjur access token is acquired once and reused until shortly
     * before it expires (and refreshed once if the server answers 401), instead of being fetched
     * for every request.
     *
     * @param config CyberArk Conjur configuration
     * @return Configured HTTP client with authentication
     */
    fun createClient(config: CyberArkKmsConfig): OkHttpClient = createClient(config, { getConjurToken(it) }, System::currentTimeMillis)

    internal fun createClient(
        config: CyberArkKmsConfig,
        fetchToken: (CyberArkKmsConfig) -> IssuedToken,
        nowMillis: () -> Long,
    ): OkHttpClient {
        val clientBuilder =
            OkHttpClient
                .Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)

        val tokens = TokenCache(DEFAULT_TOKEN_LIFETIME_MILLIS, nowMillis) { fetchToken(config) }

        // Add authentication interceptor
        clientBuilder.addInterceptor { chain ->
            fun authenticated(token: String?) =
                chain
                    .request()
                    .newBuilder()
                    .apply {
                        when {
                            token != null -> addHeader("Authorization", "Token token=\"$token\"")
                            config.username != null && config.password != null ->
                                addHeader("Authorization", Credentials.basic(config.username, config.password))
                        }
                    }.addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .build()

            if (config.apiKey != null) {
                // Conjur uses token-based authentication
                val response = chain.proceed(authenticated(tokens.get()))
                if (response.code == 401) {
                    // The cached token was rejected (revoked or expired early): refresh once.
                    response.close()
                    tokens.invalidate()
                    chain.proceed(authenticated(tokens.get()))
                } else {
                    response
                }
            } else {
                chain.proceed(authenticated(null))
            }
        }

        return clientBuilder.build()
    }

    /**
     * Gets a Conjur authentication token.
     *
     * Conjur authentication: POST /authn/{account}/{login}/authenticate
     *
     * For API key authentication: POST /authn/{account}/{hostId}/authenticate with API key in body
     * For username/password: POST /authn/{account}/{username}/authenticate with password in body
     *
     * The upstream response body is never included in an exception message: it can echo
     * credentials or internal detail. Only the HTTP status is reported (and logged at debug).
     */
    private fun getConjurToken(config: CyberArkKmsConfig): IssuedToken {
        val account = config.account ?: "default"
        val authEndpoint: String
        val authBody: String

        when {
            config.apiKey != null && config.hostId != null -> {
                // API key authentication
                authEndpoint = "${config.conjurUrl}/authn/$account/${config.hostId}/authenticate"
                authBody = config.apiKey
            }
            config.username != null && config.password != null -> {
                // Username/password authentication
                authEndpoint = "${config.conjurUrl}/authn/$account/${config.username}/authenticate"
                authBody = config.password
            }
            else -> {
                throw IllegalArgumentException("Either (apiKey and hostId) or (username and password) must be provided")
            }
        }

        val request =
            Request
                .Builder()
                .url(authEndpoint)
                .post(authBody.toRequestBody("text/plain".toMediaType()))
                .addHeader("Content-Type", "text/plain")
                .addHeader("Accept", "application/json")
                .build()

        val client =
            OkHttpClient
                .Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    logger.debug("Conjur authentication failed with HTTP {}", response.code)
                    throw IllegalStateException("Conjur authentication failed: HTTP ${response.code}")
                }
                // Conjur returns the token directly in the response body (base64-encoded)
                val token =
                    response.body
                        ?.string()
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: throw IllegalStateException("No token in Conjur authentication response")
                IssuedToken(token, lifetimeMillis = null)
            }
        } catch (e: java.io.IOException) {
            throw IllegalStateException("Failed to acquire Conjur token: ${e.javaClass.simpleName}", e)
        }
    }
}
