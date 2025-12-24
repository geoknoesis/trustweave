package org.trustweave.googlekms

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
import org.trustweave.core.plugin.PluginLifecycle
import com.google.api.gax.rpc.NotFoundException
import com.google.api.gax.rpc.PermissionDeniedException
import com.google.cloud.kms.v1.CryptoKey
import com.google.cloud.kms.v1.CryptoKey.CryptoKeyPurpose
import com.google.cloud.kms.v1.CryptoKeyVersion
import com.google.cloud.kms.v1.CryptoKeyVersion.CryptoKeyVersionState
import com.google.cloud.kms.v1.CryptoKeyVersionName
import com.google.cloud.kms.v1.CryptoKeyVersionTemplate
import com.google.cloud.kms.v1.KeyManagementServiceClient
import com.google.cloud.kms.v1.KeyRingName
import com.google.protobuf.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.trustweave.kms.util.CacheEntry
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Google Cloud KMS implementation of KeyManagementService.
 *
 * Supports all Google Cloud KMS-compatible algorithms:
 * - Ed25519 (EC_SIGN_ED25519)
 * - secp256k1 (EC_SIGN_SECP256K1_SHA256)
 * - P-256, P-384, P-521 (EC_SIGN_P256_SHA256, EC_SIGN_P384_SHA384, EC_SIGN_P521_SHA512)
 * - RSA-2048, RSA-3072, RSA-4096 (RSA_SIGN_PKCS1_2048_SHA256, etc.)
 *
 * **Example:**
 * ```kotlin
 * val config = GoogleKmsConfig.builder()
 *     .projectId("my-project")
 *     .location("us-east1")
 *     .keyRing("my-key-ring")
 *     .build()
 * val kms = GoogleCloudKeyManagementService(config)
 * val key = kms.generateKey(Algorithm.Ed25519)
 * ```
 */
class GoogleCloudKeyManagementService(
    private val config: GoogleKmsConfig,
    private val kmsClient: KeyManagementServiceClient = GoogleKmsClientFactory.createClient(config)
) : KeyManagementService, PluginLifecycle, AutoCloseable {

    private val logger = LoggerFactory.getLogger(GoogleCloudKeyManagementService::class.java)
    
    // Cache for key metadata to avoid duplicate getCryptoKey calls (with TTL support)
    private val keyMetadataCache = ConcurrentHashMap<String, CacheEntry<CryptoKey>>()
    
    /**
     * Gets cached key metadata or fetches it if not cached or expired.
     */
    private suspend fun getCachedKeyMetadata(resolvedKeyName: String): CryptoKey {
        val cacheEntry = keyMetadataCache[resolvedKeyName]
        
        // Check if cache entry exists and is not expired
        if (cacheEntry != null && !cacheEntry.isExpired()) {
            logger.debug("Using cached key metadata: keyName={}", resolvedKeyName)
            return cacheEntry.value
        }
        
        // Fetch fresh metadata
        val cryptoKey = kmsClient.getCryptoKey(
            com.google.cloud.kms.v1.GetCryptoKeyRequest.newBuilder()
                .setName(resolvedKeyName)
                .build()
        )
        
        // Cache with TTL if configured
        val ttlSeconds = config.cacheTtlSeconds
        val cacheEntryNew = if (ttlSeconds != null) {
            CacheEntry.withTtlSeconds(cryptoKey, ttlSeconds)
        } else {
            CacheEntry.permanent(cryptoKey)
        }
        keyMetadataCache[resolvedKeyName] = cacheEntryNew
        
        logger.debug("Cached key metadata: keyName={}, ttlSeconds={}", resolvedKeyName, ttlSeconds)
        return cryptoKey
    }
    
    /**
     * Invalidates cache entry for a key.
     */
    private fun invalidateCache(resolvedKeyName: String) {
        keyMetadataCache.remove(resolvedKeyName)
        logger.debug("Invalidated cache for key: keyName={}", resolvedKeyName)
    }

    companion object {
        /**
         * Algorithms supported by Google Cloud KMS.
         * Note: Ed25519 and P521 support may vary by Google Cloud KMS version.
         */
        val SUPPORTED_ALGORITHMS = setOf(
            Algorithm.Ed25519, // Note: may not be available in all Google Cloud KMS versions
            Algorithm.Secp256k1,
            Algorithm.P256,
            Algorithm.P384,
            Algorithm.P521, // Note: may not be available in all Google Cloud KMS versions
            Algorithm.RSA.RSA_2048,
            Algorithm.RSA.RSA_3072,
            Algorithm.RSA.RSA_4096
        )
    }

    override suspend fun getSupportedAlgorithms(): Set<Algorithm> = SUPPORTED_ALGORITHMS

    override suspend fun initialize(config: Map<String, Any?>): Boolean {
        // Google Cloud KMS client is already initialized
        // Could verify connection here if needed
        return true
    }

    override suspend fun start(): Boolean {
        // No startup needed for Google Cloud KMS
        return true
    }

    override suspend fun stop(): Boolean {
        // No stop needed for Google Cloud KMS
        return true
    }

    override suspend fun cleanup() {
        close()
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

        // Validate key ring name if provided
        val keyRing = (options[KmsOptionKeys.KEY_RING] as? String) ?: config.keyRing
        if (keyRing == null) {
            return@withContext GenerateKeyResult.Failure.InvalidOptions(
                algorithm = algorithm,
                reason = "Key ring must be specified in config or options",
                invalidOptions = options
            )
        }

        try {

            val keyId = (options[KmsOptionKeys.KEY_ID] as? String) ?: generateKeyId()
            val keyRingName = KeyRingName.of(config.projectId, config.location, keyRing)

            val googleKmsAlgorithm = AlgorithmMapping.toGoogleKmsAlgorithm(algorithm)

            // Create CryptoKeyVersionTemplate
            val versionTemplate = CryptoKeyVersionTemplate.newBuilder()
                .setAlgorithm(googleKmsAlgorithm)
                .build()

            // Create CryptoKey builder
            val cryptoKeyBuilder = CryptoKey.newBuilder()
                .setPurpose(CryptoKeyPurpose.ASYMMETRIC_SIGN)
                .setVersionTemplate(versionTemplate)

            // Add labels if provided
            val labels = (options[KmsOptionKeys.LABELS] as? Map<*, *>)?.let { map ->
                map.entries.associate { (k, v) -> 
                    k.toString() to v.toString() 
                }
            }
            labels?.forEach { (key, value) ->
                cryptoKeyBuilder.putLabels(key, value)
            }

            val createRequest = com.google.cloud.kms.v1.CreateCryptoKeyRequest.newBuilder()
                .setParent(keyRingName.toString())
                .setCryptoKeyId(keyId)
                .setCryptoKey(cryptoKeyBuilder)
                .build()

            val createdKey = kmsClient.createCryptoKey(createRequest)
            val keyResourceName = createdKey.name

            // Get the primary version to retrieve public key
            val primaryVersionName = createdKey.primary.name
            val publicKeyResponse = kmsClient.getPublicKey(
                com.google.cloud.kms.v1.GetPublicKeyRequest.newBuilder()
                    .setName(primaryVersionName)
                    .build()
            )

            // Google Cloud KMS returns PEM format, need to convert to DER
            val derBytes = convertPemToDer(publicKeyResponse.pem)
            val publicKeyJwk = AlgorithmMapping.publicKeyToJwk(derBytes, algorithm)

            logger.info("Successfully generated key in Google Cloud KMS: algorithm={}, keyId={}, projectId={}, location={}", 
                algorithm.name, keyResourceName, config.projectId, config.location)
            
            GenerateKeyResult.Success(
                KeyHandle(
                    id = KeyId(keyResourceName),
                    algorithm = algorithm.name,
                    publicKeyJwk = publicKeyJwk
                )
            )
        } catch (e: com.google.api.gax.rpc.AlreadyExistsException) {
            logger.warn("Key already exists in Google Cloud KMS: algorithm={}, projectId={}, location={}", 
                algorithm.name, config.projectId, config.location, e)
            GenerateKeyResult.Failure.InvalidOptions(
                algorithm = algorithm,
                reason = "Key already exists: ${e.message ?: "Unknown error"}",
                invalidOptions = options
            )
        } catch (e: PermissionDeniedException) {
            logger.error("Permission denied when generating key in Google Cloud KMS: algorithm={}, projectId={}, location={}", 
                algorithm.name, config.projectId, config.location, e)
            GenerateKeyResult.Failure.Error(
                algorithm = algorithm,
                reason = "Permission denied to Google Cloud KMS. Check IAM permissions: ${e.message ?: "Unknown error"}",
                cause = e
            )
        } catch (e: Exception) {
            logger.error("Unexpected error during key generation in Google Cloud KMS: algorithm={}, projectId={}, location={}", 
                algorithm.name, config.projectId, config.location, e)
            GenerateKeyResult.Failure.Error(
                algorithm = algorithm,
                reason = "Failed to generate key: ${e.message ?: "Unknown error"}",
                cause = e
            )
        }
    }

    override suspend fun getPublicKey(keyId: KeyId): GetPublicKeyResult = withContext(Dispatchers.IO) {
        val resolvedKeyName = AlgorithmMapping.resolveKeyName(keyId.value, config)
        
        try {
            // Get the key to find the primary version (use cache)
            val cryptoKey = getCachedKeyMetadata(resolvedKeyName)

            val primaryVersionName = cryptoKey.primary.name
            val publicKeyResponse = kmsClient.getPublicKey(
                com.google.cloud.kms.v1.GetPublicKeyRequest.newBuilder()
                    .setName(primaryVersionName)
                    .build()
            )

            val algorithm = AlgorithmMapping.fromGoogleKmsAlgorithm(publicKeyResponse.algorithm)
                ?: return@withContext GetPublicKeyResult.Failure.Error(
                    keyId = keyId,
                    reason = "Unknown algorithm: ${publicKeyResponse.algorithm}"
                )

            val derBytes = convertPemToDer(publicKeyResponse.pem)
            val publicKeyJwk = AlgorithmMapping.publicKeyToJwk(derBytes, algorithm)

            GetPublicKeyResult.Success(
                KeyHandle(
                    id = KeyId(resolvedKeyName),
                    algorithm = algorithm.name,
                    publicKeyJwk = publicKeyJwk
                )
            )
        } catch (e: NotFoundException) {
            logger.debug("Key not found in Google Cloud KMS: keyId={}, resolvedKeyName={}", keyId.value, resolvedKeyName)
            GetPublicKeyResult.Failure.KeyNotFound(keyId = keyId)
        } catch (e: PermissionDeniedException) {
            logger.error("Permission denied when getting public key from Google Cloud KMS: keyId={}, resolvedKeyName={}", keyId.value, resolvedKeyName, e)
            GetPublicKeyResult.Failure.Error(
                keyId = keyId,
                reason = "Permission denied to Google Cloud KMS. Check IAM permissions: ${e.message ?: "Unknown error"}",
                cause = e
            )
        } catch (e: Exception) {
            logger.error("Unexpected error getting public key from Google Cloud KMS: keyId={}, resolvedKeyName={}", keyId.value, resolvedKeyName, e)
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
            val resolvedKeyName = AlgorithmMapping.resolveKeyName(keyId.value, config)

            // Get the key to find the primary version (use cache)
            val cryptoKey = getCachedKeyMetadata(resolvedKeyName)

            val primaryVersionName = cryptoKey.primary.name

            // Get key algorithm
            val publicKeyResponse = kmsClient.getPublicKey(
                com.google.cloud.kms.v1.GetPublicKeyRequest.newBuilder()
                    .setName(primaryVersionName)
                    .build()
            )
            val keyAlgorithm = AlgorithmMapping.fromGoogleKmsAlgorithm(publicKeyResponse.algorithm)
                ?: return@withContext SignResult.Failure.Error(
                    keyId = keyId,
                    reason = "Cannot determine key algorithm for key: ${keyId.value}"
                )

            // Check if algorithm is compatible with key using the standard method
            if (algorithm != null && !algorithm.isCompatibleWith(keyAlgorithm)) {
                logger.warn("Algorithm incompatibility detected: keyId={}, requestedAlgorithm={}, keyAlgorithm={}", 
                    keyId.value, algorithm.name, keyAlgorithm.name)
                return@withContext SignResult.Failure.UnsupportedAlgorithm(
                    keyId = keyId,
                    requestedAlgorithm = algorithm,
                    keyAlgorithm = keyAlgorithm,
                    reason = "Algorithm '${algorithm.name}' is not compatible with key algorithm '${keyAlgorithm.name}'"
                )
            }

            // Google Cloud KMS determines the algorithm from the key version, so no need to specify it
            val signRequest = com.google.cloud.kms.v1.AsymmetricSignRequest.newBuilder()
                .setName(primaryVersionName)
                .setData(ByteString.copyFrom(data))
                .build()

            val signResponse = kmsClient.asymmetricSign(signRequest)
            SignResult.Success(signResponse.signature.toByteArray())
        } catch (e: NotFoundException) {
            logger.debug("Key not found in Google Cloud KMS during signing: keyId={}", keyId.value)
            SignResult.Failure.KeyNotFound(keyId = keyId)
        } catch (e: PermissionDeniedException) {
            logger.error("Permission denied when signing with Google Cloud KMS: keyId={}", keyId.value, e)
            SignResult.Failure.Error(
                keyId = keyId,
                reason = "Permission denied to Google Cloud KMS. Check IAM permissions: ${e.message ?: "Unknown error"}",
                cause = e
            )
        } catch (e: Exception) {
            logger.error("Unexpected error during signing with Google Cloud KMS: keyId={}", keyId.value, e)
            SignResult.Failure.Error(
                keyId = keyId,
                reason = "Failed to sign data: ${e.message ?: "Unknown error"}",
                cause = e
            )
        }
    }

    override suspend fun deleteKey(keyId: KeyId): DeleteKeyResult = withContext(Dispatchers.IO) {
        val resolvedKeyName = AlgorithmMapping.resolveKeyName(keyId.value, config)
        
        try {

            // Get the key to find the primary version
            val cryptoKey = kmsClient.getCryptoKey(
                com.google.cloud.kms.v1.GetCryptoKeyRequest.newBuilder()
                    .setName(resolvedKeyName)
                    .build()
            )

            val primaryVersionName = cryptoKey.primary.name

            // Schedule key version destruction (30 day waiting period by default)
            val destroyRequest = com.google.cloud.kms.v1.DestroyCryptoKeyVersionRequest.newBuilder()
                .setName(primaryVersionName)
                .build()

            // Note: Google Cloud KMS doesn't have a direct delete for CryptoKey
            // We destroy the primary version, which effectively makes the key unusable
            // For full deletion, use scheduleDestroyCryptoKeyVersion with a schedule
            kmsClient.destroyCryptoKeyVersion(destroyRequest)
            
            // Invalidate cache
            invalidateCache(resolvedKeyName)
            
            logger.info("Successfully deleted key in Google Cloud KMS: keyId={}, resolvedKeyName={}", keyId.value, resolvedKeyName)

            DeleteKeyResult.Deleted
        } catch (e: NotFoundException) {
            // Invalidate cache even if key not found (cleanup)
            invalidateCache(resolvedKeyName)
            logger.debug("Key not found in Google Cloud KMS during deletion: keyId={}", keyId.value)
            DeleteKeyResult.NotFound // Key doesn't exist (idempotent success)
        } catch (e: PermissionDeniedException) {
            logger.error("Permission denied when deleting key in Google Cloud KMS: keyId={}, resolvedKeyName={}", 
                keyId.value, resolvedKeyName, e)
            DeleteKeyResult.Failure.Error(
                keyId = keyId,
                reason = "Permission denied to Google Cloud KMS. Check IAM permissions: ${e.message ?: "Unknown error"}",
                cause = e
            )
        } catch (e: Exception) {
            logger.error("Unexpected error during key deletion in Google Cloud KMS: keyId={}, resolvedKeyName={}", 
                keyId.value, resolvedKeyName, e)
            DeleteKeyResult.Failure.Error(
                keyId = keyId,
                reason = "Failed to delete key: ${e.message ?: "Unknown error"}",
                cause = e
            )
        }
    }

    /**
     * Generates a unique key ID.
     */
    private fun generateKeyId(): String {
        return "key-${System.currentTimeMillis()}-${(0..9999).random()}"
    }

    /**
     * Converts PEM format to DER format.
     * Google Cloud KMS returns public keys in PEM format, but we need DER for JWK conversion.
     */
    private fun convertPemToDer(pem: String): ByteArray {
        // Remove PEM headers and whitespace
        val base64 = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("-----BEGIN RSA PUBLIC KEY-----", "")
            .replace("-----END RSA PUBLIC KEY-----", "")
            .replace("\n", "")
            .replace("\r", "")
            .replace(" ", "")

        return Base64.getDecoder().decode(base64)
    }

    override fun close() {
        kmsClient.close()
    }
}

