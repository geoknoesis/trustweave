package com.trustweave.kms.fortanix

import com.trustweave.core.exception.TrustWeaveException
import com.trustweave.kms.Algorithm
import com.trustweave.kms.KeyHandle
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.KeyNotFoundException
import com.trustweave.kms.UnsupportedAlgorithmException
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
    ): KeyHandle = withContext(Dispatchers.IO) {
        if (!supportsAlgorithm(algorithm)) {
            throw UnsupportedAlgorithmException(
                "Algorithm '${algorithm.name}' is not supported by Fortanix DSM. " +
                "Supported: ${SUPPORTED_ALGORITHMS.joinToString(", ") { it.name }}"
            )
        }
        
        try {
            val keyType = AlgorithmMapping.toFortanixKeyType(algorithm)
            val keyName = options["name"] as? String ?: "TrustWeave-key-${java.util.UUID.randomUUID()}"
            
            // Fortanix DSM API: POST /crypto/v1/keys
            val requestBody = buildJsonObject {
                put("name", keyName)
                put("obj_type", keyType)
                AlgorithmMapping.toFortanixCurve(algorithm)?.let { put("curve", it) }
                AlgorithmMapping.toFortanixKeySize(algorithm)?.let { put("key_size", it) }
                (options["description"] as? String)?.let { put("description", it) }
            }
            
            val request = Request.Builder()
                .url("${config.apiEndpoint}/crypto/v1/keys")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful) {
                throw TrustWeaveException(
                    "Fortanix DSM API error: ${response.code} - ${response.message}. " +
                    "Response: $responseBody"
                )
            }
            
            // Parse response to get key ID
            val jsonResponse = kotlinx.serialization.json.Json.parseToJsonElement(responseBody ?: "{}").jsonObject
            val keyId = jsonResponse["kid"]?.jsonPrimitive?.content
                ?: throw TrustWeaveException("Key ID not found in response")
            
            // Get public key
            val publicKeyHandle = getPublicKey(keyId)
            
            KeyHandle(
                id = keyId,
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
            
            // Fortanix DSM API: GET /crypto/v1/keys/{kid}
            val request = Request.Builder()
                .url("${config.apiEndpoint}/crypto/v1/keys/$resolvedKeyId")
                .get()
                .build()
            
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    throw KeyNotFoundException("Key not found: $keyId")
                }
                throw TrustWeaveException(
                    "Fortanix DSM API error: ${response.code} - ${response.message}. " +
                    "Response: $responseBody"
                )
            }
            
            // Parse response to get key metadata
            val jsonResponse = kotlinx.serialization.json.Json.parseToJsonElement(responseBody ?: "{}").jsonObject
            val keyType = jsonResponse["obj_type"]?.jsonPrimitive?.content
                ?: throw TrustWeaveException("Key type not found in response")
            val curve = jsonResponse["curve"]?.jsonPrimitive?.content
            val keySize = jsonResponse["key_size"]?.jsonPrimitive?.intOrNull
            
            val algorithm = AlgorithmMapping.fromFortanixKeyType(keyType, curve, keySize)
                ?: throw TrustWeaveException("Unknown key type: $keyType")
            
            // Get public key material
            // Fortanix DSM API: GET /crypto/v1/keys/{kid}/export
            val exportRequest = Request.Builder()
                .url("${config.apiEndpoint}/crypto/v1/keys/$resolvedKeyId/export")
                .get()
                .build()
            
            val exportResponse = httpClient.newCall(exportRequest).execute()
            val exportBody = exportResponse.body?.string()
            
            if (!exportResponse.isSuccessful) {
                throw TrustWeaveException(
                    "Failed to get public key: ${exportResponse.code} - ${exportResponse.message}. " +
                    "Response: $exportBody"
                )
            }
            
            val exportJson = kotlinx.serialization.json.Json.parseToJsonElement(exportBody ?: "{}").jsonObject
            val publicKeyBase64 = exportJson["pub_key"]?.jsonPrimitive?.content
                ?: throw TrustWeaveException("Public key not found in response")
            val publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64)
            
            val publicKeyJwk = AlgorithmMapping.publicKeyToJwk(publicKeyBytes, algorithm)
            
            KeyHandle(
                id = resolvedKeyId,
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
                val keyHandle = getPublicKey(resolvedKeyId)
                Algorithm.parse(keyHandle.algorithm)
                    ?: throw TrustWeaveException("Cannot determine signing algorithm for key: $keyId")
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
                    throw KeyNotFoundException("Key not found: $keyId")
                }
                throw TrustWeaveException(
                    "Fortanix DSM API error: ${response.code} - ${response.message}. " +
                    "Response: $responseBody"
                )
            }
            
            // Parse response to get signature
            val jsonResponse = kotlinx.serialization.json.Json.parseToJsonElement(responseBody ?: "{}").jsonObject
            val signatureBase64 = jsonResponse["signature"]?.jsonPrimitive?.content
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
            
            // Fortanix DSM API: DELETE /crypto/v1/keys/{kid}
            val request = Request.Builder()
                .url("${config.apiEndpoint}/crypto/v1/keys/$resolvedKeyId")
                .delete()
                .build()
            
            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
    
    override fun close() {
        // Fortanix DSM client cleanup if needed
    }
}

