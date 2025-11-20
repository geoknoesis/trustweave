package com.geoknoesis.vericore.kms.thales

import com.geoknoesis.vericore.core.VeriCoreException
import com.geoknoesis.vericore.kms.Algorithm
import com.geoknoesis.vericore.kms.KeyHandle
import com.geoknoesis.vericore.kms.KeyManagementService
import com.geoknoesis.vericore.kms.KeyNotFoundException
import com.geoknoesis.vericore.kms.UnsupportedAlgorithmException
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
    ): KeyHandle = withContext(Dispatchers.IO) {
        if (!supportsAlgorithm(algorithm)) {
            throw UnsupportedAlgorithmException(
                "Algorithm '${algorithm.name}' is not supported by Thales CipherTrust. " +
                "Supported: ${SUPPORTED_ALGORITHMS.joinToString(", ") { it.name }}"
            )
        }
        
        try {
            val keyAlgorithm = AlgorithmMapping.toThalesKeyAlgorithm(algorithm)
            val keyName = options["name"] as? String ?: "vericore-key-${java.util.UUID.randomUUID()}"
            
            // Thales CipherTrust Manager API: POST /api/v1/keys
            val requestBody = buildJsonObject {
                put("name", keyName)
                put("algorithm", keyAlgorithm)
                (options["description"] as? String)?.let { put("description", it) }
                (options["exportable"] as? Boolean)?.let { put("exportable", it) }
            }
            
            val request = Request.Builder()
                .url("${config.baseUrl}/api/v1/keys")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful) {
                throw VeriCoreException(
                    "Thales CipherTrust API error: ${response.code} - ${response.message}. " +
                    "Response: $responseBody"
                )
            }
            
            // Parse response to get key ID
            val jsonResponse = kotlinx.serialization.json.Json.parseToJsonElement(responseBody ?: "{}").jsonObject
            val keyId = jsonResponse["id"]?.jsonPrimitive?.content
                ?: throw VeriCoreException("Key ID not found in response")
            
            // Get public key
            val publicKeyHandle = getPublicKey(keyId)
            
            KeyHandle(
                id = keyId,
                algorithm = algorithm.name,
                publicKeyJwk = publicKeyHandle.publicKeyJwk
            )
        } catch (e: VeriCoreException) {
            throw e
        } catch (e: Exception) {
            throw VeriCoreException("Failed to generate key: ${e.message}", e)
        }
    }

    override suspend fun getPublicKey(keyId: String): KeyHandle = withContext(Dispatchers.IO) {
        try {
            val resolvedKeyId = AlgorithmMapping.resolveKeyId(keyId)
            
            // Thales CipherTrust Manager API: GET /api/v1/keys/{id}
            val request = Request.Builder()
                .url("${config.baseUrl}/api/v1/keys/$resolvedKeyId")
                .get()
                .build()
            
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    throw KeyNotFoundException("Key not found: $keyId")
                }
                throw VeriCoreException(
                    "Thales CipherTrust API error: ${response.code} - ${response.message}. " +
                    "Response: $responseBody"
                )
            }
            
            // Parse response to get key metadata
            val jsonResponse = kotlinx.serialization.json.Json.parseToJsonElement(responseBody ?: "{}").jsonObject
            val keyAlgorithm = jsonResponse["algorithm"]?.jsonPrimitive?.content
                ?: throw VeriCoreException("Key algorithm not found in response")
            val algorithm = AlgorithmMapping.fromThalesKeyAlgorithm(keyAlgorithm)
                ?: throw VeriCoreException("Unknown key algorithm: $keyAlgorithm")
            
            // Get public key material
            // Thales CipherTrust Manager API: GET /api/v1/keys/{id}/publickey
            val publicKeyRequest = Request.Builder()
                .url("${config.baseUrl}/api/v1/keys/$resolvedKeyId/publickey")
                .get()
                .build()
            
            val publicKeyResponse = httpClient.newCall(publicKeyRequest).execute()
            val publicKeyBody = publicKeyResponse.body?.string()
            
            if (!publicKeyResponse.isSuccessful) {
                throw VeriCoreException(
                    "Failed to get public key: ${publicKeyResponse.code} - ${publicKeyResponse.message}. " +
                    "Response: $publicKeyBody"
                )
            }
            
            val publicKeyJson = kotlinx.serialization.json.Json.parseToJsonElement(publicKeyBody ?: "{}").jsonObject
            val publicKeyBase64 = publicKeyJson["publicKey"]?.jsonPrimitive?.content
                ?: throw VeriCoreException("Public key not found in response")
            val publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64)
            
            val publicKeyJwk = AlgorithmMapping.publicKeyToJwk(publicKeyBytes, algorithm)
            
            KeyHandle(
                id = resolvedKeyId,
                algorithm = algorithm.name,
                publicKeyJwk = publicKeyJwk
            )
        } catch (e: KeyNotFoundException) {
            throw e
        } catch (e: VeriCoreException) {
            throw e
        } catch (e: Exception) {
            throw VeriCoreException("Failed to get public key: ${e.message}", e)
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
                    ?: throw VeriCoreException("Cannot determine signing algorithm for key: $keyId")
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
                    throw KeyNotFoundException("Key not found: $keyId")
                }
                throw VeriCoreException(
                    "Thales CipherTrust API error: ${response.code} - ${response.message}. " +
                    "Response: $responseBody"
                )
            }
            
            // Parse response to get signature
            val jsonResponse = kotlinx.serialization.json.Json.parseToJsonElement(responseBody ?: "{}").jsonObject
            val signatureBase64 = jsonResponse["signature"]?.jsonPrimitive?.content
                ?: throw VeriCoreException("Signature not found in response")
            
            Base64.getDecoder().decode(signatureBase64)
        } catch (e: KeyNotFoundException) {
            throw e
        } catch (e: VeriCoreException) {
            throw e
        } catch (e: Exception) {
            throw VeriCoreException("Failed to sign data: ${e.message}", e)
        }
    }

    override suspend fun deleteKey(keyId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolvedKeyId = AlgorithmMapping.resolveKeyId(keyId)
            
            // Thales CipherTrust Manager API: DELETE /api/v1/keys/{id}
            val request = Request.Builder()
                .url("${config.baseUrl}/api/v1/keys/$resolvedKeyId")
                .delete()
                .build()
            
            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
    
    override fun close() {
        // Thales CipherTrust client cleanup if needed
    }
}

