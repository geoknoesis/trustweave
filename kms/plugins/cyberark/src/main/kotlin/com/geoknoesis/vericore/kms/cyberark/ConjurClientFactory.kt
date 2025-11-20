package com.geoknoesis.vericore.kms.cyberark

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Factory for creating CyberArk Conjur HTTP clients.
 * 
 * Handles authentication (API key, username/password) and client configuration.
 */
object ConjurClientFactory {
    /**
     * Creates an HTTP client for CyberArk Conjur API.
     * 
     * @param config CyberArk Conjur configuration
     * @return Configured HTTP client with authentication
     */
    fun createClient(config: CyberArkKmsConfig): OkHttpClient {
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
                        config.apiKey != null -> {
                            // Conjur uses token-based authentication
                            // First authenticate to get token, then use token for subsequent requests
                            val token = getConjurToken(config)
                            addHeader("Authorization", "Token token=\"$token\"")
                        }
                        config.username != null && config.password != null -> {
                            val credentials = Credentials.basic(config.username, config.password)
                            addHeader("Authorization", credentials)
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
     * Gets Conjur authentication token.
     * 
     * Conjur authentication: POST /authn/{account}/{login}/authenticate
     * 
     * For API key authentication: POST /authn/{account}/{hostId}/authenticate with API key in body
     * For username/password: POST /authn/{account}/{username}/authenticate with password in body
     * 
     * Returns a token that should be cached and reused until expiration.
     */
    private fun getConjurToken(config: CyberArkKmsConfig): String {
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
        
        val request = Request.Builder()
            .url(authEndpoint)
            .post(authBody.toRequestBody("text/plain".toMediaType()))
            .addHeader("Content-Type", "text/plain")
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
                throw RuntimeException("Conjur authentication failed: ${response.code} - ${response.message}. Response: $responseBody")
            }
            
            // Conjur returns the token directly in the response body (base64-encoded)
            val token = responseBody?.trim() 
                ?: throw RuntimeException("No token in Conjur authentication response")
            
            // Note: In production, this token should be cached
            // Conjur tokens typically expire after a configurable period (default 8 minutes)
            token
        } catch (e: Exception) {
            throw RuntimeException("Failed to acquire Conjur token: ${e.message}", e)
        }
    }
}

