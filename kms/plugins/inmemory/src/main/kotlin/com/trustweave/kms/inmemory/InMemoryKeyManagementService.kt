package com.trustweave.kms.inmemory

import com.trustweave.core.identifiers.KeyId
import com.trustweave.kms.Algorithm
import com.trustweave.kms.KeyHandle
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.results.DeleteKeyResult
import com.trustweave.kms.results.GenerateKeyResult
import com.trustweave.kms.results.GetPublicKeyResult
import com.trustweave.kms.results.SignResult
import com.trustweave.kms.JwkKeys
import com.trustweave.kms.JwkKeyTypes
import com.trustweave.kms.KmsOptionKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.security.*
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider

/**
 * Native TrustWeave in-memory Key Management Service.
 * 
 * This is a built-in, native TrustWeave implementation that uses Java's built-in cryptographic
 * providers for key generation, storage, and signing. It's designed for development, testing,
 * and scenarios where a full HSM or cloud KMS is not required or available.
 * 
 * **Thread Safety:** This implementation is fully thread-safe. All operations use thread-safe
 * `ConcurrentHashMap` for key storage and are executed on `Dispatchers.IO` for I/O-bound
 * cryptographic operations.
 * 
 * **Key Storage:** Keys are stored in memory only and will be lost when the service instance
 * is destroyed. This makes it ideal for:
 * - Development and testing
 * - Single-process applications
 * - Ephemeral key management scenarios
 * 
 * **For production use**, consider using a persistent KMS provider:
 * - AWS KMS (FIPS 140-3 Level 3 validated)
 * - Azure Key Vault
 * - Google Cloud KMS
 * - HashiCorp Vault
 * - Hardware Security Modules (HSMs)
 * 
 * **Algorithm Support:**
 * - **Ed25519**: Requires Java 15+ or BouncyCastle provider
 * - **secp256k1, P-256, P-384, P-521**: Supported via Java's standard EC provider
 * 
 * **Input Validation:**
 * - Key IDs are validated for length (max 256 characters) and non-blank
 * - Signing data is validated for non-empty and size limits (max 10 MB to prevent DoS)
 * - Duplicate key IDs are rejected during generation
 * - Algorithm compatibility is checked before signing
 * 
 * **Security Considerations:**
 * - Private keys are stored in memory and are accessible to the JVM process
 * - Keys are not encrypted at rest (they're in memory)
 * - Suitable for development/testing, not for production secrets
 * - Consider using a hardware-backed KMS for production
 *
 * **Example:**
 * ```kotlin
 * // Create an in-memory KMS instance
 * val kms = InMemoryKeyManagementService()
 * 
 * // Generate a key
 * val result = kms.generateKey(Algorithm.Ed25519)
 * when (result) {
 *     is GenerateKeyResult.Success -> {
 *         val keyHandle = result.keyHandle
 *         println("Key created: ${keyHandle.id}")
 *         
 *         // Sign data
 *         val signResult = kms.sign(keyHandle.id, "Hello, World!".toByteArray())
 *         when (signResult) {
 *             is SignResult.Success -> println("Signature: ${signResult.signature.toHexString()}")
 *             is SignResult.Failure -> println("Error: ${signResult.reason}")
 *         }
 *     }
 *     is GenerateKeyResult.Failure -> println("Error: ${result.reason}")
 * }
 * ```
 * 
 * **Via Factory API:**
 * ```kotlin
 * import com.trustweave.kms.*
 * 
 * val kms = KeyManagementServices.create("inmemory")
 * ```
 */
class InMemoryKeyManagementService(
    private val keyStore: MutableMap<KeyId, KeyPair> = ConcurrentHashMap(),
    private val keyMetadata: MutableMap<KeyId, Algorithm> = ConcurrentHashMap()
) : KeyManagementService {

    init {
        // Ensure BouncyCastle provider is registered for secp256k1 and Ed25519 support
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    companion object {
        /**
         * Algorithms supported by InMemoryKeyManagementService.
         * 
         * Note: Ed25519 requires Java 15+ or a crypto provider like BouncyCastle.
         * All EC algorithms (secp256k1, P-256, P-384, P-521) are supported via Java's standard EC provider.
         * RSA algorithms are supported via Java's standard RSA provider.
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
         * Maximum key ID length (256 characters).
         */
        private const val MAX_KEY_ID_LENGTH = 256
        
        /**
         * Maximum data size for signing (10 MB) to prevent DoS attacks.
         */
        private const val MAX_SIGN_DATA_SIZE = 10 * 1024 * 1024
    }

    private val logger = LoggerFactory.getLogger(InMemoryKeyManagementService::class.java)

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
            // Validate and generate key ID
            val providedKeyId = options[KmsOptionKeys.KEY_ID] as? String
            val keyIdString = if (providedKeyId != null) {
                // If key ID is provided, validate it
                if (providedKeyId.isBlank()) {
                    return@withContext GenerateKeyResult.Failure.InvalidOptions(
                        algorithm = algorithm,
                        reason = "Key ID must be non-blank",
                        invalidOptions = options
                    )
                }
                providedKeyId
            } else {
                // Generate a new key ID if not provided
                "trustweave-key-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
            }
            
            // Validate key ID format
            if (keyIdString.length > MAX_KEY_ID_LENGTH) {
                return@withContext GenerateKeyResult.Failure.InvalidOptions(
                    algorithm = algorithm,
                    reason = "Key ID must be $MAX_KEY_ID_LENGTH characters or less, got ${keyIdString.length}",
                    invalidOptions = options
                )
            }
            
            val keyId = KeyId(keyIdString)
            
            // Check if key already exists
            if (keyStore.containsKey(keyId)) {
                logger.warn("Key already exists: keyId={}", keyId.value)
                return@withContext GenerateKeyResult.Failure.InvalidOptions(
                    algorithm = algorithm,
                    reason = "Key with ID '${keyId.value}' already exists",
                    invalidOptions = options
                )
            }

            // Generate key pair using Java crypto
            val keyPair = generateKeyPair(algorithm)
            
            // Store key pair and metadata
            keyStore[keyId] = keyPair
            keyMetadata[keyId] = algorithm

            // Convert public key to JWK format
            val publicKeyJwk = publicKeyToJwk(keyPair.public, algorithm)

            val handle = KeyHandle(
                id = keyId,
                algorithm = algorithm.name,
                publicKeyJwk = publicKeyJwk
            )

            logger.info("Generated key in in-memory KMS: keyId={}, algorithm={}", keyId.value, algorithm.name)

            GenerateKeyResult.Success(handle)
        } catch (e: NoSuchAlgorithmException) {
            logger.error("Algorithm not supported by JVM: algorithm={}", algorithm.name, e)
            GenerateKeyResult.Failure.Error(
                algorithm = algorithm,
                reason = "Algorithm '${algorithm.name}' is not supported by the JVM. " +
                        "Ed25519 requires Java 15+ or BouncyCastle provider.",
                cause = e
            )
        } catch (e: InvalidAlgorithmParameterException) {
            logger.error("Invalid algorithm parameters: algorithm={}", algorithm.name, e)
            GenerateKeyResult.Failure.Error(
                algorithm = algorithm,
                reason = "Invalid algorithm parameters for '${algorithm.name}': ${e.message ?: "Unknown error"}",
                cause = e
            )
        } catch (e: Exception) {
            logger.error("Failed to generate key: algorithm={}", algorithm.name, e)
            GenerateKeyResult.Failure.Error(
                algorithm = algorithm,
                reason = "Failed to generate key: ${e.message ?: "Unknown error"}",
                cause = e
            )
        }
    }

    override suspend fun getPublicKey(keyId: KeyId): GetPublicKeyResult = withContext(Dispatchers.IO) {
        try {
            val keyPair = keyStore[keyId] ?: return@withContext GetPublicKeyResult.Failure.KeyNotFound(keyId = keyId)
            val algorithm = keyMetadata[keyId] ?: return@withContext GetPublicKeyResult.Failure.Error(
                keyId = keyId,
                reason = "Algorithm metadata not found for key"
            )

            val publicKeyJwk = publicKeyToJwk(keyPair.public, algorithm)

            GetPublicKeyResult.Success(
                KeyHandle(
                    id = keyId,
                    algorithm = algorithm.name,
                    publicKeyJwk = publicKeyJwk
                )
            )
        } catch (e: IllegalArgumentException) {
            logger.error("Invalid argument when getting public key: keyId={}", keyId.value, e)
            GetPublicKeyResult.Failure.Error(
                keyId = keyId,
                reason = "Invalid key format: ${e.message ?: "Unknown error"}",
                cause = e
            )
        } catch (e: Exception) {
            logger.error("Failed to get public key: keyId={}", keyId.value, e)
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
            // Validate input data
            if (data.isEmpty()) {
                logger.warn("Attempted to sign empty data: keyId={}", keyId.value)
                return@withContext SignResult.Failure.Error(
                    keyId = keyId,
                    reason = "Cannot sign empty data"
                )
            }
            
            // Validate data size (prevent DoS with extremely large data)
            if (data.size > MAX_SIGN_DATA_SIZE) {
                logger.warn("Data too large for signing: keyId={}, size={}", keyId.value, data.size)
                return@withContext SignResult.Failure.Error(
                    keyId = keyId,
                    reason = "Data size (${data.size} bytes) exceeds maximum allowed size ($MAX_SIGN_DATA_SIZE bytes)"
                )
            }
            
            val keyPair = keyStore[keyId] ?: return@withContext SignResult.Failure.KeyNotFound(keyId = keyId)
            val keyAlgorithm = keyMetadata[keyId] ?: return@withContext SignResult.Failure.Error(
                keyId = keyId,
                reason = "Algorithm metadata not found for key"
            )

            // Use provided algorithm or key's algorithm
            val signingAlgorithm = algorithm ?: keyAlgorithm

            // Check compatibility
            if (algorithm != null && !algorithm.isCompatibleWith(keyAlgorithm)) {
                logger.warn("Algorithm incompatibility: keyId={}, requestedAlgorithm={}, keyAlgorithm={}", 
                    keyId.value, algorithm.name, keyAlgorithm.name)
                return@withContext SignResult.Failure.UnsupportedAlgorithm(
                    keyId = keyId,
                    requestedAlgorithm = algorithm,
                    keyAlgorithm = keyAlgorithm,
                    reason = "Algorithm '${algorithm.name}' is not compatible with key algorithm '${keyAlgorithm.name}'"
                )
            }

            val signature = signData(keyPair.private, data, signingAlgorithm)
            
            logger.debug("Successfully signed data: keyId={}, algorithm={}, dataSize={}, signatureSize={}", 
                keyId.value, signingAlgorithm.name, data.size, signature.size)

            SignResult.Success(signature)
        } catch (e: NoSuchAlgorithmException) {
            logger.error("Signing algorithm not supported: keyId={}, algorithm={}", keyId.value, algorithm?.name ?: "unknown", e)
            SignResult.Failure.Error(
                keyId = keyId,
                reason = "Signing algorithm not supported by JVM: ${algorithm?.name ?: "unknown"}. " +
                        "Ed25519 requires Java 15+ or BouncyCastle provider.",
                cause = e
            )
        } catch (e: InvalidKeyException) {
            logger.error("Invalid key for signing: keyId={}", keyId.value, e)
            SignResult.Failure.Error(
                keyId = keyId,
                reason = "Invalid key for signing: ${e.message ?: "Key may be corrupted or incompatible"}",
                cause = e
            )
        } catch (e: SignatureException) {
            logger.error("Signature generation failed: keyId={}", keyId.value, e)
            SignResult.Failure.Error(
                keyId = keyId,
                reason = "Signature generation failed: ${e.message ?: "Unknown error"}",
                cause = e
            )
        } catch (e: Exception) {
            logger.error("Failed to sign data: keyId={}", keyId.value, e)
            SignResult.Failure.Error(
                keyId = keyId,
                reason = "Failed to sign data: ${e.message ?: "Unknown error"}",
                cause = e
            )
        }
    }

    override suspend fun deleteKey(keyId: KeyId): DeleteKeyResult = withContext(Dispatchers.IO) {
        try {
            val removed = keyStore.remove(keyId) != null
            keyMetadata.remove(keyId)
            
            if (removed) {
                logger.info("Deleted key from in-memory KMS: keyId={}", keyId.value)
                DeleteKeyResult.Deleted
            } else {
                logger.debug("Key not found for deletion: keyId={}", keyId.value)
                DeleteKeyResult.NotFound
            }
        } catch (e: Exception) {
            logger.error("Failed to delete key: keyId={}", keyId.value, e)
            DeleteKeyResult.Failure.Error(
                keyId = keyId,
                reason = "Failed to delete key: ${e.message ?: "Unknown error"}",
                cause = e
            )
        }
    }

    /**
     * Gets the number of keys currently stored in memory.
     * 
     * @return Number of keys in the key store
     */
    fun getKeyCount(): Int = keyStore.size

    /**
     * Clears all keys from memory.
     * 
     * **Warning:** This operation is irreversible and will delete all keys.
     * Use with caution, especially in production scenarios.
     */
    fun clearAllKeys() {
        val count = keyStore.size
        keyStore.clear()
        keyMetadata.clear()
        logger.info("Cleared all keys from in-memory KMS: removed {} keys", count)
    }

    /**
     * Generates a key pair for the given algorithm.
     * 
     * @param algorithm The algorithm to generate a key pair for
     * @return Generated KeyPair
     * @throws NoSuchAlgorithmException if the algorithm is not supported by the JVM
     * @throws InvalidAlgorithmParameterException if the algorithm parameters are invalid
     */
    private fun generateKeyPair(algorithm: Algorithm): KeyPair {
        val keyPairGenerator = when (algorithm) {
            is Algorithm.Ed25519 -> {
                try {
                    KeyPairGenerator.getInstance("Ed25519")
                } catch (e: NoSuchAlgorithmException) {
                    throw NoSuchAlgorithmException(
                        "Ed25519 requires Java 15+ or BouncyCastle provider. " +
                        "Consider using a different algorithm or adding BouncyCastle.",
                        e
                    )
                }
            }
            is Algorithm.Secp256k1 -> {
                // secp256k1 requires a named curve specification
                // Try standard JVM provider first, fall back to BouncyCastle if needed
                try {
                    val generator = KeyPairGenerator.getInstance("EC")
                    val ecSpec = ECGenParameterSpec("secp256k1")
                    generator.initialize(ecSpec)
                    generator
                } catch (e: InvalidAlgorithmParameterException) {
                    // Fall back to BouncyCastle provider for secp256k1
                    try {
                        val bcGenerator = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME)
                        val bcSpec = ECNamedCurveTable.getParameterSpec("secp256k1")
                            ?: throw InvalidAlgorithmParameterException("secp256k1 curve not found in BouncyCastle")
                        bcGenerator.initialize(bcSpec)
                        bcGenerator
                    } catch (e2: Exception) {
                        throw InvalidAlgorithmParameterException(
                            "secp256k1 curve is not supported. " +
                            "BouncyCastle provider failed: ${e2.message}",
                            e2
                        )
                    }
                }
            }
            is Algorithm.P256 -> {
                val generator = KeyPairGenerator.getInstance("EC")
                val ecSpec = ECGenParameterSpec("secp256r1") // P-256
                generator.initialize(ecSpec)
                generator
            }
            is Algorithm.P384 -> {
                val generator = KeyPairGenerator.getInstance("EC")
                val ecSpec = ECGenParameterSpec("secp384r1") // P-384
                generator.initialize(ecSpec)
                generator
            }
            is Algorithm.P521 -> {
                val generator = KeyPairGenerator.getInstance("EC")
                val ecSpec = ECGenParameterSpec("secp521r1") // P-521
                generator.initialize(ecSpec)
                generator
            }
            is Algorithm.RSA -> {
                val generator = KeyPairGenerator.getInstance("RSA")
                generator.initialize(algorithm.keySize)
                generator
            }
            else -> throw IllegalArgumentException("Unsupported algorithm: ${algorithm.name}")
        }
        
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Converts a public key to JWK (JSON Web Key) format.
     * 
     * @param publicKey The public key to convert
     * @param algorithm The algorithm associated with the key
     * @return Map representing the JWK format
     * @throws IllegalArgumentException if the algorithm is not supported for JWK conversion
     */
    private fun publicKeyToJwk(publicKey: PublicKey, algorithm: Algorithm): Map<String, Any?> {
        return when (algorithm) {
            is Algorithm.Ed25519 -> {
                // Ed25519 public key is 32 bytes
                val publicKeyBytes = publicKey.encoded
                // Extract the raw 32-byte key from DER encoding
                val rawKey = if (publicKeyBytes.size >= 44 && publicKeyBytes[0] == 0x30.toByte()) {
                    // Ed25519 public key in DER: 30 2A 30 05 06 03 2B 65 70 03 21 00 [32 bytes]
                    publicKeyBytes.sliceArray(12 until 44)
                } else {
                    publicKeyBytes.takeLast(32).toByteArray()
                }
                mapOf(
                    JwkKeys.KTY to JwkKeyTypes.OKP,
                    JwkKeys.CRV to Algorithm.Ed25519.curveName,
                    JwkKeys.X to Base64.getUrlEncoder().withoutPadding().encodeToString(rawKey)
                )
            }
            is Algorithm.Secp256k1, is Algorithm.P256, is Algorithm.P384, is Algorithm.P521 -> {
                val ecPublicKey = publicKey as? ECPublicKey
                    ?: throw IllegalArgumentException("Expected EC public key for algorithm ${algorithm.name}")
                
                val point = ecPublicKey.w
                val curveName = algorithm.curveName
                    ?: throw IllegalArgumentException("Unsupported EC algorithm: ${algorithm.name}")

                val coordinateLength = when (algorithm) {
                    is Algorithm.Secp256k1, is Algorithm.P256 -> 32
                    is Algorithm.P384 -> 48
                    is Algorithm.P521 -> 66
                    else -> 32
                }

                // Convert BigInteger to unsigned byte array
                fun toUnsignedByteArray(bigInt: BigInteger, length: Int): ByteArray {
                    val bytes = bigInt.toByteArray()
                    val result = ByteArray(length)
                    val offset = length - bytes.size
                    if (offset >= 0) {
                        System.arraycopy(bytes, 0, result, offset, bytes.size)
                    } else {
                        System.arraycopy(bytes, bytes.size - length, result, 0, length)
                    }
                    return result
                }

                val x = toUnsignedByteArray(point.affineX, coordinateLength)
                val y = toUnsignedByteArray(point.affineY, coordinateLength)

                mapOf(
                    JwkKeys.KTY to JwkKeyTypes.EC,
                    JwkKeys.CRV to curveName,
                    JwkKeys.X to Base64.getUrlEncoder().withoutPadding().encodeToString(x),
                    JwkKeys.Y to Base64.getUrlEncoder().withoutPadding().encodeToString(y)
                )
            }
            is Algorithm.RSA -> {
                val rsaPublicKey = publicKey as? RSAPublicKey
                    ?: throw IllegalArgumentException("Expected RSA public key for algorithm ${algorithm.name}")
                
                // Convert BigInteger to unsigned byte array
                fun toUnsignedByteArray(bigInt: BigInteger): ByteArray {
                    val signed = bigInt.toByteArray()
                    if (signed.isNotEmpty() && signed[0] == 0.toByte()) {
                        return signed.sliceArray(1 until signed.size)
                    }
                    return signed
                }
                
                mapOf(
                    JwkKeys.KTY to JwkKeyTypes.RSA,
                    JwkKeys.N to Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsignedByteArray(rsaPublicKey.modulus)),
                    JwkKeys.E to Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsignedByteArray(rsaPublicKey.publicExponent))
                )
            }
            else -> throw IllegalArgumentException("Unsupported algorithm for JWK conversion: ${algorithm.name}")
        }
    }

    /**
     * Signs data using the private key and algorithm.
     * 
     * @param privateKey The private key to use for signing
     * @param data The data to sign
     * @param algorithm The algorithm to use for signing
     * @return The signature bytes
     * @throws NoSuchAlgorithmException if the signing algorithm is not supported
     * @throws InvalidKeyException if the private key is invalid
     * @throws SignatureException if signing fails
     */
    private fun signData(privateKey: PrivateKey, data: ByteArray, algorithm: Algorithm): ByteArray {
        val signature = when (algorithm) {
            is Algorithm.Ed25519 -> {
                val signer = Signature.getInstance("Ed25519")
                signer.initSign(privateKey)
                signer.update(data)
                signer.sign()
            }
            is Algorithm.Secp256k1 -> {
                // secp256k1: Try BouncyCastle first (most reliable), fall back to standard provider
                val hashAlgorithm = "SHA256withECDSA"
                try {
                    val signer = Signature.getInstance(hashAlgorithm, BouncyCastleProvider.PROVIDER_NAME)
                    signer.initSign(privateKey)
                    signer.update(data)
                    signer.sign()
                } catch (e: Exception) {
                    // Fall back to standard provider if BouncyCastle fails
                    try {
                        val signer = Signature.getInstance(hashAlgorithm)
                        signer.initSign(privateKey)
                        signer.update(data)
                        signer.sign()
                    } catch (e2: Exception) {
                        throw SignatureException("Failed to sign secp256k1 with $hashAlgorithm: ${e2.message}", e2)
                    }
                }
            }
            is Algorithm.P256, is Algorithm.P384, is Algorithm.P521 -> {
                // Use ECDSA with SHA-256/384/512 based on curve
                val hashAlgorithm = when (algorithm) {
                    is Algorithm.P256 -> "SHA256withECDSA"
                    is Algorithm.P384 -> "SHA384withECDSA"
                    is Algorithm.P521 -> "SHA512withECDSA"
                    else -> "SHA256withECDSA"
                }
                val signer = Signature.getInstance(hashAlgorithm)
                signer.initSign(privateKey)
                signer.update(data)
                signer.sign()
            }
            is Algorithm.RSA -> {
                // Use RSA with SHA-256/384/512 based on key size
                val hashAlgorithm = when (algorithm.keySize) {
                    2048 -> "SHA256withRSA"
                    3072 -> "SHA384withRSA"
                    4096 -> "SHA512withRSA"
                    else -> "SHA256withRSA"
                }
                val signer = Signature.getInstance(hashAlgorithm)
                signer.initSign(privateKey)
                signer.update(data)
                signer.sign()
            }
            else -> throw IllegalArgumentException("Unsupported algorithm for signing: ${algorithm.name}")
        }
        return signature
    }
}

