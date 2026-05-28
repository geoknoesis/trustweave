package org.trustweave.azurekms

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import com.azure.security.keyvault.keys.KeyClient
import com.azure.security.keyvault.keys.models.CreateEcKeyOptions
import com.azure.security.keyvault.keys.models.CreateKeyOptions
import com.azure.security.keyvault.keys.models.CreateRsaKeyOptions
import com.azure.security.keyvault.keys.models.KeyCurveName
import com.azure.security.keyvault.keys.models.KeyType
import com.azure.security.keyvault.keys.cryptography.CryptographyClient
import com.azure.security.keyvault.keys.cryptography.CryptographyClientBuilder
import com.azure.core.exception.ResourceNotFoundException
import com.azure.core.exception.HttpResponseException
import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.RSAPublicKeySpec
import java.util.UUID

/**
 * Azure Key Vault implementation of KeyManagementService.
 *
 * Supports Azure Key Vault-compatible algorithms:
 * - secp256k1 (P-256K)
 * - P-256, P-384, P-521 (P-256, P-384, P-521)
 * - RSA-2048, RSA-3072, RSA-4096
 *
 * Note: Ed25519 is not directly supported by Azure Key Vault.
 *
 * **Example:**
 * ```kotlin
 * val config = AzureKmsConfig.builder()
 *     .vaultUrl("https://myvault.vault.azure.net")
 *     .build()
 * val kms = AzureKeyManagementService(config)
 * val key = kms.generateKey(Algorithm.P256)
 * ```
 */
class AzureKeyManagementService(
    private val config: AzureKmsConfig,
    private val keyClient: KeyClient = AzureKmsClientFactory.createClient(config)
) : KeyManagementService, AutoCloseable {

    companion object {
        /**
         * Algorithms supported by Azure Key Vault.
         * Note: Ed25519 is not supported by Azure Key Vault.
         */
        val SUPPORTED_ALGORITHMS = setOf(
            Algorithm.Secp256k1,
            Algorithm.P256,
            Algorithm.P384,
            Algorithm.P521,
            Algorithm.RSA.RSA_2048,
            Algorithm.RSA.RSA_3072,
            Algorithm.RSA.RSA_4096
        )
    }

    private val logger = LoggerFactory.getLogger(AzureKeyManagementService::class.java)
    private val cryptoClientCache = java.util.concurrent.ConcurrentHashMap<String, CryptographyClient>()
    // Cache for resolved key algorithms to avoid re-fetching key metadata on every sign() call.
    // Invalidated by clearCryptoClientCache() when a key rotation is performed.
    private val keyAlgorithmCache = java.util.concurrent.ConcurrentHashMap<String, Algorithm>()
    // LazyThreadSafetyMode.SYNCHRONIZED (default) — required: concurrent sign() calls race to initialise this credential
    private val azureCredential: com.azure.core.credential.TokenCredential by lazy {
        if (config.clientId != null && config.clientSecret != null && config.tenantId != null) {
            com.azure.identity.ClientSecretCredentialBuilder()
                .clientId(config.clientId)
                .clientSecret(config.clientSecret)
                .tenantId(config.tenantId)
                .build()
        } else {
            com.azure.identity.DefaultAzureCredentialBuilder().build()
        }
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
        (options[KmsOptionKeys.KEY_NAME] as? String)?.let { keyName ->
            val validationError = KmsInputValidator.validateKeyId(keyName)
            if (validationError != null) {
                logger.warn("Invalid key name provided: keyName={}, error={}", keyName, validationError)
                return@withContext GenerateKeyResult.Failure.InvalidOptions(
                    algorithm = algorithm,
                    reason = "Invalid key name: $validationError",
                    invalidOptions = options
                )
            }
        }

        try {
            val (keyType, curveName) = AlgorithmMapping.toAzureKeyType(algorithm)

            val keyName = (options[KmsOptionKeys.KEY_NAME] as? String) ?: "TrustWeave-key-${UUID.randomUUID()}"

            // Create key options - Azure SDK CreateKeyOptions
            // Note: The Azure SDK CreateKeyOptions API may vary by version
            // For EC keys, the curve should be specified, but the exact method name may differ
            // This implementation creates keys with basic options; curve and operations
            // can be configured via Azure Key Vault defaults or enhanced with specific SDK version methods
            val keyVaultKey = if (keyType == KeyType.EC && curveName != null) {
                val ecOptions = CreateEcKeyOptions(keyName).setCurveName(curveName)
                runInterruptible { keyClient.createEcKey(ecOptions) }
            } else if (keyType == KeyType.RSA && algorithm is Algorithm.RSA) {
                // Use CreateRsaKeyOptions so the requested key size is passed explicitly;
                // the generic CreateKeyOptions path silently ignores the key size.
                val rsaOptions = CreateRsaKeyOptions(keyName).setKeySize(algorithm.keySize)
                runInterruptible { keyClient.createRsaKey(rsaOptions) }
            } else {
                runInterruptible { keyClient.createKey(CreateKeyOptions(keyName, keyType)) }
            }
            val keyId = keyVaultKey.id

            // Get the public key from the key material
            val publicKeyBytes = when {
                keyVaultKey.key is com.azure.security.keyvault.keys.models.JsonWebKey -> {
                    val jwk = keyVaultKey.key as com.azure.security.keyvault.keys.models.JsonWebKey
                    // Extract public key bytes from JWK
                    extractPublicKeyBytesFromJwk(jwk, algorithm)
                }
                else -> {
                    // Fallback: try to get public key via cryptography client
                    // Note: Azure SDK doesn't provide a direct way to get raw public key bytes
                    // We'll construct it from the JWK parameters
                    extractPublicKeyBytesFromKeyVaultKey(keyVaultKey, algorithm)
                }
            }

            val publicKeyJwk = AlgorithmMapping.publicKeyToJwk(publicKeyBytes, algorithm)

            GenerateKeyResult.Success(
                KeyHandle(
                    id = KeyId(keyId ?: keyName), // Use key ID (includes version) or fallback to name
                    algorithm = algorithm.name,
                    publicKeyJwk = publicKeyJwk
                )
            )
        } catch (e: HttpResponseException) {
            logger.error("Failed to generate key in Azure Key Vault", mapOf(
                "algorithm" to algorithm.name,
                "statusCode" to (e.response?.statusCode ?: "unknown"),
                "vaultUrl" to config.vaultUrl
            ), e)

            when (e.response?.statusCode) {
                400, 403 -> GenerateKeyResult.Failure.InvalidOptions(
                    algorithm = algorithm,
                    reason = "Failed to generate key: ${e.message ?: "Unknown error"}",
                    invalidOptions = options
                )
                else -> GenerateKeyResult.Failure.Error(
                    algorithm = algorithm,
                    reason = "Failed to generate key: ${e.message ?: "Unknown error"}",
                    cause = e
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Unexpected error during key generation in Azure Key Vault", mapOf(
                "algorithm" to algorithm.name,
                "vaultUrl" to config.vaultUrl
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
            val keyVaultKey = runInterruptible { keyClient.getKey(resolvedKeyId) }

            val keyType = keyVaultKey.keyType
            val curveName = keyVaultKey.key?.curveName
            val keySize = when (keyType) {
                KeyType.RSA -> {
                    // For RSA keys, get the key size from the key material.
                    // Azure may omit a leading 0x00 byte from the modulus, making n.size * 8
                    // slightly under the true bit-length (e.g. 2040 instead of 2048). Round up
                    // to the nearest standard RSA key size so parseAlgorithmFromKeyType succeeds.
                    val jwk = keyVaultKey.key
                    jwk?.n?.size?.let { nSize ->
                        val bitLength = nSize * 8
                        when {
                            bitLength <= 2048 -> 2048
                            bitLength <= 3072 -> 3072
                            bitLength <= 4096 -> 4096
                            else -> bitLength
                        }
                    }
                }
                else -> null
            }

            val algorithm = AlgorithmMapping.parseAlgorithmFromKeyType(keyType, curveName, keySize)
                ?: return@withContext GetPublicKeyResult.Failure.Error(
                    keyId = keyId,
                    reason = "Unknown key type: $keyType"
                )

            val publicKeyBytes = extractPublicKeyBytesFromKeyVaultKey(keyVaultKey, algorithm)

            val publicKeyJwk = AlgorithmMapping.publicKeyToJwk(publicKeyBytes, algorithm)

            GetPublicKeyResult.Success(
                KeyHandle(
                    id = KeyId(keyVaultKey.id ?: resolvedKeyId),
                    algorithm = algorithm.name,
                    publicKeyJwk = publicKeyJwk
                )
            )
        } catch (e: ResourceNotFoundException) {
            logger.debug("Key not found in Azure Key Vault: ${keyId.value}", mapOf(
                "keyId" to keyId.value,
                "vaultUrl" to config.vaultUrl
            ))
            GetPublicKeyResult.Failure.KeyNotFound(keyId = keyId)
        } catch (e: HttpResponseException) {
            if (e.response?.statusCode == 404) {
                logger.debug("Key not found in Azure Key Vault: ${keyId.value}", mapOf(
                    "keyId" to keyId.value,
                    "vaultUrl" to config.vaultUrl
                ))
                GetPublicKeyResult.Failure.KeyNotFound(keyId = keyId)
            } else {
                logger.error("Failed to get public key from Azure Key Vault", mapOf(
                    "keyId" to keyId.value,
                    "statusCode" to (e.response?.statusCode ?: "unknown"),
                    "vaultUrl" to config.vaultUrl
                ), e)
                GetPublicKeyResult.Failure.Error(
                    keyId = keyId,
                    reason = "Failed to get public key: ${e.message ?: "Unknown error"}",
                    cause = e
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Unexpected error getting public key from Azure Key Vault", mapOf(
                "keyId" to keyId.value,
                "vaultUrl" to config.vaultUrl
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
        // Azure SDK requires a pre-hashed digest; prehashForAzure() computes the hash before signing.
        // Validate input data
        val dataValidationError = KmsInputValidator.validateSignData(data)
        if (dataValidationError != null) {
            logger.warn("Invalid data for signing: keyId={}, error={}", keyId.value, dataValidationError)
            return@withContext SignResult.Failure.Error(
                keyId = keyId,
                reason = dataValidationError
            )
        }

        val resolvedKeyId = AlgorithmMapping.resolveKeyId(keyId.value)

        try {
            // Resolve algorithm from cache; fetch from Azure only on first use or after rotation.
            // computeIfAbsent is atomic for the *store* step, but its factory lambda is a plain
            // Java function and cannot call suspend functions such as runInterruptible.
            // We therefore use a fetch-then-putIfAbsent pattern:
            //   1. Fast path: return cached value if present (no I/O).
            //   2. Slow path: fetch from Azure (blocking, wrapped in runInterruptible) and
            //      putIfAbsent to store it.  If a concurrent thread already stored a value,
            //      putIfAbsent returns their entry and we discard the extra fetch; only one
            //      value is ever visible in the map, so there is no inconsistency.
            val keyAlgorithm = keyAlgorithmCache[resolvedKeyId] ?: run {
                val keyVaultKey = runInterruptible { keyClient.getKey(resolvedKeyId) }
                val keyType = keyVaultKey.keyType
                val curveName = keyVaultKey.key?.curveName
                val keySize = when (keyType) {
                    KeyType.RSA -> {
                        // Round up to the nearest standard size: Azure may omit the leading 0x00
                        // sign byte, making n.size * 8 slightly under the true bit-length.
                        val jwk = keyVaultKey.key
                        jwk?.n?.size?.let { nSize ->
                            val bitLength = nSize * 8
                            when {
                                bitLength <= 2048 -> 2048
                                bitLength <= 3072 -> 3072
                                bitLength <= 4096 -> 4096
                                else -> bitLength
                            }
                        }
                    }
                    else -> null
                }
                val resolved = AlgorithmMapping.parseAlgorithmFromKeyType(keyType, curveName, keySize)
                    ?: throw IllegalStateException("Cannot determine key algorithm for key: ${keyId.value}")
                // putIfAbsent is atomic: if another thread already stored a value we use theirs.
                keyAlgorithmCache.putIfAbsent(resolvedKeyId, resolved) ?: resolved
            }

            val signingAlgorithm = algorithm ?: keyAlgorithm

            // Check if algorithm is compatible with key
            if (algorithm != null && !isAlgorithmCompatible(algorithm, keyAlgorithm)) {
                return@withContext SignResult.Failure.UnsupportedAlgorithm(
                    keyId = keyId,
                    requestedAlgorithm = algorithm,
                    keyAlgorithm = keyAlgorithm,
                    reason = "Algorithm '${algorithm.name}' is not compatible with key algorithm '${keyAlgorithm.name}'"
                )
            }

            val azureSignatureAlgorithm = AlgorithmMapping.toAzureSignatureAlgorithm(signingAlgorithm)

            // Create or reuse cached cryptography client for signing.
            // Cache key format: "<vaultUrl>/<resolvedKeyId>".
            // - Include the vault URL so that same-named keys in different vaults don't collide.
            // - To sign with a specific key version, callers should pass the versioned key ID
            //   (e.g. "keyname/<version-hex>") so that the version is part of resolvedKeyId and
            //   therefore part of the cache key.
            // - After a key rotation the cached client continues using the old key version until
            //   clearCryptoClientCache(keyId) is called. Call that method whenever a rotation
            //   is performed to force the next sign() to build a fresh client.
            val cacheKey = "${config.vaultUrl}/$resolvedKeyId"
            val cryptographyClient = cryptoClientCache[cacheKey] ?: run {
                val client = runInterruptible {
                    CryptographyClientBuilder()
                        .keyIdentifier(resolvedKeyId)
                        .credential(azureCredential)
                        .buildClient()
                }
                cryptoClientCache.putIfAbsent(cacheKey, client) ?: client
            }

            val digest = prehashForAzure(data, azureSignatureAlgorithm)
            val signResult = runInterruptible { cryptographyClient.sign(azureSignatureAlgorithm, digest) }
            SignResult.Success(signResult.signature)
        } catch (e: ResourceNotFoundException) {
            logger.debug("Key not found for signing in Azure Key Vault: ${keyId.value}", mapOf(
                "keyId" to keyId.value,
                "vaultUrl" to config.vaultUrl
            ))
            SignResult.Failure.KeyNotFound(keyId = keyId)
        } catch (e: HttpResponseException) {
            if (e.response?.statusCode == 404) {
                logger.debug("Key not found for signing in Azure Key Vault: ${keyId.value}", mapOf(
                    "keyId" to keyId.value,
                    "vaultUrl" to config.vaultUrl
                ))
                return@withContext SignResult.Failure.KeyNotFound(keyId = keyId)
            }
            if (e.response?.statusCode == 400) {
                // Algorithm mismatch or bad request — evict stale caches so next call re-fetches.
                // resolvedKeyId is already computed at the top of the try block.
                clearCryptoClientCache(resolvedKeyId)
                return@withContext SignResult.Failure.Error(
                    keyId = keyId,
                    reason = "Azure signing failed (HTTP 400 — possible algorithm/key mismatch after rotation): ${e.message}",
                    cause = e
                )
            }
            logger.error("Failed to sign data in Azure Key Vault", mapOf(
                "keyId" to keyId.value,
                "statusCode" to (e.response?.statusCode ?: "unknown"),
                "vaultUrl" to config.vaultUrl
            ), e)
            SignResult.Failure.Error(
                keyId = keyId,
                reason = "Azure signing failed (HTTP ${e.response?.statusCode}): ${e.message}",
                cause = e
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Unexpected error during signing in Azure Key Vault", mapOf(
                "keyId" to keyId.value,
                "vaultUrl" to config.vaultUrl
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

            // Azure Key Vault supports soft delete and purge
            // We'll use beginDeleteKey which schedules deletion (soft delete)
            val deleteKeyOperation = runInterruptible { keyClient.beginDeleteKey(resolvedKeyId) }
            // withTimeout bounds the maximum blocking time so coroutine cancellation
            // can always propagate even if the Azure poller ignores thread interrupts.
            withTimeout(60_000L) {
                runInterruptible {
                    deleteKeyOperation.waitForCompletion()
                }
            }

            // Evict crypto-client and algorithm caches so stale entries cannot be used
            // for signing after the key has been deleted.
            clearCryptoClientCache(resolvedKeyId)

            // Optionally purge the key (hard delete) if requested
            // For now, we'll just soft delete
            DeleteKeyResult.Deleted
        } catch (e: ResourceNotFoundException) {
            logger.debug("Key not found for deletion in Azure Key Vault (idempotent): ${keyId.value}", mapOf(
                "keyId" to keyId.value,
                "vaultUrl" to config.vaultUrl
            ))
            DeleteKeyResult.NotFound // Key doesn't exist (idempotent success)
        } catch (e: HttpResponseException) {
            if (e.response?.statusCode == 404) {
                logger.debug("Key not found for deletion in Azure Key Vault (idempotent): ${keyId.value}", mapOf(
                    "keyId" to keyId.value,
                    "vaultUrl" to config.vaultUrl
                ))
                DeleteKeyResult.NotFound
            } else {
                logger.error("Failed to delete key in Azure Key Vault", mapOf(
                    "keyId" to keyId.value,
                    "statusCode" to (e.response?.statusCode ?: "unknown"),
                    "vaultUrl" to config.vaultUrl
                ), e)
                DeleteKeyResult.Failure.Error(
                    keyId = keyId,
                    reason = "Failed to delete key: ${e.message ?: "Unknown error"}",
                    cause = e
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Unexpected error during key deletion in Azure Key Vault", mapOf(
                "keyId" to keyId.value,
                "vaultUrl" to config.vaultUrl
            ), e)
            DeleteKeyResult.Failure.Error(
                keyId = keyId,
                reason = "Failed to delete key: ${e.message ?: "Unknown error"}",
                cause = e
            )
        }
    }

    /**
     * Checks if two algorithms are compatible for signing.
     */
    private fun isAlgorithmCompatible(requested: Algorithm, key: Algorithm): Boolean {
        // Same algorithm is always compatible
        if (requested == key) return true
        
        // For RSA, key sizes must match — different sizes use different hash algorithms
        if (requested is Algorithm.RSA && key is Algorithm.RSA) return requested.keySize == key.keySize
        
        // For ECC, algorithms must match exactly
        return false
    }

    /**
     * Extracts public key bytes from Azure Key Vault key.
     */
    private fun extractPublicKeyBytesFromKeyVaultKey(
        keyVaultKey: com.azure.security.keyvault.keys.models.KeyVaultKey,
        algorithm: Algorithm
    ): ByteArray {
        val jwk = keyVaultKey.key
        return extractPublicKeyBytesFromJwk(jwk, algorithm)
    }

    /**
     * Extracts public key bytes from Azure Key Vault JWK.
     */
    private fun extractPublicKeyBytesFromJwk(
        jwk: com.azure.security.keyvault.keys.models.JsonWebKey,
        algorithm: Algorithm
    ): ByteArray {
        // Azure Key Vault returns keys in JWK format
        // We need to reconstruct the public key bytes from JWK parameters
        return when (algorithm) {
            is Algorithm.Secp256k1, is Algorithm.P256, is Algorithm.P384, is Algorithm.P521 -> {
                // For EC keys, we need x and y coordinates
                val x = jwk.x
                val y = jwk.y
                if (x == null || y == null) {
                    throw TrustWeaveException.Unknown(
                        message = "Missing x or y coordinates in EC key"
                    )
                }
                // Reconstruct the public key in DER format
                // This is a simplified approach - in production, you'd want to use a proper EC key factory
                val coordinateLength = when (algorithm) {
                    is Algorithm.Secp256k1, is Algorithm.P256 -> 32
                    is Algorithm.P384 -> 48
                    is Algorithm.P521 -> 66
                    else -> 32
                }
                // Build uncompressed EC point: 0x04 || x (right-aligned) || y (right-aligned)
                // Azure JWK byte arrays may be shorter than coordinateLength when leading bytes are 0
                val result = ByteArray(1 + coordinateLength * 2)
                result[0] = 0x04 // Uncompressed point format
                val xOffset = 1 + maxOf(0, coordinateLength - x.size)
                val yOffset = 1 + coordinateLength + maxOf(0, coordinateLength - y.size)
                System.arraycopy(x, maxOf(0, x.size - coordinateLength), result, xOffset, minOf(x.size, coordinateLength))
                System.arraycopy(y, maxOf(0, y.size - coordinateLength), result, yOffset, minOf(y.size, coordinateLength))
                result
            }
            is Algorithm.RSA -> {
                // For RSA keys, we need modulus and exponent
                val n = jwk.n
                val e = jwk.e
                if (n == null || e == null) {
                    throw TrustWeaveException.Unknown(
                        message = "Missing modulus or exponent in RSA key"
                    )
                }
                // Reconstruct RSA public key
                // This is simplified - in production, use proper RSA key factory
                val keyFactory = KeyFactory.getInstance("RSA")
                val publicKeySpec = RSAPublicKeySpec(
                    BigInteger(1, n),
                    BigInteger(1, e)
                )
                val publicKey = keyFactory.generatePublic(publicKeySpec)
                publicKey.encoded
            }
            else -> throw TrustWeaveException.Unknown(
                message = "Unsupported algorithm for key extraction: ${algorithm.name}"
            )
        }
    }

    private fun prehashForAzure(
        data: ByteArray,
        algorithm: com.azure.security.keyvault.keys.cryptography.models.SignatureAlgorithm
    ): ByteArray {
        val hashAlgorithm = when {
            algorithm == com.azure.security.keyvault.keys.cryptography.models.SignatureAlgorithm.ES256 ||
                algorithm == com.azure.security.keyvault.keys.cryptography.models.SignatureAlgorithm.ES256K ||
                algorithm == com.azure.security.keyvault.keys.cryptography.models.SignatureAlgorithm.RS256 -> "SHA-256"
            algorithm == com.azure.security.keyvault.keys.cryptography.models.SignatureAlgorithm.ES384 ||
                algorithm == com.azure.security.keyvault.keys.cryptography.models.SignatureAlgorithm.RS384 -> "SHA-384"
            algorithm == com.azure.security.keyvault.keys.cryptography.models.SignatureAlgorithm.ES512 ||
                algorithm == com.azure.security.keyvault.keys.cryptography.models.SignatureAlgorithm.RS512 -> "SHA-512"
            // PS256/PS384/PS512: Azure PSS performs hashing internally; do NOT pre-hash.
            // If PSS is ever supported, route through a separate non-prehash path.
            // The else branch below handles any unknown algorithm.
            else -> throw TrustWeaveException.UnsupportedAlgorithm(
                algorithm = algorithm.toString(),
                supportedAlgorithms = listOf("ES256", "ES256K", "ES384", "ES512", "RS256", "RS384", "RS512")
            )
        }
        return MessageDigest.getInstance(hashAlgorithm).digest(data)
    }

    /**
     * Removes all cached [CryptographyClient] entries whose cache key contains [keyId].
     *
     * Call this after rotating a key in Azure Key Vault so that the next [sign] invocation
     * builds a fresh client pointing at the new key version instead of continuing to use the
     * stale cached client that still references the old version.
     *
     * @param keyId The key name (or name/version) whose cache entries should be evicted.
     *              Matching is done by substring: any entry whose cache key contains [keyId]
     *              will be removed.
     */
    fun clearCryptoClientCache(keyId: String) {
        val resolvedId = AlgorithmMapping.resolveKeyId(keyId)
        // Delimiter-aware predicate: matches the exact key name, a versioned path of that key
        // (resolvedId/<version>), or a vault-URL-prefixed entry (<vaultUrl>/resolvedId).
        // Using contains() would incorrectly evict entries for keys whose name is a prefix of
        // another key's name (e.g. evicting "mykey" would also evict "mykey-v2").
        // Note: a concurrent sign() call that races with this eviction may re-insert a fresh
        // client immediately after removal; callers rotating keys should quiesce signing
        // operations or accept that one additional signing with the old key version may occur.
        cryptoClientCache.entries.removeIf { (k, _) ->
            k == resolvedId || k.startsWith("$resolvedId/") || k.endsWith("/$resolvedId")
        }
        // Also evict the algorithm cache so a re-fetch reflects the new key version.
        keyAlgorithmCache.entries.removeIf { (k, _) ->
            k == resolvedId || k.startsWith("$resolvedId/") || k.endsWith("/$resolvedId")
        }
        logger.debug("Evicted crypto client cache entries for key: {}", resolvedId)
    }

    override fun close() {
        // Azure KeyClient implements Closeable, but we don't need to explicitly close it
        // as it's managed by the Azure SDK
    }
}

