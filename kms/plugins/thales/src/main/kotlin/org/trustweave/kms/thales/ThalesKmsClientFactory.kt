package org.trustweave.kms.thales

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * Factory for creating Thales CipherTrust Manager HTTP clients.
 *
 * Handles authentication (API key, username/password, or OAuth2) and client configuration.
 */
object ThalesKmsClientFactory {
    private val logger = LoggerFactory.getLogger(ThalesKmsClientFactory::class.java)

    /** Used when the token response carries no `expires_in`. */
    private const val DEFAULT_TOKEN_LIFETIME_MILLIS = 5L * 60 * 1000

    /**
     * Creates an HTTP client for Thales CipherTrust Manager API.
     *
     * With the OAuth2 client-credentials flow the access token is acquired once and reused until
     * shortly before it expires (and refreshed once if the server answers 401), instead of being
     * fetched for every request.
     *
     * @param config Thales CipherTrust configuration
     * @return Configured HTTP client with authentication
     */
    fun createClient(config: ThalesKmsConfig): OkHttpClient = createClient(config, { getOAuth2Token(it) }, System::currentTimeMillis)

    internal fun createClient(
        config: ThalesKmsConfig,
        fetchToken: (ThalesKmsConfig) -> IssuedToken,
        nowMillis: () -> Long,
    ): OkHttpClient {
        val clientBuilder =
            OkHttpClient
                .Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)

        val tokens = TokenCache(DEFAULT_TOKEN_LIFETIME_MILLIS, nowMillis) { fetchToken(config) }
        val usesOAuth =
            config.accessToken == null &&
                config.apiKey == null &&
                !(config.username != null && config.password != null) &&
                config.clientId != null &&
                config.clientSecret != null

        // Add authentication interceptor
        clientBuilder.addInterceptor { chain ->
            fun authenticated(oauthToken: String?) =
                chain
                    .request()
                    .newBuilder()
                    .apply {
                        when {
                            config.accessToken != null -> addHeader("Authorization", "Bearer ${config.accessToken}")
                            config.apiKey != null -> addHeader("Authorization", "Bearer ${config.apiKey}")
                            config.username != null && config.password != null ->
                                addHeader("Authorization", Credentials.basic(config.username, config.password))
                            oauthToken != null -> addHeader("Authorization", "Bearer $oauthToken")
                        }
                    }.addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .build()

            if (usesOAuth) {
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
     * Gets OAuth2 access token using client credentials flow.
     *
     * Thales CipherTrust Manager typically uses OAuth2 client credentials flow:
     * POST /oauth/token with grant_type=client_credentials
     *
     * The upstream response body is never included in an exception message: it can echo
     * credentials or internal detail. Only the HTTP status is reported (and logged at debug).
     */
    private fun getOAuth2Token(config: ThalesKmsConfig): IssuedToken {
        if (config.clientId == null || config.clientSecret == null) {
            throw IllegalArgumentException("clientId and clientSecret are required for OAuth2 authentication")
        }

        val tokenEndpoint = "${config.baseUrl}/oauth/token"

        val requestBody =
            buildJsonObject {
                put("grant_type", "client_credentials")
                put("client_id", config.clientId)
                put("client_secret", config.clientSecret)
                if (config.scope != null) {
                    put("scope", config.scope)
                }
            }

        val request =
            Request
                .Builder()
                .url(tokenEndpoint)
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")
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
                    logger.debug("OAuth2 token acquisition failed with HTTP {}", response.code)
                    throw IllegalStateException("OAuth2 token acquisition failed: HTTP ${response.code}")
                }
                val jsonResponse = Json.parseToJsonElement(response.body?.string() ?: "{}").jsonObject
                val accessToken =
                    jsonResponse["access_token"]?.jsonPrimitive?.content
                        ?: throw IllegalStateException("No access_token in OAuth2 response")
                // expires_in is in seconds
                val lifetimeMillis = jsonResponse["expires_in"]?.jsonPrimitive?.runCatching { long * 1000 }?.getOrNull()
                IssuedToken(accessToken, lifetimeMillis)
            }
        } catch (e: java.io.IOException) {
            throw IllegalStateException("Failed to acquire OAuth2 token: ${e.javaClass.simpleName}", e)
        } catch (e: kotlinx.serialization.SerializationException) {
            throw IllegalStateException("OAuth2 token response was not valid JSON", e)
        }
    }
}
