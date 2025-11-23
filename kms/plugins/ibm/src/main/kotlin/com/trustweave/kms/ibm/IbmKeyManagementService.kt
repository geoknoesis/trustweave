package com.trustweave.kms.ibm

import com.trustweave.core.TrustWeaveException
import com.trustweave.kms.Algorithm
import com.trustweave.kms.KeyHandle
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.KeyNotFoundException
import com.trustweave.kms.UnsupportedAlgorithmException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.Base64

/**
 * IBM Key Protect / Hyper Protect Crypto Services implementation of KeyManagementService.
 * 
 * Supports all IBM Key Protect-compatible algorithms:
 * - Ed25519
 * - secp256k1
 * - P-256, P-384, P-521 (EC:secp256r1/384r1/521r1)
 * - RSA-2048, RSA-3072, RSA-4096
 * 
 * **Example:**
 * ```kotlin
 * val config = IbmKmsConfig.builder()
 *     .apiKey("xxx")
 *     .instanceId("xxx")
 *     .region("us-south")
 *     .build()
 * val kms = IbmKeyManagementService(config)
 * val key = kms.generateKey(Algorithm.Ed25519)
 * ```
 */
class IbmKeyManagementService(
    private val config: IbmKmsConfig,
    private val httpClient: OkHttpClient = IbmKmsClientFactory.createClient(config)
) : KeyManagementService, AutoCloseable {
    
    private val baseUrl = IbmKmsClientFactory.getServiceUrl(config.region, config.serviceUrl)

    companion object {
        /**
         * Algorithms supported by IBM Key Protect.
         */
        val SUPPORTED_ALGORITHMS = setOf(
            Algorithm.Ed25519,
            Algorithm.Secp256k1,
            Algorithm.P256,
            Algorithm.P384,
            Algorithm.P521,
            Algorithm.RSA.RSA_2048,
            Algorithm.RSA.RSA_3072,
            Algorithm.RSA.RSA_4096
        )
    }

    override suspend fun getSupportedAlgorithms(): Set<Algorithm> = SUPPORTED_ALGORITHMS

    override suspend fun generateKey(
        algorithm: Algorithm,
        options: Map<String, Any?>
    ): KeyHandle = withContext(Dispatchers.IO) {
        if (!supportsAlgorithm(algorithm)) {
            throw UnsupportedAlgorithmException(
                "Algorithm '${algorithm.name}' is not supported by IBM Key Protect. " +
                "Supported: ${SUPPORTED_ALGORITHMS.joinToString(", ") { it.name }}"
            )
        }

        try {
            val keyType = AlgorithmMapping.toIbmKeyType(algorithm)
            val keyName = options["name"] as? String ?: generateKeyName(algorithm)
            
            // IBM Key Protect API: POST /api/v2/keys
            val requestBody = buildJsonObject {
                put("metadata", buildJsonObject {
                    put("collectionType", "application/vnd.ibm.kms.key+json")
                    put("collectionTotal", 1)
                })
                put("resources", buildJsonArray {
                    add(buildJsonObject {
                        put("type", keyType)
                        put("name", keyName)
                        (options["description"] as? String)?.let { put("description", it) }
                        (options["extractable"] as? Boolean)?.let { put("extractable", it) }
                    })
                })
            }
            
            val request = Request.Builder()
                .url("$baseUrl/api/v2/keys")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful) {
                throw TrustWeaveException(
                    "IBM Key Protect API error: ${response.code} - ${response.message}. " +
                    "Response: $responseBody"
                )
            }
            
            // Parse response to get key ID and CRN
            val jsonResponse = kotlinx.serialization.json.Json.parseToJsonElement(responseBody ?: "{}").jsonObject
            val resources = jsonResponse["resources"]?.jsonArray ?: throw TrustWeaveException("Invalid response from IBM Key Protect")
            val keyResource = resources.firstOrNull()?.jsonObject ?: throw TrustWeaveException("No key in response")
            
            val keyId = keyResource["id"]?.jsonPrimitive?.content
                ?: throw TrustWeaveException("Key ID not found in response")
            val keyCrn = keyResource["crn"]?.jsonPrimitive?.content ?: keyId
            
            // Get public key
            val publicKeyHandle = getPublicKey(keyCrn)
            
            KeyHandle(
                id = keyCrn,
                algorithm = algorithm.name,
                publicKeyJwk = publicKeyHandle.publicKeyJwk
            )
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException("Failed to generate key: ${e.message}", e)
        }
    }

    override suspend fun getPublicKey(keyId: String): KeyHandle = withContext(Dispatchers.IO) {
        try {
            val resolvedKeyId = AlgorithmMapping.resolveKeyId(keyId)
            
            // IBM Key Protect API: GET /api/v2/keys/{id}
            val request = Request.Builder()
                .url("$baseUrl/api/v2/keys/$resolvedKeyId")
                .get()
                .build()
            
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    throw KeyNotFoundException("Key not found: $keyId")
                }
                throw TrustWeaveException(
                    "IBM Key Protect API error: ${response.code} - ${response.message}. " +
                    "Response: $responseBody"
                )
            }
            
            // Parse response to get key metadata
            val jsonResponse = kotlinx.serialization.json.Json.parseToJsonElement(responseBody ?: "{}").jsonObject
            val resources = jsonResponse["resources"]?.jsonArray ?: throw TrustWeaveException("Invalid response from IBM Key Protect")
            val keyResource = resources.firstOrNull()?.jsonObject ?: throw TrustWeaveException("No key in response")
            
            val keyType = keyResource["type"]?.jsonPrimitive?.content
                ?: throw TrustWeaveException("Key type not found in response")
            val algorithm = AlgorithmMapping.fromIbmKeyType(keyType)
                ?: throw TrustWeaveException("Unknown key type: $keyType")
            
            // Get public key material
            // IBM Key Protect API: GET /api/v2/keys/{id}/publickey
            val publicKeyRequest = Request.Builder()
                .url("$baseUrl/api/v2/keys/$resolvedKeyId/publickey")
                .get()
                .build()
            
            val publicKeyResponse = httpClient.newCall(publicKeyRequest).execute()
            val publicKeyBody = publicKeyResponse.body?.string()
            
            if (!publicKeyResponse.isSuccessful) {
                throw TrustWeaveException(
                    "Failed to get public key: ${publicKeyResponse.code} - ${publicKeyResponse.message}. " +
                    "Response: $publicKeyBody"
                )
            }
            
            val publicKeyJson = kotlinx.serialization.json.Json.parseToJsonElement(publicKeyBody ?: "{}").jsonObject
            val publicKeyResources = publicKeyJson["resources"]?.jsonArray ?: throw TrustWeaveException("Invalid public key response")
            val publicKeyResource = publicKeyResources.firstOrNull()?.jsonObject ?: throw TrustWeaveException("No public key in response")
            
            val publicKeyBase64 = publicKeyResource["publicKey"]?.jsonPrimitive?.content
                ?: throw TrustWeaveException("Public key not found in response")
            val publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64)
            
            val publicKeyJwk = AlgorithmMapping.publicKeyToJwk(publicKeyBytes, algorithm)
            val keyCrn = keyResource["crn"]?.jsonPrimitive?.content ?: resolvedKeyId
            
            KeyHandle(
                id = keyCrn,
                algorithm = algorithm.name,
                publicKeyJwk = publicKeyJwk
            )
        } catch (e: KeyNotFoundException) {
            throw e
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException("Failed to get public key: ${e.message}", e)
        }
    }

    override suspend fun sign(
        keyId: String,
        data: ByteArray,
        algorithm: Algorithm?
    ): ByteArray = withContext(Dispatchers.IO) {
        try {
            val resolvedKeyId = AlgorithmMapping.resolveKeyId(keyId)
            
            // Determine signing algorithm
            val signingAlgorithm = algorithm ?: run {
                // Get key metadata to determine algorithm
                val keyHandle = getPublicKey(resolvedKeyId)
                Algorithm.parse(keyHandle.algorithm)
                    ?: throw TrustWeaveException("Cannot determine signing algorithm for key: $keyId")
            }
            
            val ibmSigningAlgorithm = AlgorithmMapping.toIbmSigningAlgorithm(signingAlgorithm)
            val dataBase64 = Base64.getEncoder().encodeToString(data)
            
            // IBM Key Protect API: POST /api/v2/keys/{id}/sign
            val requestBody = buildJsonObject {
                put("metadata", buildJsonObject {
                    put("collectionType", "application/vnd.ibm.kms.sign+json")
                    put("collectionTotal", 1)
                })
                put("resources", buildJsonArray {
                    add(buildJsonObject {
                        put("message", dataBase64)
                        put("messageType", "application/vnd.ibm.kms.message+json")
                    })
                })
            }
            
            val request = Request.Builder()
                .url("$baseUrl/api/v2/keys/$resolvedKeyId/sign")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    throw KeyNotFoundException("Key not found: $keyId")
                }
                throw TrustWeaveException(
                    "IBM Key Protect API error: ${response.code} - ${response.message}. " +
                    "Response: $responseBody"
                )
            }
            
            // Parse response to get signature
            val jsonResponse = kotlinx.serialization.json.Json.parseToJsonElement(responseBody ?: "{}").jsonObject
            val resources = jsonResponse["resources"]?.jsonArray ?: throw TrustWeaveException("Invalid response from IBM Key Protect")
            val signResource = resources.firstOrNull()?.jsonObject ?: throw TrustWeaveException("No signature in response")
            
            val signatureBase64 = signResource["signature"]?.jsonPrimitive?.content
                ?: throw TrustWeaveException("Signature not found in response")
            
            Base64.getDecoder().decode(signatureBase64)
        } catch (e: KeyNotFoundException) {
            throw e
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException("Failed to sign data: ${e.message}", e)
        }
    }

    override suspend fun deleteKey(keyId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolvedKeyId = AlgorithmMapping.resolveKeyId(keyId)
            
            // IBM Key Protect API: DELETE /api/v2/keys/{id}
            // Note: IBM Key Protect requires setting deletion date first, then deleting
            // For immediate deletion, we use the DELETE endpoint with force parameter
            
            // First, try to schedule immediate deletion
            val requestBody = buildJsonObject {
                put("metadata", buildJsonObject {
                    put("collectionType", "application/vnd.ibm.kms.key+json")
                    put("collectionTotal", 1)
                })
                put("resources", buildJsonArray {
                    add(buildJsonObject {
                        put("deletionDate", "0") // Immediate deletion
                    })
                })
            }
            
            val scheduleRequest = Request.Builder()
                .url("$baseUrl/api/v2/keys/$resolvedKeyId")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val scheduleResponse = httpClient.newCall(scheduleRequest).execute()
            
            if (scheduleResponse.isSuccessful) {
                // Now delete the key
                val deleteRequest = Request.Builder()
                    .url("$baseUrl/api/v2/keys/$resolvedKeyId")
                    .delete()
                    .build()
                
                val deleteResponse = httpClient.newCall(deleteRequest).execute()
                deleteResponse.isSuccessful
            } else {
                // If scheduling fails, try direct deletion
                val deleteRequest = Request.Builder()
                    .url("$baseUrl/api/v2/keys/$resolvedKeyId")
                    .delete()
                    .build()
                
                val deleteResponse = httpClient.newCall(deleteRequest).execute()
                deleteResponse.isSuccessful
            }
        } catch (e: Exception) {
            // Return false on error rather than throwing
            false
        }
    }

    /**
     * Generates a unique key name for a given algorithm.
     */
    private fun generateKeyName(algorithm: Algorithm): String {
        val prefix = when (algorithm) {
            is Algorithm.Ed25519 -> "ed25519"
            is Algorithm.Secp256k1 -> "secp256k1"
            is Algorithm.P256 -> "p256"
            is Algorithm.P384 -> "p384"
            is Algorithm.P521 -> "p521"
            is Algorithm.RSA -> "rsa${algorithm.keySize}"
            else -> "key"
        }
        return "$prefix-${java.util.UUID.randomUUID().toString().take(8)}"
    }


    override fun close() {
        // IBM Key Protect client cleanup if needed
    }
}

