package org.trustweave.kms.thales

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.*

/**
 * Factory for creating Thales CipherTrust Manager HTTP clients.
 *
 * Handles authentication (API key, username/password, or OAuth2) and client configuration.
 */
object ThalesKmsClientFactory {
    /**
     * Creates an HTTP client for Thales CipherTrust Manager API.
     *
     * @param config Thales CipherTrust configuration
     * @return Configured HTTP client with authentication
     */
    fun createClient(config: ThalesKmsConfig): OkHttpClient {
        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)

        // Add authentication interceptor
        clientBuilder.addInterceptor { chain ->
            val originalRequest = chain.request()
            val authenticatedRequest = originalRequest.newBuilder()
                .apply {
                    when {
                        config.accessToken != null -> {
                            addHeader("Authorization", "Bearer ${config.accessToken}")
                        }
                        config.apiKey != null -> {
                            addHeader("Authorization", "Bearer ${config.apiKey}")
                        }
                        config.username != null && config.password != null -> {
                            val credentials = Credentials.basic(config.username, config.password)
                            addHeader("Authorization", credentials)
                        }
                        config.clientId != null && config.clientSecret != null -> {
                            // OAuth2 client credentials flow
                            val token = getOAuth2Token(config)
                            addHeader("Authorization", "Bearer $token")
                        }
                    }
                }
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build()
            chain.proceed(authenticatedRequest)
        }

        return clientBuilder.build()
    }

    /**
     * Gets OAuth2 access token using client credentials flow.
     *
     * Thales CipherTrust Manager typically uses OAuth2 client credentials flow:
     * POST /oauth/token with grant_type=client_credentials
     */
    private fun getOAuth2Token(config: ThalesKmsConfig): String {
        if (config.clientId == null || config.clientSecret == null) {
            throw IllegalArgumentException("clientId and clientSecret are required for OAuth2 authentication")
        }

        val tokenEndpoint = "${config.baseUrl}/oauth/token"

        val requestBody = buildJsonObject {
            put("grant_type", "client_credentials")
            put("client_id", config.clientId)
            put("client_secret", config.clientSecret)
            if (config.scope != null) {
                put("scope", config.scope)
            }
        }

        val request = Request.Builder()
            .url(tokenEndpoint)
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build()

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                throw RuntimeException("OAuth2 token acquisition failed: ${response.code} - ${response.message}. Response: $responseBody")
            }

            val jsonResponse = Json.parseToJsonElement(responseBody ?: "{}").jsonObject
            val accessToken = jsonResponse["access_token"]?.jsonPrimitive?.content
                ?: throw RuntimeException("No access_token in OAuth2 response")

            // Note: In production, this token should be cached with expiration time
            // The token expiration is typically in the "expires_in" field (seconds)
            accessToken
        } catch (e: Exception) {
            throw RuntimeException("Failed to acquire OAuth2 token: ${e.message}", e)
        }
    }
}

