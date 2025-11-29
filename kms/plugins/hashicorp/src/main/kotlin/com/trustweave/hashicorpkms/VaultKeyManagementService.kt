package com.trustweave.hashicorpkms

import com.bettercloud.vault.Vault
import com.bettercloud.vault.VaultException
import com.bettercloud.vault.response.LogicalResponse
import com.trustweave.core.exception.TrustWeaveException
import com.trustweave.core.types.KeyId
import com.trustweave.kms.Algorithm
import com.trustweave.kms.KeyHandle
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.exception.KmsException
import com.trustweave.kms.UnsupportedAlgorithmException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64
import java.util.UUID

/**
 * HashiCorp Vault implementation of KeyManagementService using the Transit engine.
 *
 * Supports all Vault Transit-compatible algorithms:
 * - Ed25519 (ed25519)
 * - secp256k1 (ecdsa-p256k1)
 * - P-256, P-384, P-521 (ecdsa-p256/384/521)
 * - RSA-2048, RSA-3072, RSA-4096 (rsa-2048/3072/4096)
 *
 * **Example:**
 * ```kotlin
 * val config = VaultKmsConfig.builder()
 *     .address("http://localhost:8200")
 *     .token("hvs.xxx")
 *     .build()
 * val kms = VaultKeyManagementService(config)
 * val key = kms.generateKey(Algorithm.Ed25519)
 * ```
 */
class VaultKeyManagementService(
    private val config: VaultKmsConfig,
    private val vaultClient: Vault = VaultKmsClientFactory.createClient(config)
) : KeyManagementService, AutoCloseable {

    companion object {
        /**
         * Algorithms supported by Vault Transit engine.
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
                "Algorithm '${algorithm.name}' is not supported by Vault Transit. " +
                "Supported: ${SUPPORTED_ALGORITHMS.joinToString(", ") { it.name }}"
            )
        }

        try {
            val keyName = (options["keyName"] as? String) ?: generateKeyName(algorithm)
            val vaultKeyType = AlgorithmMapping.toVaultKeyType(algorithm)

            // Create key in Vault Transit
            val createParams = mutableMapOf<String, Any>(
                "type" to vaultKeyType
            )

            // Add optional parameters
            (options["exportable"] as? Boolean)?.let {
                createParams["exportable"] = it
            }
            (options["allowPlaintextBackup"] as? Boolean)?.let {
                createParams["allow_plaintext_backup"] = it
            }

            val createPath = "${config.transitPath}/keys/$keyName"
            vaultClient.logical().write(createPath, createParams)

            // Get public key
            val keyInfoPath = "${config.transitPath}/keys/$keyName"
            val keyInfo = vaultClient.logical().read(keyInfoPath)

            val publicKeyPem = keyInfo.data["keys"]?.let { keys ->
                // Get the latest version's public key
                val latestVersion = keyInfo.data["latest_version"] as? String ?: "1"
                val versionData = (keys as? Map<*, *>)?.get(latestVersion) as? Map<*, *>
                versionData?.get("public_key") as? String
            } ?: throw TrustWeaveException.Unknown(
                message = "Failed to retrieve public key from Vault"
            )

            // Convert PEM to JWK
            val publicKeyJwk = AlgorithmMapping.publicKeyPemToJwk(publicKeyPem, algorithm)

            // Full key path for identification
            val keyIdStr = "${config.transitPath}/keys/$keyName"

            KeyHandle(
                id = KeyId(keyIdStr),
                algorithm = algorithm.name,
                publicKeyJwk = publicKeyJwk
            )
        } catch (e: VaultException) {
            throw mapVaultException(e, "Failed to generate key")
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                message = "Failed to generate key: ${e.message ?: "Unknown error"}",
                cause = e
            )
        }
    }

    override suspend fun getPublicKey(keyId: KeyId): KeyHandle = withContext(Dispatchers.IO) {
        try {
            val keyName = AlgorithmMapping.resolveKeyName(keyId.value, config)
            val keyInfoPath = "${config.transitPath}/keys/$keyName"

            val keyInfo = vaultClient.logical().read(keyInfoPath)

            if (keyInfo.data.isEmpty()) {
                throw KmsException.KeyNotFound(keyId = keyId.value)
            }

            val keyType = keyInfo.data["type"] as? String
                ?: throw TrustWeaveException.Unknown(
                    message = "Key type not found in Vault response"
                )

            val algorithm = AlgorithmMapping.fromVaultKeyType(keyType)
                ?: throw TrustWeaveException.Unknown(
                    message = "Unknown key type: $keyType"
                )

            val latestVersion = keyInfo.data["latest_version"] as? String ?: "1"
            val keys = keyInfo.data["keys"] as? Map<*, *>
            val versionData = keys?.get(latestVersion) as? Map<*, *>
            val publicKeyPem = versionData?.get("public_key") as? String
                ?: throw TrustWeaveException.Unknown(
                    message = "Public key not found for key: ${keyId.value}"
                )

            val publicKeyJwk = AlgorithmMapping.publicKeyPemToJwk(publicKeyPem, algorithm)

            KeyHandle(
                id = keyId,
                algorithm = algorithm.name,
                publicKeyJwk = publicKeyJwk
            )
        } catch (e: VaultException) {
            val httpCode = getHttpCode(e)
            if (httpCode == 404) {
                throw KmsException.KeyNotFound(keyId = keyId.value)
            }
            throw mapVaultException(e, "Failed to get public key")
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
            val keyName = AlgorithmMapping.resolveKeyName(keyId.value, config)

            // Determine signing algorithm
            val signingAlgorithm = algorithm ?: run {
                val keyInfoPath = "${config.transitPath}/keys/$keyName"
                val keyInfo = vaultClient.logical().read(keyInfoPath)
                val keyType = keyInfo.data["type"] as? String
                    ?: throw TrustWeaveException.Unknown(
                        message = "Cannot determine signing algorithm for key: ${keyId.value}"
                    )
                AlgorithmMapping.fromVaultKeyType(keyType)
                    ?: throw TrustWeaveException.Unknown(
                        message = "Cannot determine signing algorithm for key: ${keyId.value}"
                    )
            }

            val hashAlgorithm = AlgorithmMapping.toVaultHashAlgorithm(signingAlgorithm)

            // Base64 encode the data
            val base64Data = Base64.getEncoder().encodeToString(data)

            // Sign using Vault Transit
            val signPath = "${config.transitPath}/sign/$keyName"
            val signParams = mapOf(
                "input" to base64Data,
                "hash_algorithm" to hashAlgorithm,
                "marshaling_algorithm" to "asn1"
            )

            val signResponse = vaultClient.logical().write(signPath, signParams)
            val signature = signResponse.data["signature"] as? String
                ?: throw TrustWeaveException.Unknown(
                    message = "Signature not found in Vault response"
                )

            // Extract signature from Vault format (vault:v1:base64signature)
            val signatureBase64 = signature.substringAfter(":")
                .substringAfter(":")

            Base64.getDecoder().decode(signatureBase64)
        } catch (e: VaultException) {
            val httpCode = getHttpCode(e)
            if (httpCode == 404) {
                throw KmsException.KeyNotFound(keyId = keyId.value)
            }
            throw mapVaultException(e, "Failed to sign data")
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                message = "Failed to sign data: ${e.message ?: "Unknown error"}",
                cause = e
            )
        }
    }

    override suspend fun deleteKey(keyId: KeyId): Boolean = withContext(Dispatchers.IO) {
        try {
            val keyName = AlgorithmMapping.resolveKeyName(keyId.value, config)
            val deletePath = "${config.transitPath}/keys/$keyName"

            // Vault Transit doesn't support key deletion by default
            // Keys can be rotated or archived, but deletion requires special configuration
            // For now, we'll attempt to delete and return false if not supported
            vaultClient.logical().delete(deletePath)
            true
        } catch (e: VaultException) {
            val httpCode = getHttpCode(e)
            if (httpCode == 404) {
                return@withContext false // Key doesn't exist
            }
            // Deletion may not be allowed by policy
            false
        } catch (e: Exception) {
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
        return "$prefix-${UUID.randomUUID().toString().take(8)}"
    }

    /**
     * Extracts HTTP status code from VaultException.
     */
    private fun getHttpCode(e: VaultException): Int? {
        // VaultException may contain HTTP status code in message or as a field
        // Try to extract from exception message or use reflection
        return try {
            // Check if exception has httpCode field via reflection
            val httpCodeField = e.javaClass.getDeclaredField("httpCode")
            httpCodeField.isAccessible = true
            httpCodeField.get(e) as? Int
        } catch (ex: Exception) {
            // If httpCode field doesn't exist, try to parse from message
            val message = e.message ?: ""
            when {
                message.contains("404") || message.contains("not found") -> 404
                message.contains("403") || message.contains("permission denied") -> 403
                message.contains("401") || message.contains("unauthorized") -> 401
                else -> null
            }
        }
    }

    /**
     * Maps Vault exceptions to TrustWeave exceptions.
     */
    private fun mapVaultException(e: VaultException, operation: String): Exception {
        val httpCode = getHttpCode(e)
        return when (httpCode) {
            404 -> {
                val errorMessage = e.message ?: "Key not found"
                KmsException.KeyNotFound(keyId = errorMessage)
            }
            403 -> TrustWeaveException.Unknown(
                message = "Access denied to Vault. Check Vault policies: ${e.message ?: "Unknown error"}",
                cause = e
            )
            401 -> TrustWeaveException.Unknown(
                message = "Authentication failed. Check Vault token or credentials: ${e.message ?: "Unknown error"}",
                cause = e
            )
            else -> TrustWeaveException.Unknown(
                message = "$operation: ${e.message ?: "Unknown error"}",
                cause = e
            )
        }
    }

    override fun close() {
        // Vault client doesn't need explicit closing, but we can add cleanup if needed
    }
}

