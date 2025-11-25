package com.trustweave.godiddy

import com.trustweave.core.exception.TrustWeaveException
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Base HTTP client wrapper for godiddy API calls.
 */
class GodiddyClient(
    internal val config: GodiddyConfig
) {
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        
        engine {
            requestTimeout = config.timeout
        }
    }

    /**
     * Makes a GET request and returns HttpResponse.
     */
    suspend fun getJson(path: String) = withContext(Dispatchers.IO) {
        try {
            httpClient.get("${config.baseUrl}$path") {
                config.apiKey?.let { apiKey ->
                    header("Authorization", "Bearer $apiKey")
                }
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }
        } catch (e: Exception) {
            throw com.trustweave.core.exception.TrustWeaveException.Unknown(
                message = "Failed to GET $path: ${e.message ?: "Unknown error"}",
                context = mapOf("path" to path, "method" to "GET"),
                cause = e
            )
        }
    }

    /**
     * Makes a POST request with a JSON body.
     */
    internal suspend inline fun <reified T> post(path: String, body: Any): T {
        val baseUrl = config.baseUrl
        val apiKey = config.apiKey
        return withContext(Dispatchers.IO) {
            try {
                httpClient.post("$baseUrl$path") {
                    contentType(ContentType.Application.Json)
                    apiKey?.let {
                        header("Authorization", "Bearer $it")
                    }
                    setBody(body)
                }.body()
            } catch (e: Exception) {
                throw com.trustweave.core.exception.TrustWeaveException.Unknown(
                    message = "Failed to POST $path: ${e.message ?: "Unknown error"}",
                    context = mapOf("path" to path, "method" to "POST"),
                    cause = e
                )
            }
        }
    }

    /**
     * Makes a POST request with JsonElement body and returns HttpResponse.
     */
    suspend fun postJson(path: String, body: kotlinx.serialization.json.JsonElement) = withContext(Dispatchers.IO) {
        try {
            httpClient.post("${config.baseUrl}$path") {
                contentType(ContentType.Application.Json)
                config.apiKey?.let { apiKey ->
                    header("Authorization", "Bearer $apiKey")
                }
                setBody(body)
            }
        } catch (e: Exception) {
            throw TrustWeaveException("Failed to POST $path: ${e.message}", e)
        }
    }

    /**
     * Closes the HTTP client.
     */
    fun close() {
        httpClient.close()
    }
}

