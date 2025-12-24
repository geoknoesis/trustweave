package org.trustweave.kms.thales

import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyHandle
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.KmsOptionKeys
import org.trustweave.kms.exception.KmsException
import org.trustweave.kms.UnsupportedAlgorithmException
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.GetPublicKeyResult
import org.trustweave.kms.results.SignResult
import org.trustweave.kms.results.DeleteKeyResult
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
 * Thales CipherTrust Manager implementation of KeyManagementService.
 *
 * Supports all Thales CipherTrust-compatible algorithms:
 * - Ed25519, secp256k1, P-256/P-384/P-521, RSA-2048/3072/4096
 *
 * Uses Thales CipherTrust Manager REST API for key operations.
 */
class ThalesKeyManagementService(
    private val config: ThalesKmsConfig,
    private val httpClient: OkHttpClient = ThalesKmsClientFactory.createClient(config)
) : KeyManagementService, AutoCloseable {

    companion object {
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

        try {
            val keyAlgorithm = AlgorithmMapping.toThalesKeyAlgorithm(algorithm)
            val keyName = options[KmsOptionKeys.NAME] as? String ?: "TrustWeave-key-${java.util.UUID.randomUUID()}"

            // Thales CipherTrust Manager API: POST /api/v1/keys
            val requestBody = buildJsonObject {
                put("name", keyName)
                put("algorithm", keyAlgorithm)
                (options[KmsOptionKeys.DESCRIPTION] as? String)?.let { put("description", it) }
                (options[KmsOptionKeys.EXPORTABLE] as? Boolean)?.let { put("exportable", it) }
            }

            val request = Request.Builder()
                .url("${config.baseUrl}/api/v1/keys")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                return@withContext GenerateKeyResult.Failure.Error(
                    algorithm = algorithm,
                    reason = "Thales CipherTrust API error: ${response.code} - ${response.message ?: "Unknown error"}. Response: $responseBody",
                    cause = null
                )
            }

            // Parse response to get key ID
            val jsonResponse = kotlinx.serialization.json.Json.parseToJsonElement(responseBody ?: "{}").jsonObject
            val keyId = jsonResponse["id"]?.jsonPrimitive?.content
                ?: return@withContext GenerateKeyResult.Failure.Error(
                    algorithm = algorithm,
                    reason = "Key ID not found in response",
                    cause = null
                )

            // Get public key
            val publicKeyResult = getPublicKey(KeyId(keyId))
            when (publicKeyResult) {
                is GetPublicKeyResult.Success -> {
                    GenerateKeyResult.Success(
                        KeyHandle(
                            id = KeyId(keyId),
                            algorithm = algorithm.name,
                            publicKeyJwk = publicKeyResult.keyHandle.publicKeyJwk
                        )
                    )
                }
                is GetPublicKeyResult.Failure.KeyNotFound -> {
                    GenerateKeyResult.Failure.Error(
                        algorithm = algorithm,
                        reason = "Key created but public key not found: ${publicKeyResult.keyId.value}",
                        cause = null
                    )
                }
                is GetPublicKeyResult.Failure.Error -> {
                    GenerateKeyResult.Failure.Error(
                        algorithm = algorithm,
                        reason = "Failed to get public key after creation: ${publicKeyResult.reason}",
                        cause = publicKeyResult.cause
                    )
                }
            }
        } catch (e: Exception) {
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

            // Thales CipherTrust Manager API: GET /api/v1/keys/{id}
            val request = Request.Builder()
                .url("${config.baseUrl}/api/v1/keys/$resolvedKeyId")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                if (response.code == 404) {
                    return@withContext GetPublicKeyResult.Failure.KeyNotFound(keyId)
                }
                return@withContext GetPublicKeyResult.Failure.Error(
                    keyId = keyId,
                    reason = "Thales CipherTrust API error: ${response.code} - ${response.message ?: "Unknown error"}. Response: $responseBody",
                    cause = null
                )
            }

            // Parse response to get key metadata
            val jsonResponse = kotlinx.serialization.json.Json.parseToJsonElement(responseBody ?: "{}").jsonObject
            val keyAlgorithm = jsonResponse["algorithm"]?.jsonPrimitive?.content
                ?: return@withContext GetPublicKeyResult.Failure.Error(
                    keyId = keyId,
                    reason = "Key algorithm not found in response",
                    cause = null
                )
            val algorithm = AlgorithmMapping.fromThalesKeyAlgorithm(keyAlgorithm)
                ?: return@withContext GetPublicKeyResult.Failure.Error(
                    keyId = keyId,
                    reason = "Unknown key algorithm: $keyAlgorithm",
                    cause = null
                )

            // Get public key material
            // Thales CipherTrust Manager API: GET /api/v1/keys/{id}/publickey
            val publicKeyRequest = Request.Builder()
                .url("${config.baseUrl}/api/v1/keys/$resolvedKeyId/publickey")
                .get()
                .build()

            val publicKeyResponse = httpClient.newCall(publicKeyRequest).execute()
            val publicKeyBody = publicKeyResponse.body?.string()

            if (!publicKeyResponse.isSuccessful) {
                return@withContext GetPublicKeyResult.Failure.Error(
                    keyId = keyId,
                    reason = "Failed to get public key: ${publicKeyResponse.code} - ${publicKeyResponse.message ?: "Unknown error"}. Response: $publicKeyBody",
                    cause = null
                )
            }

            val publicKeyJson = kotlinx.serialization.json.Json.parseToJsonElement(publicKeyBody ?: "{}").jsonObject
            val publicKeyBase64 = publicKeyJson["publicKey"]?.jsonPrimitive?.content
                ?: return@withContext GetPublicKeyResult.Failure.Error(
                    keyId = keyId,
                    reason = "Public key not found in response",
                    cause = null
                )
            val publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64)

            val publicKeyJwk = AlgorithmMapping.publicKeyToJwk(publicKeyBytes, algorithm)

            GetPublicKeyResult.Success(
                KeyHandle(
                    id = KeyId(resolvedKeyId),
                    algorithm = algorithm.name,
                    publicKeyJwk = publicKeyJwk
                )
            )
        } catch (e: Exception) {
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
        try {
            val resolvedKeyId = AlgorithmMapping.resolveKeyId(keyId.value)

            // Determine signing algorithm
            val signingAlgorithm = algorithm ?: run {
                val keyHandleResult = getPublicKey(keyId)
                when (keyHandleResult) {
                    is GetPublicKeyResult.Success -> {
                        Algorithm.parse(keyHandleResult.keyHandle.algorithm)
                            ?: return@withContext SignResult.Failure.Error(
                                keyId = keyId,
                                reason = "Cannot determine signing algorithm for key: ${keyId.value}",
                                cause = null
                            )
                    }
                    is GetPublicKeyResult.Failure.KeyNotFound -> {
                        return@withContext SignResult.Failure.KeyNotFound(keyId)
                    }
                    is GetPublicKeyResult.Failure.Error -> {
                        return@withContext SignResult.Failure.Error(
                            keyId = keyId,
                            reason = "Failed to get key metadata: ${keyHandleResult.reason}",
                            cause = keyHandleResult.cause
                        )
                    }
                }
            }

            val thalesSigningAlgorithm = AlgorithmMapping.toThalesSigningAlgorithm(signingAlgorithm)
            val dataBase64 = Base64.getEncoder().encodeToString(data)

            // Thales CipherTrust Manager API: POST /api/v1/keys/{id}/sign
            val requestBody = buildJsonObject {
                put("data", dataBase64)
                put("algorithm", thalesSigningAlgorithm)
            }

            val request = Request.Builder()
                .url("${config.baseUrl}/api/v1/keys/$resolvedKeyId/sign")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                if (response.code == 404) {
                    return@withContext SignResult.Failure.KeyNotFound(keyId)
                }
                return@withContext SignResult.Failure.Error(
                    keyId = keyId,
                    reason = "Thales CipherTrust API error: ${response.code} - ${response.message ?: "Unknown error"}. Response: $responseBody",
                    cause = null
                )
            }

            // Parse response to get signature
            val jsonResponse = kotlinx.serialization.json.Json.parseToJsonElement(responseBody ?: "{}").jsonObject
            val signatureBase64 = jsonResponse["signature"]?.jsonPrimitive?.content
                ?: return@withContext SignResult.Failure.Error(
                    keyId = keyId,
                    reason = "Signature not found in response",
                    cause = null
                )

            SignResult.Success(Base64.getDecoder().decode(signatureBase64))
        } catch (e: Exception) {
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

            // Thales CipherTrust Manager API: DELETE /api/v1/keys/{id}
            val request = Request.Builder()
                .url("${config.baseUrl}/api/v1/keys/$resolvedKeyId")
                .delete()
                .build()

            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                DeleteKeyResult.Deleted
            } else if (response.code == 404) {
                DeleteKeyResult.NotFound
            } else {
                DeleteKeyResult.Failure.Error(
                    keyId = keyId,
                    reason = "Thales CipherTrust API error: ${response.code} - ${response.message ?: "Unknown error"}",
                    cause = null
                )
            }
        } catch (e: Exception) {
            DeleteKeyResult.Failure.Error(
                keyId = keyId,
                reason = "Failed to delete key: ${e.message ?: "Unknown error"}",
                cause = e
            )
        }
    }

    override fun close() {
        // Thales CipherTrust client cleanup if needed
    }
}

