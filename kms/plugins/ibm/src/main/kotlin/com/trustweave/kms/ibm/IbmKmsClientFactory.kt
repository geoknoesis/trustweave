package com.trustweave.kms.ibm

import okhttp3.OkHttpClient
import java.net.URI

/**
 * Factory for creating IBM Key Protect HTTP clients.
 *
 * Handles authentication (IAM API key) and client configuration.
 *
 * Note: This uses OkHttp directly. For production, use IBM Cloud SDK when available.
 */
object IbmKmsClientFactory {
    /**
     * Creates an HTTP client for IBM Key Protect API.
     *
     * @param config IBM Key Protect configuration
     * @return Configured HTTP client
     */
    fun createClient(config: IbmKmsConfig): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer ${config.apiKey}")
                    .addHeader("Bluemix-Instance", config.instanceId)
                    .addHeader("Content-Type", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    /**
     * Gets the service URL for the given region.
     */
    fun getServiceUrl(region: String, override: String?): String {
        return override ?: when (region) {
            "us-south" -> "https://us-south.kms.cloud.ibm.com"
            "us-east" -> "https://us-east.kms.cloud.ibm.com"
            "eu-gb" -> "https://eu-gb.kms.cloud.ibm.com"
            "eu-de" -> "https://eu-de.kms.cloud.ibm.com"
            "jp-tok" -> "https://jp-tok.kms.cloud.ibm.com"
            "au-syd" -> "https://au-syd.kms.cloud.ibm.com"
            else -> "https://${region}.kms.cloud.ibm.com"
        }
    }
}

