package com.trustweave.kms.fortanix

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Factory for creating Fortanix DSM HTTP clients.
 * 
 * Handles authentication (API key) and client configuration.
 */
object FortanixKmsClientFactory {
    /**
     * Creates an HTTP client for Fortanix DSM API.
     * 
     * @param config Fortanix DSM configuration
     * @return Configured HTTP client with authentication
     */
    fun createClient(config: FortanixKmsConfig): OkHttpClient {
        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
        
        // Add authentication interceptor
        clientBuilder.addInterceptor { chain ->
            val originalRequest = chain.request()
            val authenticatedRequest = originalRequest.newBuilder()
                .addHeader("Authorization", "Basic ${java.util.Base64.getEncoder().encodeToString(":${config.apiKey}".toByteArray())}")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build()
            chain.proceed(authenticatedRequest)
        }
        
        return clientBuilder.build()
    }
}

