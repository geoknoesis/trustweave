package org.trustweave.soldid

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
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
                                        put("commitment", config.commitment)
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
                        boundedBody(response)
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

                // A DID document lives in an account owned by the configured DID registry program. When a
                // program is configured, an account owned by anyone else is not a DID account.
                val expectedOwner = config.programId
                if (expectedOwner != null) {
                    val owner = (value["owner"] as? JsonPrimitive)?.contentOrNull
                    if (owner != expectedOwner) {
                        throw TrustWeaveException.Unknown(
                            code = "ACCOUNT_OWNER_MISMATCH",
                            message = "Solana account $address is not owned by the configured DID program",
                        )
                    }
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
                // base64 inflates by 4/3: refuse before decoding rather than after.
                if (data.length > MAX_ACCOUNT_DATA_BYTES / 3 * 4 + 4) {
                    throw TrustWeaveException.Unknown(
                        code = "ACCOUNT_DATA_TOO_LARGE",
                        message = "Solana account $address holds more than $MAX_ACCOUNT_DATA_BYTES bytes",
                    )
                }
                val decoded =
                    java.util.Base64
                        .getDecoder()
                        .decode(data)
                if (decoded.size > MAX_ACCOUNT_DATA_BYTES) {
                    throw TrustWeaveException.Unknown(
                        code = "ACCOUNT_DATA_TOO_LARGE",
                        message = "Solana account $address holds more than $MAX_ACCOUNT_DATA_BYTES bytes",
                    )
                }
                decoded
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

                val responseBody =
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw TrustWeaveException.Unknown(
                                code = "RPC_ERROR",
                                message = "Solana RPC error: HTTP ${response.code}: ${boundedBody(response)}",
                            )
                        }
                        boundedBody(response)
                    }
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

                val responseBody =
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            return@withContext null
                        }
                        boundedBody(response)
                    }
                val responseJson = Json.parseToJsonElement(responseBody).jsonObject

                responseJson["result"]?.jsonObject
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                null
            }
        }

    /** Reads at most [MAX_RESPONSE_BYTES] of the body; a larger answer is an error, not a truncation. */
    private fun boundedBody(response: okhttp3.Response): String {
        val body = response.body ?: throw TrustWeaveException.Unknown(code = "EMPTY_RESPONSE", message = "Empty Solana RPC response")
        if (body.contentLength() > MAX_RESPONSE_BYTES) {
            throw TrustWeaveException.Unknown(code = "RESPONSE_TOO_LARGE", message = "Solana RPC response is too large")
        }
        val bytes = response.peekBody(MAX_RESPONSE_BYTES + 1).bytes()
        if (bytes.size > MAX_RESPONSE_BYTES) {
            throw TrustWeaveException.Unknown(code = "RESPONSE_TOO_LARGE", message = "Solana RPC response is too large")
        }
        return String(bytes, Charsets.UTF_8)
    }
}

/** A DID document is a few KiB; this bounds what a hostile RPC can make us hold in memory. */
private const val MAX_ACCOUNT_DATA_BYTES = 256 * 1024
private const val MAX_RESPONSE_BYTES = 1024 * 1024L
