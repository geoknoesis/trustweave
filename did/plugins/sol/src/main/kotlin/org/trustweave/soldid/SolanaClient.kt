package org.trustweave.soldid

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.trustweave.core.exception.TrustWeaveException

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
    private val config: SolDidConfig,
) {
    /**
     * Gets account data from Solana.
     *
     * @param address Solana address (base58 encoded)
     * @return Account data as bytes, or null if the account does not exist
     * @throws TrustWeaveException if the RPC call fails (HTTP error, RPC error, malformed response)
     */
    suspend fun getAccountData(address: String): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val requestBody =
                    buildJsonObject {
                        put("jsonrpc", "2.0")
                        put("id", 1)
                        put("method", "getAccountInfo")
                        put(
                            "params",
                            buildJsonArray {
                                add(address)
                                add(
                                    buildJsonObject {
                                        put("encoding", "base64")
                                    },
                                )
                            },
                        )
                    }

                val request =
                    Request
                        .Builder()
                        .url(config.rpcUrl)
                        .post(
                            Json.encodeToString(JsonObject.serializer(), requestBody).toRequestBody(
                                "application/json".toMediaType(),
                            ),
                        ).addHeader("Content-Type", "application/json")
                        .build()

                val responseBody =
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw TrustWeaveException.Unknown(
                                code = "RPC_ERROR",
                                message = "Solana RPC getAccountInfo failed: HTTP ${response.code}",
                            )
                        }
                        response.body?.string()
                    } ?: throw TrustWeaveException.Unknown(code = "EMPTY_RESPONSE", message = "Empty Solana RPC response")
                val responseJson = Json.parseToJsonElement(responseBody).jsonObject
                responseJson["error"]?.let { error ->
                    throw TrustWeaveException.Unknown(code = "RPC_ERROR", message = "Solana RPC error: $error")
                }

                val result = responseJson["result"]?.jsonObject
                val value = result?.get("value")?.takeUnless { it is JsonNull }?.jsonObject

                if (value == null) {
                    // Account not found
                    return@withContext null
                }

                // Decode base64 account data
                val data =
                    value["data"]
                        ?.jsonArray
                        ?.get(0)
                        ?.jsonPrimitive
                        ?.content
                        ?: return@withContext null

                // The RPC returns the account data base64-encoded (we asked for "encoding": "base64");
                // this decodes it to the raw account bytes.
                java.util.Base64
                    .getDecoder()
                    .decode(data)
            } catch (e: TrustWeaveException) {
                throw e
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                throw TrustWeaveException.Unknown(
                    code = "ACCOUNT_DATA_FAILED",
                    message = "Failed to get Solana account data: ${e.message}",
                    cause = e,
                )
            }
        }

    /**
     * Sends a transaction to Solana.
     *
     * @param transaction Serialized transaction (base64)
     * @return Transaction signature
     */
    suspend fun sendTransaction(transaction: String): String =
        withContext(Dispatchers.IO) {
            try {
                val requestBody =
                    buildJsonObject {
                        put("jsonrpc", "2.0")
                        put("id", 1)
                        put("method", "sendTransaction")
                        put(
                            "params",
                            buildJsonArray {
                                add(transaction)
                                add(
                                    buildJsonObject {
                                        put("encoding", "base64")
                                        put("skipPreflight", false)
                                    },
                                )
                            },
                        )
                    }

                val request =
                    Request
                        .Builder()
                        .url(config.rpcUrl)
                        .post(
                            Json.encodeToString(JsonObject.serializer(), requestBody).toRequestBody(
                                "application/json".toMediaType(),
                            ),
                        ).addHeader("Content-Type", "application/json")
                        .build()

                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    throw TrustWeaveException.Unknown(
                        code = "RPC_ERROR",
                        message = "Solana RPC error: HTTP ${response.code}: $errorBody",
                    )
                }

                val responseBody =
                    response.body?.string() ?: throw TrustWeaveException.Unknown(
                        code = "EMPTY_RESPONSE",
                        message = "Empty response",
                    )
                val responseJson = Json.parseToJsonElement(responseBody).jsonObject

                val result =
                    responseJson["result"]?.jsonPrimitive?.content
                        ?: throw TrustWeaveException.Unknown(
                            code = "NO_RESULT",
                            message = "No result in response: $responseBody",
                        )

                result
            } catch (e: TrustWeaveException) {
                throw e
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                throw TrustWeaveException.Unknown(
                    code = "TRANSACTION_FAILED",
                    message = "Failed to send Solana transaction: ${e.message}",
                    cause = e,
                )
            }
        }

    /**
     * Gets transaction details.
     *
     * @param signature Transaction signature
     * @return Transaction details JSON
     */
    suspend fun getTransaction(signature: String): JsonObject? =
        withContext(Dispatchers.IO) {
            try {
                val requestBody =
                    buildJsonObject {
                        put("jsonrpc", "2.0")
                        put("id", 1)
                        put("method", "getTransaction")
                        put(
                            "params",
                            buildJsonArray {
                                add(signature)
                                add(
                                    buildJsonObject {
                                        put("encoding", "jsonParsed")
                                    },
                                )
                            },
                        )
                    }

                val request =
                    Request
                        .Builder()
                        .url(config.rpcUrl)
                        .post(
                            Json.encodeToString(JsonObject.serializer(), requestBody).toRequestBody(
                                "application/json".toMediaType(),
                            ),
                        ).addHeader("Content-Type", "application/json")
                        .build()

                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext null
                }

                val responseBody = response.body?.string() ?: return@withContext null
                val responseJson = Json.parseToJsonElement(responseBody).jsonObject

                responseJson["result"]?.jsonObject
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                null
            }
        }
}
