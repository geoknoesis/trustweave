package com.geoknoesis.vericore.soldid

import com.geoknoesis.vericore.core.VeriCoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Client for interacting with Solana blockchain via RPC.
 * 
 * Implements Solana JSON-RPC methods:
 * - getAccountInfo: Get account data
 * - sendTransaction: Send transaction
 * - getTransaction: Get transaction details
 */
class SolanaClient(
    private val httpClient: OkHttpClient,
    private val config: SolDidConfig
) {

    /**
     * Gets account data from Solana.
     * 
     * @param address Solana address (base58 encoded)
     * @return Account data as bytes, or null if not found
     */
    suspend fun getAccountData(address: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val requestBody = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "getAccountInfo")
                put("params", buildJsonArray {
                    add(address)
                    add(buildJsonObject {
                        put("encoding", "base64")
                    })
                })
            }
            
            val request = Request.Builder()
                .url(config.rpcUrl)
                .post(
                    Json.encodeToString(JsonObject.serializer(), requestBody).toRequestBody(
                        "application/json".toMediaType()
                    )
                )
                .addHeader("Content-Type", "application/json")
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return@withContext null
            }
            
            val responseBody = response.body?.string() ?: return@withContext null
            val responseJson = Json.parseToJsonElement(responseBody).jsonObject
            
            val result = responseJson["result"]?.jsonObject
            val value = result?.get("value")?.jsonObject
            
            if (value == null) {
                // Account not found
                return@withContext null
            }
            
            // Decode base64 account data
            val data = value["data"]?.jsonArray?.get(0)?.jsonPrimitive?.content
                ?: return@withContext null
            
            // Decode base64 (simplified - real implementation needs proper base64 decoding)
            java.util.Base64.getDecoder().decode(data)
        } catch (e: Exception) {
            throw VeriCoreException(
                "Failed to get Solana account data: ${e.message}",
                e
            )
        }
    }

    /**
     * Sends a transaction to Solana.
     * 
     * @param transaction Serialized transaction (base64)
     * @return Transaction signature
     */
    suspend fun sendTransaction(transaction: String): String = withContext(Dispatchers.IO) {
        try {
            val requestBody = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "sendTransaction")
                put("params", buildJsonArray {
                    add(transaction)
                    add(buildJsonObject {
                        put("encoding", "base64")
                        put("skipPreflight", false)
                    })
                })
            }
            
            val request = Request.Builder()
                .url(config.rpcUrl)
                .post(
                    Json.encodeToString(JsonObject.serializer(), requestBody).toRequestBody(
                        "application/json".toMediaType()
                    )
                )
                .addHeader("Content-Type", "application/json")
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                throw VeriCoreException("Solana RPC error: HTTP ${response.code}: $errorBody")
            }
            
            val responseBody = response.body?.string() ?: throw VeriCoreException("Empty response")
            val responseJson = Json.parseToJsonElement(responseBody).jsonObject
            
            val result = responseJson["result"]?.jsonPrimitive?.content
                ?: throw VeriCoreException("No result in response: $responseBody")
            
            result
        } catch (e: VeriCoreException) {
            throw e
        } catch (e: Exception) {
            throw VeriCoreException(
                "Failed to send Solana transaction: ${e.message}",
                e
            )
        }
    }

    /**
     * Gets transaction details.
     * 
     * @param signature Transaction signature
     * @return Transaction details JSON
     */
    suspend fun getTransaction(signature: String): JsonObject? = withContext(Dispatchers.IO) {
        try {
            val requestBody = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "getTransaction")
                put("params", buildJsonArray {
                    add(signature)
                    add(buildJsonObject {
                        put("encoding", "jsonParsed")
                    })
                })
            }
            
            val request = Request.Builder()
                .url(config.rpcUrl)
                .post(
                    Json.encodeToString(JsonObject.serializer(), requestBody).toRequestBody(
                        "application/json".toMediaType()
                    )
                )
                .addHeader("Content-Type", "application/json")
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return@withContext null
            }
            
            val responseBody = response.body?.string() ?: return@withContext null
            val responseJson = Json.parseToJsonElement(responseBody).jsonObject
            
            responseJson["result"]?.jsonObject
        } catch (e: Exception) {
            null
        }
    }
}

