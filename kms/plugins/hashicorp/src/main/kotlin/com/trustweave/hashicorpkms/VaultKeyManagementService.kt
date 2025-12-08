package com.trustweave.hashicorpkms

import com.bettercloud.vault.Vault
import com.bettercloud.vault.VaultException
import com.trustweave.core.identifiers.KeyId
import com.trustweave.kms.Algorithm
import com.trustweave.kms.KeyHandle
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.results.DeleteKeyResult
import com.trustweave.kms.results.GenerateKeyResult
import com.trustweave.kms.results.GetPublicKeyResult
import com.trustweave.kms.results.SignResult
import com.trustweave.kms.KmsOptionKeys
import com.trustweave.kms.util.KmsErrorHandler
import com.trustweave.kms.util.KmsInputValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
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
 * val result = kms.generateKey(Algorithm.Ed25519)
 * when (result) {
 *     is GenerateKeyResult.Success -> println("Key created: ${result.keyHandle.id}")
 *     is GenerateKeyResult.Failure -> println("Error: ${result.reason}")
 * }
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

    private val logger = LoggerFactory.getLogger(VaultKeyManagementService::class.java)

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
        val keyName = (options[KmsOptionKeys.KEY_NAME] as? String)?.takeIf { it.isNotBlank() }
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
            val finalKeyName = keyName ?: generateKeyName(algorithm)
            val vaultKeyType = AlgorithmMapping.toVaultKeyType(algorithm)

            // Create key in Vault Transit
            val createParams = mutableMapOf<String, Any>(
                "type" to vaultKeyType
            )

            // Add optional parameters
            (options[KmsOptionKeys.EXPORTABLE] as? Boolean)?.let {
                createParams["exportable"] = it
            }
            (options[KmsOptionKeys.ALLOW_PLAINTEXT_BACKUP] as? Boolean)?.let {
                createParams["allow_plaintext_backup"] = it
            }

            val createPath = "${config.transitPath}/keys/$finalKeyName"
            vaultClient.logical().write(createPath, createParams)

            logger.debug("Created key in Vault Transit: keyName={}, algorithm={}", finalKeyName, algorithm.name)

            // Get public key
            val keyInfoPath = "${config.transitPath}/keys/$finalKeyName"
            val keyInfo = vaultClient.logical().read(keyInfoPath)

            val publicKeyPem = keyInfo.data["keys"]?.let { keys ->
                // Get the latest version's public key
                val latestVersion = keyInfo.data["latest_version"] as? String ?: "1"
                val versionData = (keys as? Map<*, *>)?.get(latestVersion) as? Map<*, *>
                versionData?.get("public_key") as? String
            } ?: return@withContext GenerateKeyResult.Failure.Error(
                algorithm = algorithm,
                reason = "Failed to retrieve public key from Vault",
                cause = null
            )

            // Convert PEM to JWK
            val publicKeyJwk = AlgorithmMapping.publicKeyPemToJwk(publicKeyPem, algorithm)

            // Full key path for identification
            val keyIdStr = "${config.transitPath}/keys/$finalKeyName"
            val keyId = KeyId(keyIdStr)

            logger.info("Generated key in Vault Transit: keyId={}, algorithm={}", keyId.value, algorithm.name)

            GenerateKeyResult.Success(
                KeyHandle(
                    id = keyId,
                    algorithm = algorithm.name,
                    publicKeyJwk = publicKeyJwk
                )
            )
        } catch (e: VaultException) {
            val httpCode = getHttpCode(e)
            val context = KmsErrorHandler.createErrorContext(
                algorithm = algorithm,
                operation = "generateKey",
                additional = mapOf(
                    "vaultAddress" to config.address,
                    "transitPath" to config.transitPath,
                    "httpCode" to (httpCode ?: "unknown")
                )
            )
            
            when (httpCode) {
                400, 403 -> {
                    logger.error("Invalid request to Vault Transit", context, e)
                    GenerateKeyResult.Failure.InvalidOptions(
                        algorithm = algorithm,
                        reason = "Invalid request to Vault: ${e.message ?: "Unknown error"}",
                        invalidOptions = options
                    )
                }
                404 -> {
                    logger.error("Resource not found in Vault", context, e)
                    GenerateKeyResult.Failure.Error(
                        algorithm = algorithm,
                        reason = "Resource not found in Vault: ${e.message ?: "Unknown error"}",
                        cause = e
                    )
                }
                401 -> {
                    logger.error("Authentication failed with Vault", context, e)
                    GenerateKeyResult.Failure.Error(
                        algorithm = algorithm,
                        reason = "Authentication failed. Check Vault token: ${e.message ?: "Unknown error"}",
                        cause = e
                    )
                }
                else -> {
                    logger.error("Failed to generate key in Vault Transit", context, e)
                    GenerateKeyResult.Failure.Error(
                        algorithm = algorithm,
                        reason = "Failed to generate key: ${e.message ?: "Unknown error"}",
                        cause = e
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("Unexpected error generating key in Vault Transit", mapOf(
                "algorithm" to algorithm.name,
                "vaultAddress" to config.address
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
            val keyName = AlgorithmMapping.resolveKeyName(keyId.value, config)
            val keyInfoPath = "${config.transitPath}/keys/$keyName"

            val keyInfo = vaultClient.logical().read(keyInfoPath)

            if (keyInfo.data.isEmpty()) {
                logger.debug("Key not found in Vault Transit: keyId={}", keyId.value)
                return@withContext GetPublicKeyResult.Failure.KeyNotFound(keyId = keyId)
            }

            val keyType = keyInfo.data["type"] as? String
                ?: return@withContext GetPublicKeyResult.Failure.Error(
                    keyId = keyId,
                    reason = "Key type not found in Vault response"
                )

            val algorithm = AlgorithmMapping.fromVaultKeyType(keyType)
                ?: return@withContext GetPublicKeyResult.Failure.Error(
                    keyId = keyId,
                    reason = "Unknown key type: $keyType"
                )

            val latestVersion = keyInfo.data["latest_version"] as? String ?: "1"
            val keys = keyInfo.data["keys"] as? Map<*, *>
            val versionData = keys?.get(latestVersion) as? Map<*, *>
            val publicKeyPem = versionData?.get("public_key") as? String
                ?: return@withContext GetPublicKeyResult.Failure.Error(
                    keyId = keyId,
                    reason = "Public key not found for key: ${keyId.value}"
                )

            val publicKeyJwk = AlgorithmMapping.publicKeyPemToJwk(publicKeyPem, algorithm)

            GetPublicKeyResult.Success(
                KeyHandle(
                    id = keyId,
                    algorithm = algorithm.name,
                    publicKeyJwk = publicKeyJwk
                )
            )
        } catch (e: VaultException) {
            val httpCode = getHttpCode(e)
            if (httpCode == 404) {
                logger.debug("Key not found in Vault Transit: keyId={}", keyId.value)
                GetPublicKeyResult.Failure.KeyNotFound(keyId = keyId)
            } else {
                logger.error("Failed to get public key from Vault Transit", mapOf(
                    "keyId" to keyId.value,
                    "httpCode" to (httpCode ?: "unknown"),
                    "vaultAddress" to config.address
                ), e)
                GetPublicKeyResult.Failure.Error(
                    keyId = keyId,
                    reason = "Failed to get public key: ${e.message ?: "Unknown error"}",
                    cause = e
                )
            }
        } catch (e: Exception) {
            logger.error("Unexpected error getting public key from Vault Transit", mapOf(
                "keyId" to keyId.value,
                "vaultAddress" to config.address
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
            val keyName = AlgorithmMapping.resolveKeyName(keyId.value, config)

            // Determine signing algorithm
            val signingAlgorithm = algorithm ?: run {
                val keyInfoPath = "${config.transitPath}/keys/$keyName"
                val keyInfo = vaultClient.logical().read(keyInfoPath)
                val keyType = keyInfo.data["type"] as? String
                    ?: return@withContext SignResult.Failure.Error(
                        keyId = keyId,
                        reason = "Cannot determine signing algorithm for key: ${keyId.value}"
                    )
                AlgorithmMapping.fromVaultKeyType(keyType)
                    ?: return@withContext SignResult.Failure.Error(
                        keyId = keyId,
                        reason = "Cannot determine signing algorithm for key: ${keyId.value}"
                    )
            }

            // Check algorithm compatibility if algorithm was provided
            if (algorithm != null) {
                val keyInfoPath = "${config.transitPath}/keys/$keyName"
                val keyInfo = vaultClient.logical().read(keyInfoPath)
                val keyType = keyInfo.data["type"] as? String
                val keyAlgorithm = keyType?.let { AlgorithmMapping.fromVaultKeyType(it) }
                
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
                ?: return@withContext SignResult.Failure.Error(
                    keyId = keyId,
                    reason = "Signature not found in Vault response"
                )

            // Extract signature from Vault format (vault:v1:base64signature)
            val signatureBase64 = signature.substringAfter(":")
                .substringAfter(":")

            val signatureBytes = Base64.getDecoder().decode(signatureBase64)
            
            logger.debug("Successfully signed data: keyId={}, algorithm={}, dataSize={}, signatureSize={}", 
                keyId.value, signingAlgorithm.name, data.size, signatureBytes.size)

            SignResult.Success(signatureBytes)
        } catch (e: VaultException) {
            val httpCode = getHttpCode(e)
            if (httpCode == 404) {
                logger.debug("Key not found for signing in Vault Transit: keyId={}", keyId.value)
                SignResult.Failure.KeyNotFound(keyId = keyId)
            } else {
                logger.error("Failed to sign data in Vault Transit", mapOf(
                    "keyId" to keyId.value,
                    "httpCode" to (httpCode ?: "unknown"),
                    "vaultAddress" to config.address
                ), e)
                SignResult.Failure.Error(
                    keyId = keyId,
                    reason = "Failed to sign data: ${e.message ?: "Unknown error"}",
                    cause = e
                )
            }
        } catch (e: Exception) {
            logger.error("Unexpected error signing data in Vault Transit", mapOf(
                "keyId" to keyId.value,
                "vaultAddress" to config.address
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
            val keyName = AlgorithmMapping.resolveKeyName(keyId.value, config)
            val deletePath = "${config.transitPath}/keys/$keyName"

            // Vault Transit doesn't support key deletion by default
            // Keys can be rotated or archived, but deletion requires special configuration
            // For now, we'll attempt to delete and return appropriate result
            vaultClient.logical().delete(deletePath)
            
            logger.info("Deleted key from Vault Transit: keyId={}", keyId.value)
            DeleteKeyResult.Deleted
        } catch (e: VaultException) {
            val httpCode = getHttpCode(e)
            if (httpCode == 404) {
                logger.debug("Key not found for deletion in Vault Transit: keyId={}", keyId.value)
                DeleteKeyResult.NotFound
            } else {
                logger.error("Failed to delete key from Vault Transit", mapOf(
                    "keyId" to keyId.value,
                    "httpCode" to (httpCode ?: "unknown"),
                    "vaultAddress" to config.address
                ), e)
                DeleteKeyResult.Failure.Error(
                    keyId = keyId,
                    reason = "Failed to delete key: ${e.message ?: "Unknown error"}. " +
                            "Note: Vault Transit may require special policy configuration for key deletion.",
                    cause = e
                )
            }
        } catch (e: Exception) {
            logger.error("Unexpected error deleting key from Vault Transit", mapOf(
                "keyId" to keyId.value,
                "vaultAddress" to config.address
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

    override fun close() {
        // Vault client doesn't need explicit closing, but we can add cleanup if needed
    }
}
