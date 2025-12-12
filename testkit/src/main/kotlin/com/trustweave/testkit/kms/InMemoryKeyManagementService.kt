package com.trustweave.testkit.kms

import com.trustweave.core.identifiers.KeyId
import com.trustweave.kms.*
import com.trustweave.kms.results.*
import java.security.*
import java.security.spec.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec

/**
 * In-memory implementation of KeyManagementService for testing.
 * Generates and stores keys in memory using Java crypto APIs.
 */
class InMemoryKeyManagementService : KeyManagementService {

    companion object {
        /**
         * Algorithms supported by InMemoryKeyManagementService.
         */
        val SUPPORTED_ALGORITHMS = setOf(Algorithm.Ed25519, Algorithm.Secp256k1)
    }

    private val keys = ConcurrentHashMap<KeyId, KeyPair>()
    private val keyMetadata = ConcurrentHashMap<KeyId, KeyHandle>()

    override suspend fun getSupportedAlgorithms(): Set<Algorithm> = SUPPORTED_ALGORITHMS

    override suspend fun generateKey(
        algorithm: Algorithm,
        options: Map<String, Any?>
    ): GenerateKeyResult {
        if (!supportsAlgorithm(algorithm)) {
            return GenerateKeyResult.Failure.UnsupportedAlgorithm(
                algorithm = algorithm,
                supportedAlgorithms = SUPPORTED_ALGORITHMS
            )
        }

        return try {
            val keyPair = when (algorithm) {
                is Algorithm.Ed25519 -> generateEd25519KeyPair()
                is Algorithm.Secp256k1 -> generateSecp256k1KeyPair()
                else -> return GenerateKeyResult.Failure.UnsupportedAlgorithm(
                    algorithm = algorithm,
                    supportedAlgorithms = SUPPORTED_ALGORITHMS
                )
            }

            val keyIdString = options["keyId"] as? String ?: "key_${UUID.randomUUID()}"
            val keyId = KeyId(keyIdString)

            keys[keyId] = keyPair

            val publicKeyJwk = when (algorithm) {
                is Algorithm.Ed25519 -> {
                    // Extract raw 32 bytes from DER-encoded public key
                    // Ed25519 public key in DER format: 30 2A 30 05 06 03 2B 65 70 03 21 00 [32 bytes]
                    // The raw key is at bytes 12-44 (12 byte header + 32 byte key)
                    val encoded = keyPair.public.encoded
                    val rawKey = if (encoded.size >= 44 && encoded[0] == 0x30.toByte()) {
                        // Standard DER format: extract bytes 12-44 (32 bytes)
                        encoded.sliceArray(12 until 44)
                    } else if (encoded.size >= 32) {
                        // Fallback: extract last 32 bytes
                        encoded.sliceArray((encoded.size - 32) until encoded.size)
                    } else {
                        return GenerateKeyResult.Failure.Error(
                            algorithm = algorithm,
                            reason = "Invalid Ed25519 public key encoding: expected at least 32 bytes, got ${encoded.size}"
                        )
                    }

                    if (rawKey.size != 32) {
                        return GenerateKeyResult.Failure.Error(
                            algorithm = algorithm,
                            reason = "Expected 32-byte Ed25519 public key, but got ${rawKey.size} bytes from encoded key of size ${encoded.size}"
                        )
                    }

                    mapOf(
                        "kty" to "OKP",
                        "crv" to "Ed25519",
                        "x" to Base64.getUrlEncoder().withoutPadding().encodeToString(rawKey)
                    )
                }
                is Algorithm.Secp256k1 -> mapOf(
                    "kty" to "EC",
                    "crv" to "secp256k1",
                    "x" to Base64.getUrlEncoder().withoutPadding().encodeToString(keyPair.public.encoded.take(32).toByteArray()),
                    "y" to Base64.getUrlEncoder().withoutPadding().encodeToString(keyPair.public.encoded.drop(32).take(32).toByteArray())
                )
                else -> emptyMap()
            }

            val handle = KeyHandle(
                id = keyId,
                algorithm = algorithm.name,
                publicKeyJwk = publicKeyJwk
            )

            keyMetadata[keyId] = handle
            GenerateKeyResult.Success(handle)
        } catch (e: Exception) {
            GenerateKeyResult.Failure.Error(
                algorithm = algorithm,
                reason = "Failed to generate key: ${e.message ?: "Unknown error"}",
                cause = e
            )
        }
    }

    override suspend fun getPublicKey(keyId: KeyId): GetPublicKeyResult {
        val handle = keyMetadata[keyId]
        return if (handle != null) {
            GetPublicKeyResult.Success(handle)
        } else {
            GetPublicKeyResult.Failure.KeyNotFound(keyId = keyId)
        }
    }

    override suspend fun sign(
        keyId: KeyId,
        data: ByteArray,
        algorithm: Algorithm?
    ): SignResult {
        val keyPair = keys[keyId]
            ?: return SignResult.Failure.KeyNotFound(keyId = keyId)

        val keyHandle = keyMetadata[keyId]
            ?: return SignResult.Failure.KeyNotFound(keyId = keyId)

        // Determine the key's algorithm
        val keyAlgorithm = Algorithm.parse(keyHandle.algorithm)
            ?: return SignResult.Failure.Error(
                keyId = keyId,
                reason = "Cannot determine key algorithm from handle: ${keyHandle.algorithm}"
            )

        // Use provided algorithm or key's algorithm
        val effectiveAlgorithm = algorithm ?: keyAlgorithm

        // Validate algorithm compatibility
        if (algorithm != null && !isAlgorithmCompatible(algorithm, keyAlgorithm)) {
            return SignResult.Failure.UnsupportedAlgorithm(
                keyId = keyId,
                requestedAlgorithm = algorithm,
                keyAlgorithm = keyAlgorithm,
                reason = "Algorithm '${algorithm.name}' is not compatible with key algorithm '${keyAlgorithm.name}'"
            )
        }

        return try {
            val signAlgorithm = when (effectiveAlgorithm) {
                is Algorithm.Ed25519 -> "Ed25519"
                is Algorithm.Secp256k1 -> "SHA256withECDSA"
                else -> return SignResult.Failure.UnsupportedAlgorithm(
                    keyId = keyId,
                    requestedAlgorithm = effectiveAlgorithm,
                    keyAlgorithm = keyAlgorithm,
                    reason = "Unknown algorithm: ${effectiveAlgorithm.name}"
                )
            }

            // For secp256k1, use BouncyCastle provider (keys are generated with BouncyCastle)
            val signature = if (effectiveAlgorithm is Algorithm.Secp256k1) {
                // Ensure BouncyCastle provider is available
                if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                    Security.addProvider(BouncyCastleProvider())
                }
                // Use BouncyCastle provider for secp256k1 signing
                // The key was generated with BouncyCastle, so we must use BouncyCastle for signing
                val signer = Signature.getInstance(signAlgorithm, BouncyCastleProvider.PROVIDER_NAME)
                signer.initSign(keyPair.private)
                signer.update(data)
                signer.sign()
            } else {
                Signature.getInstance(signAlgorithm).apply {
                    initSign(keyPair.private)
                    update(data)
                }.sign()
            }
            SignResult.Success(signature)
        } catch (e: Exception) {
            SignResult.Failure.Error(
                keyId = keyId,
                reason = "Failed to sign data: ${e.message ?: "Unknown error"}",
                cause = e
            )
        }
    }

    override suspend fun deleteKey(keyId: KeyId): DeleteKeyResult {
        val keyExisted = keys.remove(keyId) != null
        val metadataExisted = keyMetadata.remove(keyId) != null
        val existed = keyExisted || metadataExisted
        return if (existed) {
            DeleteKeyResult.Deleted
        } else {
            DeleteKeyResult.NotFound
        }
    }

    /**
     * Checks if two algorithms are compatible for signing.
     */
    private fun isAlgorithmCompatible(requested: Algorithm, key: Algorithm): Boolean {
        // Same algorithm is always compatible
        if (requested == key) return true
        
        // For RSA, any RSA variant can sign with any other RSA variant (same key type)
        if (requested is Algorithm.RSA && key is Algorithm.RSA) return true
        
        // For ECC, algorithms must match exactly
        return false
    }

    /**
     * Clears all keys (useful for test cleanup).
     */
    fun clear() {
        keys.clear()
        keyMetadata.clear()
    }

    private fun generateEd25519KeyPair(): KeyPair {
        // Note: Ed25519 support may vary by JDK version
        // For compatibility, we'll use a fallback approach
        try {
            val keyPairGenerator = KeyPairGenerator.getInstance("Ed25519")
            return keyPairGenerator.generateKeyPair()
        } catch (e: NoSuchAlgorithmException) {
            // Fallback: Use EdDSA if Ed25519 is not available
            try {
                val keyPairGenerator = KeyPairGenerator.getInstance("EdDSA")
                val parameterSpec = NamedParameterSpec.ED25519
                keyPairGenerator.initialize(parameterSpec)
                return keyPairGenerator.generateKeyPair()
            } catch (e2: Exception) {
                // Final fallback: Generate a simple key pair for testing
                // This is not cryptographically secure but works for testing
                val random = SecureRandom()
                val privateKeyBytes = ByteArray(32)
                random.nextBytes(privateKeyBytes)
                // Create a mock key pair - in real implementation, use proper Ed25519 library
                throw UnsupportedOperationException("Ed25519 not available in this JVM. Use JDK 15+ or add a crypto library.")
            }
        }
    }

    private fun generateSecp256k1KeyPair(): KeyPair {
        // Try standard JVM first
        try {
            val keyPairGenerator = KeyPairGenerator.getInstance("EC")
            val ecGenParameterSpec = ECGenParameterSpec("secp256k1")
            keyPairGenerator.initialize(ecGenParameterSpec)
            return keyPairGenerator.generateKeyPair()
        } catch (e: InvalidAlgorithmParameterException) {
            // secp256k1 not supported by default JVM, try BouncyCastle
            try {
                // Ensure BouncyCastle provider is available
                if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                    Security.addProvider(BouncyCastleProvider())
                }
                
                val parameterSpec = ECNamedCurveTable.getParameterSpec("secp256k1")
                val keyPairGenerator = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME)
                keyPairGenerator.initialize(parameterSpec)
                return keyPairGenerator.generateKeyPair()
            } catch (e2: Exception) {
                throw UnsupportedOperationException("secp256k1 not available: ${e2.message}", e2)
            }
        } catch (e: Exception) {
            throw UnsupportedOperationException("secp256k1 not available: ${e.message}", e)
        }
    }
}

/**
 * Extension methods for backward compatibility with old exception-based API.
 * These are provided for test convenience but new code should use the Result-based API.
 */
suspend fun InMemoryKeyManagementService.generateKey(
    algorithm: Algorithm,
    options: Map<String, Any?>
): KeyHandle {
    return when (val result = generateKey(algorithm, options)) {
        is GenerateKeyResult.Success -> result.keyHandle
        is GenerateKeyResult.Failure.UnsupportedAlgorithm -> throw UnsupportedAlgorithmException(
            result.reason ?: "Algorithm '${result.algorithm.name}' is not supported. " +
                "Supported: ${result.supportedAlgorithms.joinToString(", ") { it.name }}"
        )
        is GenerateKeyResult.Failure.InvalidOptions -> throw IllegalArgumentException(result.reason)
        is GenerateKeyResult.Failure.Error -> throw RuntimeException(result.reason, result.cause)
    }
}

// Extension function that returns GenerateKeyResult (for tests that check result types)
suspend fun InMemoryKeyManagementService.generateKey(
    algorithmName: String,
    options: Map<String, Any?>
): GenerateKeyResult {
    val algorithm = Algorithm.parse(algorithmName) ?: return GenerateKeyResult.Failure.Error(
        algorithm = Algorithm.Ed25519, // Dummy algorithm for error case
        reason = "Unknown algorithm: $algorithmName"
    )
    return generateKey(algorithm, options)
}

suspend fun InMemoryKeyManagementService.getPublicKey(keyId: KeyId): KeyHandle {
    return when (val result = getPublicKey(keyId)) {
        is GetPublicKeyResult.Success -> result.keyHandle
        is GetPublicKeyResult.Failure.KeyNotFound -> throw com.trustweave.kms.exception.KmsException.KeyNotFound(
            keyId = keyId.value
        )
        is GetPublicKeyResult.Failure.Error -> throw RuntimeException(result.reason, result.cause)
    }
}

suspend fun InMemoryKeyManagementService.signLegacy(
    keyId: KeyId,
    data: ByteArray,
    algorithm: Algorithm? = null
): ByteArray {
    return when (val result = sign(keyId, data, algorithm)) {
        is SignResult.Success -> result.signature
        is SignResult.Failure.KeyNotFound -> throw com.trustweave.kms.exception.KmsException.KeyNotFound(
            keyId = keyId.value
        )
        is SignResult.Failure.UnsupportedAlgorithm -> throw UnsupportedAlgorithmException(
            result.reason ?: "Algorithm '${result.requestedAlgorithm?.name ?: "null"}' is not compatible with key algorithm '${result.keyAlgorithm.name}'"
        )
        is SignResult.Failure.Error -> throw RuntimeException(result.reason, result.cause)
    }
}

suspend fun InMemoryKeyManagementService.signLegacy(
    keyId: KeyId,
    data: ByteArray,
    algorithmName: String?
): ByteArray {
    val algorithm = algorithmName?.let { Algorithm.parse(it) }
    return signLegacy(keyId, data, algorithm)
}

// Extension function for sign that accepts string algorithm name
suspend fun InMemoryKeyManagementService.sign(
    keyId: KeyId,
    data: ByteArray,
    algorithmName: String?
): SignResult {
    val algorithm = algorithmName?.let { Algorithm.parse(it) }
    return sign(keyId, data, algorithm)
}

suspend fun InMemoryKeyManagementService.deleteKey(keyId: KeyId): Boolean {
    return when (val result = deleteKey(keyId)) {
        is DeleteKeyResult.Deleted -> true
        is DeleteKeyResult.NotFound -> false
        is DeleteKeyResult.Failure.Error -> throw RuntimeException(result.reason, result.cause)
    }
}

