package org.trustweave.kms.ibm

import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyHandle
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.DeleteKeyResult
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.GetPublicKeyResult
import org.trustweave.kms.results.SignResult
import org.trustweave.kms.KmsOptionKeys
import org.trustweave.kms.util.KmsErrorHandler
import org.trustweave.kms.util.KmsInputValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.slf4j.LoggerFactory
import java.util.Base64
import java.util.UUID

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
 * val result = kms.generateKey(Algorithm.Ed25519)
 * when (result) {
 *     is GenerateKeyResult.Success -> println("Key created: ${result.keyHandle.id}")
 *     is GenerateKeyResult.Failure -> println("Error: ${result.reason}")
 * }
 * ```
 */
class IbmKeyManagementService(
    private val config: IbmKmsConfig,
    private val httpClient: OkHttpClient = IbmKmsClientFactory.createClient(config)
) : KeyManagementService, AutoCloseable {

    private val baseUrl = IbmKmsClientFactory.getServiceUrl(config.region, config.serviceUrl)
    private val logger = LoggerFactory.getLogger(IbmKeyManagementService::class.java)

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
    ): GenerateKeyResult = withContext(Dispatchers.IO) {
        if (!supportsAlgorithm(algorithm)) {
            return@withContext GenerateKeyResult.Failure.UnsupportedAlgorithm(
                algorithm = algorithm,
                supportedAlgorithms = SUPPORTED_ALGORITHMS
            )
        }

        // Validate key name if provided
        val keyName = (options[KmsOptionKeys.NAME] as? String)?.takeIf { it.isNotBlank() }
        keyName?.let {
            val validationError = KmsInputValidator.validateKeyId(it)
            if (validationError != null) {
                logger.warn("Invalid key name provided: keyName={}, error={}", it, validationError)
                return@withContext GenerateKeyResult.Failure.InvalidOptions(
                    algorithm = algorithm,
                    reason = "Invalid key name: $validationError",
                    invalidOptions = options
                )
            }
        }

        try {
            val keyType = AlgorithmMapping.toIbmKeyType(algorithm)
            val finalKeyName = keyName ?: generateKeyName(algorithm)

            // IBM Key Protect API: POST /api/v2/keys
            val requestBody = buildJsonObject {
                put("metadata", buildJsonObject {
                    put("collectionType", "application/vnd.ibm.kms.key+json")
                    put("collectionTotal", 1)
                })
                put("resources", buildJsonArray {
                    add(buildJsonObject {
                        put("type", keyType)
                        put("name", finalKeyName)
                        (options[KmsOptionKeys.DESCRIPTION] as? String)?.let { put("description", it) }
                        (options[KmsOptionKeys.EXTRACTABLE] as? Boolean)?.let { put("extractable", it) }
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
                val context = KmsErrorHandler.createErrorContext(
                    algorithm = algorithm,
                    operation = "generateKey",
                    additional = mapOf(
                        "statusCode" to response.code,
                        "region" to config.region,
                        "instanceId" to config.instanceId
                    )
                )
                
                when (response.code) {
                    400, 403 -> {
                        logger.error("Invalid request to IBM Key Protect", context)
                        GenerateKeyResult.Failure.InvalidOptions(
                            algorithm = algorithm,
                            reason = "IBM Key Protect API error: ${response.code} - ${response.message ?: "Unknown error"}. Response: $responseBody",
                            invalidOptions = options
                        )
                    }
                    401 -> {
                        logger.error("Authentication failed with IBM Key Protect", context)
                        GenerateKeyResult.Failure.Error(
                            algorithm = algorithm,
                            reason = "Authentication failed. Check API key: ${response.message ?: "Unknown error"}",
                            cause = null
                        )
                    }
                    else -> {
                        logger.error("Failed to generate key in IBM Key Protect", context)
                        GenerateKeyResult.Failure.Error(
                            algorithm = algorithm,
                            reason = "IBM Key Protect API error: ${response.code} - ${response.message ?: "Unknown error"}. Response: $responseBody",
                            cause = null
                        )
                    }
                }
            } else {
                // Parse response to get key ID and CRN
                val jsonResponse = kotlinx.serialization.json.Json.parseToJsonElement(responseBody ?: "{}").jsonObject
                val resources = jsonResponse["resources"]?.jsonArray ?: return@withContext GenerateKeyResult.Failure.Error(
                    algorithm = algorithm,
                    reason = "Invalid response from IBM Key Protect: missing resources"
                )
                val keyResource = resources.firstOrNull()?.jsonObject ?: return@withContext GenerateKeyResult.Failure.Error(
                    algorithm = algorithm,
                    reason = "Invalid response from IBM Key Protect: no key in response"
                )

                val keyId = keyResource["id"]?.jsonPrimitive?.content
                    ?: return@withContext GenerateKeyResult.Failure.Error(
                        algorithm = algorithm,
                        reason = "Key ID not found in response"
                    )
                val keyCrn = keyResource["crn"]?.jsonPrimitive?.content ?: keyId

                // Get public key
                val publicKeyResult = getPublicKey(KeyId(keyCrn))
                when (publicKeyResult) {
                    is GetPublicKeyResult.Success -> {
                        logger.info("Generated key in IBM Key Protect: keyId={}, algorithm={}", keyCrn, algorithm.name)
                        GenerateKeyResult.Success(
                            KeyHandle(
                                id = KeyId(keyCrn),
                                algorithm = algorithm.name,
                                publicKeyJwk = publicKeyResult.keyHandle.publicKeyJwk
                            )
                        )
                    }
                    is GetPublicKeyResult.Failure.KeyNotFound -> {
                        GenerateKeyResult.Failure.Error(
                            algorithm = algorithm,
                            reason = "Failed to retrieve public key after key creation: Key not found",
                            cause = null
                        )
                    }
                    is GetPublicKeyResult.Failure.Error -> {
                        GenerateKeyResult.Failure.Error(
                            algorithm = algorithm,
                            reason = "Failed to retrieve public key after key creation: ${publicKeyResult.reason}",
                            cause = publicKeyResult.cause
                        )
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Unexpected error generating key in IBM Key Protect", mapOf(
                "algorithm" to algorithm.name,
                "region" to config.region
            ), e)
            GenerateKeyResult.Failure.Error(
                algorithm = algorithm,
                reason = "Failed to generate key: ${e.message ?: "Unknown error"}",
                cause = e
            )
        }
    }

    override suspend fun getPublicKey(keyId: KeyId): GetPublicKeyResult = withContext(Dispatchers.IO) {
        try {
            val resolvedKeyId = AlgorithmMapping.resolveKeyId(keyId.value)

            // IBM Key Protect API: GET /api/v2/keys/{id}
            val request = Request.Builder()
                .url("$baseUrl/api/v2/keys/$resolvedKeyId")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                if (response.code == 404) {
                    logger.debug("Key not found in IBM Key Protect: keyId={}", keyId.value)
                    return@withContext GetPublicKeyResult.Failure.KeyNotFound(keyId = keyId)
                }
                logger.error("Failed to get key from IBM Key Protect", mapOf(
                    "keyId" to keyId.value,
                    "statusCode" to response.code,
                    "region" to config.region
                ))
                return@withContext GetPublicKeyResult.Failure.Error(
                    keyId = keyId,
                    reason = "IBM Key Protect API error: ${response.code} - ${response.message ?: "Unknown error"}. Response: $responseBody"
                )
            }

            // Parse response to get key metadata
            val jsonResponse = kotlinx.serialization.json.Json.parseToJsonElement(responseBody ?: "{}").jsonObject
            val resources = jsonResponse["resources"]?.jsonArray ?: return@withContext GetPublicKeyResult.Failure.Error(
                keyId = keyId,
                reason = "Invalid response from IBM Key Protect: missing resources"
            )
            val keyResource = resources.firstOrNull()?.jsonObject ?: return@withContext GetPublicKeyResult.Failure.Error(
                keyId = keyId,
                reason = "Invalid response from IBM Key Protect: no key in response"
            )

            val keyType = keyResource["type"]?.jsonPrimitive?.content
                ?: return@withContext GetPublicKeyResult.Failure.Error(
                    keyId = keyId,
                    reason = "Key type not found in response"
                )
            val algorithm = AlgorithmMapping.fromIbmKeyType(keyType)
                ?: return@withContext GetPublicKeyResult.Failure.Error(
                    keyId = keyId,
                    reason = "Unknown key type: $keyType"
                )

            // Get public key material
            // IBM Key Protect API: GET /api/v2/keys/{id}/publickey
            val publicKeyRequest = Request.Builder()
                .url("$baseUrl/api/v2/keys/$resolvedKeyId/publickey")
                .get()
                .build()

            val publicKeyResponse = httpClient.newCall(publicKeyRequest).execute()
            val publicKeyBody = publicKeyResponse.body?.string()

            if (!publicKeyResponse.isSuccessful) {
                logger.error("Failed to get public key material from IBM Key Protect", mapOf(
                    "keyId" to keyId.value,
                    "statusCode" to publicKeyResponse.code,
                    "region" to config.region
                ))
                return@withContext GetPublicKeyResult.Failure.Error(
                    keyId = keyId,
                    reason = "Failed to get public key: ${publicKeyResponse.code} - ${publicKeyResponse.message ?: "Unknown error"}. Response: $publicKeyBody"
                )
            }

            val publicKeyJson = kotlinx.serialization.json.Json.parseToJsonElement(publicKeyBody ?: "{}").jsonObject
            val publicKeyResources = publicKeyJson["resources"]?.jsonArray ?: return@withContext GetPublicKeyResult.Failure.Error(
                keyId = keyId,
                reason = "Invalid public key response from IBM Key Protect"
            )
            val publicKeyResource = publicKeyResources.firstOrNull()?.jsonObject ?: return@withContext GetPublicKeyResult.Failure.Error(
                keyId = keyId,
                reason = "No public key in response from IBM Key Protect"
            )

            val publicKeyBase64 = publicKeyResource["publicKey"]?.jsonPrimitive?.content
                ?: return@withContext GetPublicKeyResult.Failure.Error(
                    keyId = keyId,
                    reason = "Public key not found in response"
                )
            val publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64)

            val publicKeyJwk = AlgorithmMapping.publicKeyToJwk(publicKeyBytes, algorithm)
            val keyCrn = keyResource["crn"]?.jsonPrimitive?.content ?: resolvedKeyId

            GetPublicKeyResult.Success(
                KeyHandle(
                    id = KeyId(keyCrn),
                    algorithm = algorithm.name,
                    publicKeyJwk = publicKeyJwk
                )
            )
        } catch (e: Exception) {
            logger.error("Unexpected error getting public key from IBM Key Protect", mapOf(
                "keyId" to keyId.value,
                "region" to config.region
            ), e)
            GetPublicKeyResult.Failure.Error(
                keyId = keyId,
                reason = "Failed to get public key: ${e.message ?: "Unknown error"}",
                cause = e
            )
        }
    }

    override suspend fun sign(
        keyId: KeyId,
        data: ByteArray,
        algorithm: Algorithm?
    ): SignResult = withContext(Dispatchers.IO) {
        // Validate input data
        val dataValidationError = KmsInputValidator.validateSignData(data)
        if (dataValidationError != null) {
            logger.warn("Invalid data for signing: keyId={}, error={}", keyId.value, dataValidationError)
            return@withContext SignResult.Failure.Error(
                keyId = keyId,
                reason = dataValidationError
            )
        }

        try {
            val resolvedKeyId = AlgorithmMapping.resolveKeyId(keyId.value)

            // Determine signing algorithm
            val signingAlgorithm = algorithm ?: run {
                // Get key metadata to determine algorithm
                val keyResult = getPublicKey(keyId)
                when (keyResult) {
                    is GetPublicKeyResult.Success -> {
                        Algorithm.parse(keyResult.keyHandle.algorithm)
                            ?: return@withContext SignResult.Failure.Error(
                                keyId = keyId,
                                reason = "Cannot determine signing algorithm for key: ${keyId.value}"
                            )
                    }
                    is GetPublicKeyResult.Failure.KeyNotFound -> {
                        return@withContext SignResult.Failure.KeyNotFound(keyId = keyId)
                    }
                    is GetPublicKeyResult.Failure.Error -> {
                        return@withContext SignResult.Failure.Error(
                            keyId = keyId,
                            reason = "Failed to get key metadata: ${keyResult.reason}"
                        )
                    }
                }
                null // This should never be reached due to return@withContext above
            }

            // Check algorithm compatibility if algorithm was provided
            if (algorithm != null) {
                val keyResult = getPublicKey(keyId)
                when (keyResult) {
                    is GetPublicKeyResult.Success -> {
                        val keyAlgorithm = Algorithm.parse(keyResult.keyHandle.algorithm)
                        if (keyAlgorithm != null && !algorithm.isCompatibleWith(keyAlgorithm)) {
                            logger.warn("Algorithm incompatibility: keyId={}, requestedAlgorithm={}, keyAlgorithm={}", 
                                keyId.value, algorithm.name, keyAlgorithm.name)
                            return@withContext SignResult.Failure.UnsupportedAlgorithm(
                                keyId = keyId,
                                requestedAlgorithm = algorithm,
                                keyAlgorithm = keyAlgorithm,
                                reason = "Algorithm '${algorithm.name}' is not compatible with key algorithm '${keyAlgorithm.name}'"
                            )
                        }
                    }
                    is GetPublicKeyResult.Failure.KeyNotFound -> {
                        return@withContext SignResult.Failure.KeyNotFound(keyId = keyId)
                    }
                    is GetPublicKeyResult.Failure.Error -> {
                        return@withContext SignResult.Failure.Error(
                            keyId = keyId,
                            reason = "Failed to get key metadata: ${keyResult.reason}"
                        )
                    }
                }
            }

            val ibmSigningAlgorithm = AlgorithmMapping.toIbmSigningAlgorithm(signingAlgorithm ?: return@withContext SignResult.Failure.Error(
                keyId = keyId,
                reason = "Cannot determine signing algorithm for key: ${keyId.value}"
            ))
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
                    logger.debug("Key not found for signing in IBM Key Protect: keyId={}", keyId.value)
                    return@withContext SignResult.Failure.KeyNotFound(keyId = keyId)
                }
                logger.error("Failed to sign data in IBM Key Protect", mapOf(
                    "keyId" to keyId.value,
                    "statusCode" to response.code,
                    "region" to config.region
                ))
                return@withContext SignResult.Failure.Error(
                    keyId = keyId,
                    reason = "IBM Key Protect API error: ${response.code} - ${response.message ?: "Unknown error"}. Response: $responseBody"
                )
            }

            // Parse response to get signature
            val jsonResponse = kotlinx.serialization.json.Json.parseToJsonElement(responseBody ?: "{}").jsonObject
            val resources = jsonResponse["resources"]?.jsonArray ?: return@withContext SignResult.Failure.Error(
                keyId = keyId,
                reason = "Invalid response from IBM Key Protect: missing resources"
            )
            val signResource = resources.firstOrNull()?.jsonObject ?: return@withContext SignResult.Failure.Error(
                keyId = keyId,
                reason = "Invalid response from IBM Key Protect: no signature in response"
            )

            val signatureBase64 = signResource["signature"]?.jsonPrimitive?.content
                ?: return@withContext SignResult.Failure.Error(
                    keyId = keyId,
                    reason = "Signature not found in response"
                )

            val signature = Base64.getDecoder().decode(signatureBase64)
            
            logger.debug("Successfully signed data: keyId={}, algorithm={}, dataSize={}, signatureSize={}", 
                keyId.value, signingAlgorithm.name, data.size, signature.size)

            SignResult.Success(signature)
        } catch (e: Exception) {
            logger.error("Unexpected error signing data in IBM Key Protect", mapOf(
                "keyId" to keyId.value,
                "region" to config.region
            ), e)
            SignResult.Failure.Error(
                keyId = keyId,
                reason = "Failed to sign data: ${e.message ?: "Unknown error"}",
                cause = e
            )
        }
    }

    override suspend fun deleteKey(keyId: KeyId): DeleteKeyResult = withContext(Dispatchers.IO) {
        try {
            val resolvedKeyId = AlgorithmMapping.resolveKeyId(keyId.value)

            // IBM Key Protect API: DELETE /api/v2/keys/{id}
            // Note: IBM Key Protect requires setting deletion date first, then deleting
            // For immediate deletion, we use the DELETE endpoint

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
                if (deleteResponse.isSuccessful) {
                    logger.info("Deleted key from IBM Key Protect: keyId={}", keyId.value)
                    DeleteKeyResult.Deleted
                } else {
                    if (deleteResponse.code == 404) {
                        logger.debug("Key not found for deletion in IBM Key Protect: keyId={}", keyId.value)
                        DeleteKeyResult.NotFound
                    } else {
                        logger.error("Failed to delete key from IBM Key Protect", mapOf(
                            "keyId" to keyId.value,
                            "statusCode" to deleteResponse.code,
                            "region" to config.region
                        ))
                        DeleteKeyResult.Failure.Error(
                            keyId = keyId,
                            reason = "Failed to delete key: ${deleteResponse.code} - ${deleteResponse.message ?: "Unknown error"}"
                        )
                    }
                }
            } else {
                // If scheduling fails, try direct deletion
                if (scheduleResponse.code == 404) {
                    logger.debug("Key not found for deletion in IBM Key Protect: keyId={}", keyId.value)
                    DeleteKeyResult.NotFound
                } else {
                    val deleteRequest = Request.Builder()
                        .url("$baseUrl/api/v2/keys/$resolvedKeyId")
                        .delete()
                        .build()

                    val deleteResponse = httpClient.newCall(deleteRequest).execute()
                    if (deleteResponse.isSuccessful) {
                        logger.info("Deleted key from IBM Key Protect: keyId={}", keyId.value)
                        DeleteKeyResult.Deleted
                    } else if (deleteResponse.code == 404) {
                        logger.debug("Key not found for deletion in IBM Key Protect: keyId={}", keyId.value)
                        DeleteKeyResult.NotFound
                    } else {
                        logger.error("Failed to delete key from IBM Key Protect", mapOf(
                            "keyId" to keyId.value,
                            "statusCode" to deleteResponse.code,
                            "region" to config.region
                        ))
                        DeleteKeyResult.Failure.Error(
                            keyId = keyId,
                            reason = "Failed to delete key: ${deleteResponse.code} - ${deleteResponse.message ?: "Unknown error"}"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Unexpected error deleting key from IBM Key Protect", mapOf(
                "keyId" to keyId.value,
                "region" to config.region
            ), e)
            DeleteKeyResult.Failure.Error(
                keyId = keyId,
                reason = "Failed to delete key: ${e.message ?: "Unknown error"}",
                cause = e
            )
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
        return "$prefix-${UUID.randomUUID().toString().take(8)}"
    }

    override fun close() {
        // IBM Key Protect client cleanup if needed
    }
}
