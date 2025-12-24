package org.trustweave.awskms

import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyHandle
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.DeleteKeyResult
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.GetPublicKeyResult
import org.trustweave.kms.results.SignResult
import org.trustweave.kms.KmsOptionKeys
import org.trustweave.kms.util.KmsInputValidator
import org.trustweave.kms.util.CacheEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.*
import software.amazon.awssdk.awscore.exception.AwsServiceException
import java.util.concurrent.ConcurrentHashMap

/**
 * AWS KMS implementation of KeyManagementService.
 *
 * Supports all AWS KMS-compatible algorithms:
 * - Ed25519 (ECC_Ed25519) - Note: Not in current FIPS certificate, may use non-FIPS path
 * - secp256k1 (ECC_SECG_P256K1) - FIPS 140-3 Level 3 validated (blockchain use only)
 * - P-256, P-384, P-521 (ECC_NIST_P256/384/521) - FIPS 140-3 Level 3 validated
 * - RSA-2048, RSA-3072, RSA-4096 - FIPS 140-3 Level 3 validated
 *
 * AWS KMS uses FIPS 140-3 Level 3 validated hardware security modules.
 * See [NIST Certificate #4884](https://csrc.nist.gov/projects/cryptographic-module-validation-program/certificate/4884)
 * for validation details.
 *
 * **Example:**
 * ```kotlin
 * val config = AwsKmsConfig.builder()
 *     .region("us-east-1")
 *     .build()
 * val kms = AwsKeyManagementService(config)
 * val key = kms.generateKey(Algorithm.Ed25519)
 * ```
 */
class AwsKeyManagementService(
    private val config: AwsKmsConfig,
    private val kmsClient: KmsClient = AwsKmsClientFactory.createClient(config)
) : KeyManagementService, AutoCloseable {

    companion object {
        /**
         * Algorithms supported by AWS KMS.
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

        /**
         * Default pending window for key deletion (30 days).
         */
        const val DEFAULT_PENDING_WINDOW_DAYS = 30
    }

    private val logger = LoggerFactory.getLogger(AwsKeyManagementService::class.java)
    
    // Cache for key metadata to avoid duplicate describeKey calls
    private val keyMetadataCache = ConcurrentHashMap<String, CacheEntry<KeyMetadata>>()

    override suspend fun getSupportedAlgorithms(): Set<Algorithm> = SUPPORTED_ALGORITHMS
    
    /**
     * Gets cached key metadata or fetches it if not cached or expired.
     */
    private suspend fun getCachedKeyMetadata(resolvedKeyId: String): KeyMetadata {
        val cacheEntry = keyMetadataCache[resolvedKeyId]
        
        // Check if cache entry exists and is not expired
        if (cacheEntry != null && !cacheEntry.isExpired()) {
            logger.debug("Using cached key metadata: keyId={}", resolvedKeyId)
            return cacheEntry.value
        }
        
        // Fetch fresh metadata
        val describeResponse = kmsClient.describeKey(
            DescribeKeyRequest.builder()
                .keyId(resolvedKeyId)
                .build()
        )
        val keyMetadata = describeResponse.keyMetadata()
        
        // Cache with TTL if configured
        val ttlSeconds = config.cacheTtlSeconds
        val cacheEntryNew = if (ttlSeconds != null) {
            CacheEntry.withTtlSeconds(keyMetadata, ttlSeconds)
        } else {
            CacheEntry.permanent(keyMetadata)
        }
        keyMetadataCache[resolvedKeyId] = cacheEntryNew
        
        logger.debug("Cached key metadata: keyId={}, ttlSeconds={}", resolvedKeyId, ttlSeconds)
        return keyMetadata
    }
    
    /**
     * Invalidates cache entry for a key.
     */
    private fun invalidateCache(resolvedKeyId: String) {
        keyMetadataCache.remove(resolvedKeyId)
        logger.debug("Invalidated cache for key: keyId={}", resolvedKeyId)
    }

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

        // Validate key ID if provided
        (options[KmsOptionKeys.KEY_ID] as? String)?.let { keyIdStr ->
            val validationError = KmsInputValidator.validateKeyId(keyIdStr)
            if (validationError != null) {
                logger.warn("Invalid key ID provided: keyId={}, error={}", keyIdStr, validationError)
                return@withContext GenerateKeyResult.Failure.InvalidOptions(
                    algorithm = algorithm,
                    reason = "Invalid key ID: $validationError",
                    invalidOptions = options
                )
            }
        }

        try {
            val keySpec = AlgorithmMapping.toAwsKeySpec(algorithm)

            val requestBuilder = CreateKeyRequest.builder()
                .keySpec(keySpec)
                .keyUsage(KeyUsageType.SIGN_VERIFY)

            // Add description if provided
            (options[KmsOptionKeys.DESCRIPTION] as? String)?.let {
                requestBuilder.description(it)
            }

            // Add tags if provided
            val tags = (options[KmsOptionKeys.TAGS] as? Map<*, *>)?.let { map ->
                map.entries.associate { (k, v) -> 
                    k.toString() to v.toString() 
                }
            }
            tags?.let {
                val tagList = it.map { (key, value) ->
                    Tag.builder().tagKey(key).tagValue(value).build()
                }
                requestBuilder.tags(tagList)
            }

            val createResponse = kmsClient.createKey(requestBuilder.build())
            val keyId = createResponse.keyMetadata().keyId()
            val keyArn = createResponse.keyMetadata().arn()

            // Enable automatic rotation if requested
            if (options[KmsOptionKeys.ENABLE_AUTOMATIC_ROTATION] == true) {
                try {
                    kmsClient.enableKeyRotation(
                        EnableKeyRotationRequest.builder()
                            .keyId(keyId)
                            .build()
                    )
                    logger.debug("Enabled automatic rotation for key: $keyId")
                } catch (e: Exception) {
                    // Log warning but don't fail key creation
                    // Automatic rotation may not be available for all key types
                    logger.warn("Failed to enable automatic rotation for key $keyId: ${e.message}", e)
                }
            }

            // Create alias if provided
            val alias = options[KmsOptionKeys.ALIAS] as? String
            alias?.let { aliasName ->
                try {
                    val aliasValue = if (aliasName.startsWith("alias/")) aliasName else "alias/$aliasName"
                    kmsClient.createAlias(
                        CreateAliasRequest.builder()
                            .aliasName(aliasValue)
                            .targetKeyId(keyId)
                            .build()
                    )
                    logger.debug("Created alias $aliasValue for key: $keyId")
                } catch (e: Exception) {
                    // If alias creation fails, continue with key ID
                    // The key is still usable by its ID/ARN
                    logger.warn("Failed to create alias $aliasName for key $keyId: ${e.message}", e)
                }
            }

            // Get public key to include in KeyHandle
            val publicKeyResponse = kmsClient.getPublicKey(
                GetPublicKeyRequest.builder()
                    .keyId(keyId)
                    .build()
            )

            val publicKeyBytes = publicKeyResponse.publicKey().asByteArray()
            val publicKeyJwk = AlgorithmMapping.publicKeyToJwk(publicKeyBytes, algorithm)

            GenerateKeyResult.Success(
                KeyHandle(
                    id = KeyId(keyArn ?: keyId), // Prefer ARN for full identification
                    algorithm = algorithm.name,
                    publicKeyJwk = publicKeyJwk
                )
            )
        } catch (e: AwsServiceException) {
            val errorCode = e.awsErrorDetails()?.errorCode()
            val requestId = e.requestId()
            logger.error("Failed to generate key", mapOf(
                "algorithm" to algorithm.name,
                "errorCode" to (errorCode ?: "unknown"),
                "requestId" to (requestId ?: "unknown"),
                "statusCode" to e.statusCode()
            ), e)
            
            when (errorCode) {
                "InvalidKeyUsageException" -> GenerateKeyResult.Failure.InvalidOptions(
                    algorithm = algorithm,
                    reason = "Invalid key usage: ${e.message ?: "Unknown error"}",
                    invalidOptions = options
                )
                else -> GenerateKeyResult.Failure.Error(
                    algorithm = algorithm,
                    reason = "Failed to generate key: ${e.message ?: "Unknown error"}",
                    cause = e
                )
            }
        } catch (e: Exception) {
            logger.error("Unexpected error during key generation", mapOf(
                "algorithm" to algorithm.name
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

            // Get key metadata to determine algorithm (use cache)
            val keyMetadata = getCachedKeyMetadata(resolvedKeyId)
            val keySpec = keyMetadata.keySpec()
            val algorithmName = keySpec.toString()
            val algorithm = parseAlgorithmFromKeySpec(algorithmName)
                ?: return@withContext GetPublicKeyResult.Failure.Error(
                    keyId = keyId,
                    reason = "Unknown key spec: $algorithmName"
                )

            // Get public key
            val publicKeyResponse = kmsClient.getPublicKey(
                GetPublicKeyRequest.builder()
                    .keyId(resolvedKeyId)
                    .build()
            )
            val publicKeyBytes = publicKeyResponse.publicKey().asByteArray()
            val publicKeyJwk = AlgorithmMapping.publicKeyToJwk(publicKeyBytes, algorithm)

            GetPublicKeyResult.Success(
                KeyHandle(
                    id = KeyId(keyMetadata.arn() ?: keyMetadata.keyId()),
                    algorithm = algorithm.name,
                    publicKeyJwk = publicKeyJwk
                )
            )
        } catch (e: AwsServiceException) {
            val requestId = e.requestId()
            if (e.statusCode() == 404 || e.awsErrorDetails()?.errorCode() == "NotFoundException") {
                logger.debug("Key not found: ${keyId.value}", mapOf(
                    "keyId" to keyId.value,
                    "requestId" to (requestId ?: "unknown")
                ))
                GetPublicKeyResult.Failure.KeyNotFound(keyId = keyId)
            } else {
                logger.error("Failed to get public key", mapOf(
                    "keyId" to keyId.value,
                    "errorCode" to (e.awsErrorDetails()?.errorCode() ?: "unknown"),
                    "requestId" to (requestId ?: "unknown"),
                    "statusCode" to e.statusCode()
                ), e)
                GetPublicKeyResult.Failure.Error(
                    keyId = keyId,
                    reason = "Failed to get public key: ${e.message ?: "Unknown error"}",
                    cause = e
                )
            }
        } catch (e: Exception) {
            logger.error("Unexpected error getting public key", mapOf(
                "keyId" to keyId.value
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

            // Get key metadata (use cache)
            val keyMetadata = getCachedKeyMetadata(resolvedKeyId)
            val keySpec = keyMetadata.keySpec().toString()
            val keyAlgorithm = parseAlgorithmFromKeySpec(keySpec)
                ?: return@withContext SignResult.Failure.Error(
                    keyId = keyId,
                    reason = "Cannot determine key algorithm for key: ${keyId.value}"
                )

            // Determine signing algorithm (use provided or key's default)
            val signingAlgorithm = algorithm ?: keyAlgorithm

            // Check if algorithm is compatible with key
            if (algorithm != null && !algorithm.isCompatibleWith(keyAlgorithm)) {
                logger.warn("Algorithm incompatibility detected", mapOf(
                    "keyId" to keyId.value,
                    "requestedAlgorithm" to algorithm.name,
                    "keyAlgorithm" to keyAlgorithm.name
                ))
                return@withContext SignResult.Failure.UnsupportedAlgorithm(
                    keyId = keyId,
                    requestedAlgorithm = algorithm,
                    keyAlgorithm = keyAlgorithm,
                    reason = "Algorithm '${algorithm.name}' is not compatible with key algorithm '${keyAlgorithm.name}'"
                )
            }

            val awsSigningAlgorithm = AlgorithmMapping.toAwsSigningAlgorithm(signingAlgorithm)

            val signRequest = SignRequest.builder()
                .keyId(resolvedKeyId)
                .message(SdkBytes.fromByteArray(data))
                .signingAlgorithm(awsSigningAlgorithm)
                .build()

            val signResponse = kmsClient.sign(signRequest)
            SignResult.Success(signResponse.signature().asByteArray())
        } catch (e: AwsServiceException) {
            val requestId = e.requestId()
            if (e.statusCode() == 404 || e.awsErrorDetails()?.errorCode() == "NotFoundException") {
                logger.debug("Key not found for signing: ${keyId.value}", mapOf(
                    "keyId" to keyId.value,
                    "requestId" to (requestId ?: "unknown")
                ))
                SignResult.Failure.KeyNotFound(keyId = keyId)
            } else {
                logger.error("Failed to sign data", mapOf(
                    "keyId" to keyId.value,
                    "errorCode" to (e.awsErrorDetails()?.errorCode() ?: "unknown"),
                    "requestId" to (requestId ?: "unknown"),
                    "statusCode" to e.statusCode()
                ), e)
                SignResult.Failure.Error(
                    keyId = keyId,
                    reason = "Failed to sign data: ${e.message ?: "Unknown error"}",
                    cause = e
                )
            }
        } catch (e: Exception) {
            logger.error("Unexpected error during signing", mapOf(
                "keyId" to keyId.value
            ), e)
            SignResult.Failure.Error(
                keyId = keyId,
                reason = "Failed to sign data: ${e.message ?: "Unknown error"}",
                cause = e
            )
        }
    }

    override suspend fun deleteKey(keyId: KeyId): DeleteKeyResult = withContext(Dispatchers.IO) {
        val resolvedKeyId = AlgorithmMapping.resolveKeyId(keyId.value)
        
        try {
            // Get pending window from config or use default
            val pendingWindowInDays = config.pendingWindowInDays ?: DEFAULT_PENDING_WINDOW_DAYS


            kmsClient.scheduleKeyDeletion(
                ScheduleKeyDeletionRequest.builder()
                    .keyId(resolvedKeyId)
                    .pendingWindowInDays(pendingWindowInDays)
                    .build()
            )

            logger.info("Scheduled key deletion", mapOf(
                "keyId" to keyId.value,
                "pendingWindowInDays" to pendingWindowInDays
            ))
            
            // Invalidate cache for deleted key
            invalidateCache(resolvedKeyId)

            DeleteKeyResult.Deleted
        } catch (e: AwsServiceException) {
            val requestId = e.requestId()
            if (e.statusCode() == 404 || e.awsErrorDetails()?.errorCode() == "NotFoundException") {
                logger.debug("Key not found for deletion (idempotent): ${keyId.value}", mapOf(
                    "keyId" to keyId.value,
                    "requestId" to (requestId ?: "unknown")
                ))
                // Invalidate cache even if key not found (cleanup)
                invalidateCache(resolvedKeyId)
                DeleteKeyResult.NotFound // Key doesn't exist (idempotent success)
            } else {
                logger.error("Failed to delete key", mapOf(
                    "keyId" to keyId.value,
                    "errorCode" to (e.awsErrorDetails()?.errorCode() ?: "unknown"),
                    "requestId" to (requestId ?: "unknown"),
                    "statusCode" to e.statusCode()
                ), e)
                DeleteKeyResult.Failure.Error(
                    keyId = keyId,
                    reason = "Failed to delete key: ${e.message ?: "Unknown error"}",
                    cause = e
                )
            }
        } catch (e: IllegalArgumentException) {
            // Re-throw validation errors
            throw e
        } catch (e: Exception) {
            logger.error("Unexpected error during key deletion", mapOf(
                "keyId" to keyId.value
            ), e)
            DeleteKeyResult.Failure.Error(
                keyId = keyId,
                reason = "Failed to delete key: ${e.message ?: "Unknown error"}",
                cause = e
            )
        }
    }

    /**
     * Parses algorithm from AWS KMS key spec string.
     */
    private fun parseAlgorithmFromKeySpec(keySpec: String): Algorithm? {
        return when (keySpec.uppercase()) {
            "ECC_ED25519" -> Algorithm.Ed25519
            "ECC_SECG_P256K1" -> Algorithm.Secp256k1
            "ECC_NIST_P256" -> Algorithm.P256
            "ECC_NIST_P384" -> Algorithm.P384
            "ECC_NIST_P521" -> Algorithm.P521
            "RSA_2048" -> Algorithm.RSA.RSA_2048
            "RSA_3072" -> Algorithm.RSA.RSA_3072
            "RSA_4096" -> Algorithm.RSA.RSA_4096
            else -> null
        }
    }


    override fun close() {
        kmsClient.close()
    }
}

