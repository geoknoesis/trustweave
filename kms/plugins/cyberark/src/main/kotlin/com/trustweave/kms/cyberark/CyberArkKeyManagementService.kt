package com.trustweave.kms.cyberark

import com.trustweave.core.exception.TrustWeaveException
import com.trustweave.core.types.KeyId
import com.trustweave.kms.Algorithm
import com.trustweave.kms.KeyHandle
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.exception.KmsException
import com.trustweave.kms.UnsupportedAlgorithmException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64

/**
 * CyberArk Conjur implementation of KeyManagementService.
 *
 * Note: Conjur is primarily a secrets management system. This implementation
 * generates keys locally and stores them in Conjur as secrets. For signing,
 * keys are retrieved from Conjur and operations are performed locally.
 *
 * Supports all standard cryptographic algorithms.
 */
class CyberArkKeyManagementService(
    private val config: CyberArkKmsConfig,
    private val httpClient: OkHttpClient = ConjurClientFactory.createClient(config)
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
                "Algorithm '${algorithm.name}' is not supported by CyberArk Conjur. " +
                "Supported: ${SUPPORTED_ALGORITHMS.joinToString(", ") { it.name }}"
            )
        }

        try {
            // Generate key pair locally (Conjur doesn't generate keys, it stores them)
            val keyPair = generateKeyPair(algorithm)
            val keyId = options["name"] as? String ?: "TrustWeave-key-${java.util.UUID.randomUUID()}"
            val secretPath = AlgorithmMapping.resolveKeyId(keyId, config.account)

            // Store private key in Conjur as a secret
            // Conjur API: POST /secrets/{account}/{kind}/{identifier}
            val privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.private.encoded)
            val requestBody = buildJsonObject {
                put("value", privateKeyBase64)
            }

            val request = Request.Builder()
                .url("${config.conjurUrl}/secrets${secretPath}/private")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                throw TrustWeaveException.Unknown(
                    message = "CyberArk Conjur API error: ${response.code} - ${response.message ?: "Unknown error"}. " +
                    "Response: $responseBody"
                )
            }

            // Store public key metadata
            val publicKeyJwk = AlgorithmMapping.publicKeyToJwk(keyPair.public.encoded, algorithm)
            val metadataBody = buildJsonObject {
                put("algorithm", AlgorithmMapping.toConjurAlgorithm(algorithm))
                put("publicKey", Base64.getEncoder().encodeToString(keyPair.public.encoded))
            }

            val metadataRequest = Request.Builder()
                .url("${config.conjurUrl}/secrets${secretPath}/metadata")
                .post(metadataBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(metadataRequest).execute().use { /* Store metadata */ }

            KeyHandle(
                id = KeyId(secretPath),
                algorithm = algorithm.name,
                publicKeyJwk = publicKeyJwk
            )
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                message = "Failed to generate key: ${e.message ?: "Unknown error"}",
                cause = e
            )
        }
    }

    /**
     * Generates a key pair locally for the given algorithm.
     */
    private fun generateKeyPair(algorithm: Algorithm): java.security.KeyPair {
        val keyPairGenerator = when (algorithm) {
            is Algorithm.Ed25519 -> {
                // Ed25519 requires special handling
                val kg = KeyPairGenerator.getInstance("Ed25519")
                kg.initialize(256)
                kg
            }
            is Algorithm.Secp256k1 -> {
                val kg = KeyPairGenerator.getInstance("EC")
                val ecSpec = java.security.spec.ECGenParameterSpec("secp256k1")
                kg.initialize(ecSpec)
                kg
            }
            is Algorithm.P256 -> {
                val kg = KeyPairGenerator.getInstance("EC")
                val ecSpec = java.security.spec.ECGenParameterSpec("secp256r1")
                kg.initialize(ecSpec)
                kg
            }
            is Algorithm.P384 -> {
                val kg = KeyPairGenerator.getInstance("EC")
                val ecSpec = java.security.spec.ECGenParameterSpec("secp384r1")
                kg.initialize(ecSpec)
                kg
            }
            is Algorithm.P521 -> {
                val kg = KeyPairGenerator.getInstance("EC")
                val ecSpec = java.security.spec.ECGenParameterSpec("secp521r1")
                kg.initialize(ecSpec)
                kg
            }
            is Algorithm.RSA -> {
                val kg = KeyPairGenerator.getInstance("RSA")
                kg.initialize(algorithm.keySize)
                kg
            }
            else -> throw IllegalArgumentException("Unsupported algorithm: ${algorithm.name}")
        }
        return keyPairGenerator.generateKeyPair()
    }

    override suspend fun getPublicKey(keyId: KeyId): KeyHandle = withContext(Dispatchers.IO) {
        try {
            val secretPath = AlgorithmMapping.resolveKeyId(keyId.value, config.account)

            // Get metadata from Conjur
            // Conjur API: GET /secrets/{account}/{kind}/{identifier}/metadata
            val metadataRequest = Request.Builder()
                .url("${config.conjurUrl}/secrets${secretPath}/metadata")
                .get()
                .build()

            val metadataResponse = httpClient.newCall(metadataRequest).execute()
            val metadataBody = metadataResponse.body?.string()

            if (!metadataResponse.isSuccessful) {
                if (metadataResponse.code == 404) {
                    throw KmsException.KeyNotFound(keyId = keyId.value)
                }
                throw TrustWeaveException.Unknown(
                    message = "CyberArk Conjur API error: ${metadataResponse.code} - ${metadataResponse.message ?: "Unknown error"}. " +
                    "Response: $metadataBody"
                )
            }

            val metadataJson = kotlinx.serialization.json.Json.parseToJsonElement(metadataBody ?: "{}").jsonObject
            val algorithmStr = metadataJson["algorithm"]?.jsonPrimitive?.content
                ?: throw TrustWeaveException.Unknown(
                    message = "Algorithm not found in metadata"
                )
            val algorithm = AlgorithmMapping.fromConjurAlgorithm(algorithmStr)
                ?: throw TrustWeaveException.Unknown(
                    message = "Unknown algorithm: $algorithmStr"
                )

            val publicKeyBase64 = metadataJson["publicKey"]?.jsonPrimitive?.content
                ?: throw TrustWeaveException.Unknown(
                    message = "Public key not found in metadata"
                )
            val publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64)

            val publicKeyJwk = AlgorithmMapping.publicKeyToJwk(publicKeyBytes, algorithm)

            KeyHandle(
                id = KeyId(secretPath),
                algorithm = algorithm.name,
                publicKeyJwk = publicKeyJwk
            )
        } catch (e: KmsException.KeyNotFound) {
            throw e
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                message = "Failed to get public key: ${e.message ?: "Unknown error"}",
                cause = e
            )
        }
    }

    override suspend fun sign(
        keyId: KeyId,
        data: ByteArray,
        algorithm: Algorithm?
    ): ByteArray = withContext(Dispatchers.IO) {
        try {
            val secretPath = AlgorithmMapping.resolveKeyId(keyId.value, config.account)

            // Get private key from Conjur
            // Conjur API: GET /secrets/{account}/{kind}/{identifier}/private
            val privateKeyRequest = Request.Builder()
                .url("${config.conjurUrl}/secrets${secretPath}/private")
                .get()
                .build()

            val privateKeyResponse = httpClient.newCall(privateKeyRequest).execute()
            val privateKeyBody = privateKeyResponse.body?.string()

            if (!privateKeyResponse.isSuccessful) {
                if (privateKeyResponse.code == 404) {
                    throw KmsException.KeyNotFound(keyId = keyId.value)
                }
                throw TrustWeaveException.Unknown(
                    message = "Failed to get private key: ${privateKeyResponse.code} - ${privateKeyResponse.message ?: "Unknown error"}. " +
                    "Response: $privateKeyBody"
                )
            }

            // Parse private key
            val privateKeyJson = kotlinx.serialization.json.Json.parseToJsonElement(privateKeyBody ?: "{}").jsonObject
            val privateKeyBase64 = privateKeyJson["value"]?.jsonPrimitive?.content
                ?: throw TrustWeaveException.Unknown(
                    message = "Private key value not found"
                )
            val privateKeyBytes = Base64.getDecoder().decode(privateKeyBase64)

            // Get algorithm from metadata if not provided
            val signingAlgorithm = algorithm ?: run {
                val keyHandle = getPublicKey(keyId)
                Algorithm.parse(keyHandle.algorithm)
                    ?: throw TrustWeaveException.Unknown(
                        message = "Cannot determine signing algorithm"
                    )
            }

            // Sign data locally using the private key
            val signature = signWithPrivateKey(privateKeyBytes, data, signingAlgorithm)
            signature
        } catch (e: KmsException.KeyNotFound) {
            throw e
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                message = "Failed to sign data: ${e.message ?: "Unknown error"}",
                cause = e
            )
        }
    }

    /**
     * Signs data using a private key.
     */
    private fun signWithPrivateKey(
        privateKeyBytes: ByteArray,
        data: ByteArray,
        algorithm: Algorithm
    ): ByteArray {
        val keyFactory = when (algorithm) {
            is Algorithm.Ed25519 -> java.security.KeyFactory.getInstance("Ed25519")
            is Algorithm.Secp256k1, is Algorithm.P256, is Algorithm.P384, is Algorithm.P521 ->
                java.security.KeyFactory.getInstance("EC")
            is Algorithm.RSA -> java.security.KeyFactory.getInstance("RSA")
            else -> throw IllegalArgumentException("Unsupported algorithm: ${algorithm.name}")
        }

        val privateKey = keyFactory.generatePrivate(
            java.security.spec.PKCS8EncodedKeySpec(privateKeyBytes)
        ) as PrivateKey

        val signature = Signature.getInstance(
            when (algorithm) {
                is Algorithm.Ed25519 -> "Ed25519"
                is Algorithm.Secp256k1, is Algorithm.P256, is Algorithm.P384, is Algorithm.P521 ->
                    "SHA256withECDSA"
                is Algorithm.RSA -> "SHA256withRSA"
                else -> throw IllegalArgumentException("Unsupported algorithm: ${algorithm.name}")
            }
        )

        signature.initSign(privateKey)
        signature.update(data)
        return signature.sign()
    }

    override suspend fun deleteKey(keyId: KeyId): Boolean = withContext(Dispatchers.IO) {
        try {
            val secretPath = AlgorithmMapping.resolveKeyId(keyId.value, config.account)

            // Delete secret from Conjur
            // Conjur API: DELETE /secrets/{account}/{kind}/{identifier}
            val request = Request.Builder()
                .url("${config.conjurUrl}/secrets$secretPath")
                .delete()
                .build()

            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    override fun close() {
        // CyberArk Conjur client cleanup if needed
    }
}

