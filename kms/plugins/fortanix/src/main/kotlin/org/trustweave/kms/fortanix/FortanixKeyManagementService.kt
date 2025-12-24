package org.trustweave.kms.fortanix

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
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.Base64

/**
 * Fortanix Data Security Manager (DSM) implementation of KeyManagementService.
 *
 * Supports all Fortanix DSM-compatible algorithms:
 * - Ed25519, secp256k1, P-256/P-384/P-521, RSA-2048/3072/4096
 *
 * Uses Fortanix DSM REST API for key operations.
 */
class FortanixKeyManagementService(
    private val config: FortanixKmsConfig,
    private val httpClient: OkHttpClient = FortanixKmsClientFactory.createClient(config)
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
            val keyType = AlgorithmMapping.toFortanixKeyType(algorithm)
            val keyName = options[KmsOptionKeys.NAME] as? String ?: "TrustWeave-key-${java.util.UUID.randomUUID()}"

            // Fortanix DSM API: POST /crypto/v1/keys
            val requestBody = buildJsonObject {
                put("name", keyName)
                put("obj_type", keyType)
                AlgorithmMapping.toFortanixCurve(algorithm)?.let { put("curve", it) }
                AlgorithmMapping.toFortanixKeySize(algorithm)?.let { put("key_size", it) }
                (options[KmsOptionKeys.DESCRIPTION] as? String)?.let { put("description", it) }
            }

            val request = Request.Builder()
                .url("${config.apiEndpoint}/crypto/v1/keys")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                return@withContext GenerateKeyResult.Failure.Error(
                    algorithm = algorithm,
                    reason = "Fortanix DSM API error: ${response.code} - ${response.message ?: "Unknown error"}. Response: $responseBody",
                    cause = null
                )
            }

            // Parse response to get key ID
            val jsonResponse = kotlinx.serialization.json.Json.parseToJsonElement(responseBody ?: "{}").jsonObject
            val keyId = jsonResponse["kid"]?.jsonPrimitive?.content
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

            // Fortanix DSM API: GET /crypto/v1/keys/{kid}
            val request = Request.Builder()
                .url("${config.apiEndpoint}/crypto/v1/keys/$resolvedKeyId")
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
                    reason = "Fortanix DSM API error: ${response.code} - ${response.message ?: "Unknown error"}. Response: $responseBody",
                    cause = null
                )
            }

            // Parse response to get key metadata
            val jsonResponse = kotlinx.serialization.json.Json.parseToJsonElement(responseBody ?: "{}").jsonObject
            val keyType = jsonResponse["obj_type"]?.jsonPrimitive?.content
                ?: return@withContext GetPublicKeyResult.Failure.Error(
                    keyId = keyId,
                    reason = "Key type not found in response",
                    cause = null
                )
            val curve = jsonResponse["curve"]?.jsonPrimitive?.content
            val keySize = jsonResponse["key_size"]?.jsonPrimitive?.intOrNull

            val algorithm = AlgorithmMapping.fromFortanixKeyType(keyType, curve, keySize)
                ?: return@withContext GetPublicKeyResult.Failure.Error(
                    keyId = keyId,
                    reason = "Unknown key type: $keyType",
                    cause = null
                )

            // Get public key material
            // Fortanix DSM API: GET /crypto/v1/keys/{kid}/export
            val exportRequest = Request.Builder()
                .url("${config.apiEndpoint}/crypto/v1/keys/$resolvedKeyId/export")
                .get()
                .build()

            val exportResponse = httpClient.newCall(exportRequest).execute()
            val exportBody = exportResponse.body?.string()

            if (!exportResponse.isSuccessful) {
                return@withContext GetPublicKeyResult.Failure.Error(
                    keyId = keyId,
                    reason = "Failed to get public key: ${exportResponse.code} - ${exportResponse.message ?: "Unknown error"}. Response: $exportBody",
                    cause = null
                )
            }

            val exportJson = kotlinx.serialization.json.Json.parseToJsonElement(exportBody ?: "{}").jsonObject
            val publicKeyBase64 = exportJson["pub_key"]?.jsonPrimitive?.content
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

            val fortanixSigningAlgorithm = AlgorithmMapping.toFortanixSigningAlgorithm(signingAlgorithm)
            val dataBase64 = Base64.getEncoder().encodeToString(data)

            // Fortanix DSM API: POST /crypto/v1/keys/{kid}/sign
            val requestBody = buildJsonObject {
                put("hash_alg", fortanixSigningAlgorithm)
                put("data", dataBase64)
            }

            val request = Request.Builder()
                .url("${config.apiEndpoint}/crypto/v1/keys/$resolvedKeyId/sign")
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
                    reason = "Fortanix DSM API error: ${response.code} - ${response.message ?: "Unknown error"}. Response: $responseBody",
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

            // Fortanix DSM API: DELETE /crypto/v1/keys/{kid}
            val request = Request.Builder()
                .url("${config.apiEndpoint}/crypto/v1/keys/$resolvedKeyId")
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
                    reason = "Fortanix DSM API error: ${response.code} - ${response.message ?: "Unknown error"}",
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
        // Fortanix DSM client cleanup if needed
    }
}

