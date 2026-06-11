package org.trustweave.testkit.kms

import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.*
import org.trustweave.kms.results.*
import org.trustweave.kms.util.EcdsaSignatureCodec
import java.math.BigInteger
import java.security.*
import java.security.interfaces.ECPublicKey
import java.security.spec.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec

/**
 * In-memory implementation of KeyManagementService for testing.
 * Generates and stores keys in memory using Java crypto APIs.
 *
 * Conforms to the [KeyManagementService] contract the same way the production
 * `kms:plugins:inmemory` provider does:
 * - EC signatures are returned in IEEE P1363 form (raw `r || s`), with low-s
 *   normalization for secp256k1, via [EcdsaSignatureCodec.normalize].
 * - secp256k1 JWKs carry the real affine `x`/`y` coordinates of the public key
 *   (not bytes sliced out of the DER/SPKI encoding).
 * - Algorithm compatibility follows [Algorithm.isCompatibleWith].
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

            val publicKeyJwk = publicKeyToJwk(keyPair.public, algorithm)

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
            // JCA ECDSA emits ASN.1 DER; the KeyManagementService contract requires P1363
            // (raw r||s) with low-s for secp256k1, so normalize before returning — exactly
            // like the production kms:plugins:inmemory provider.
            SignResult.Success(EcdsaSignatureCodec.normalize(signature, effectiveAlgorithm))
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
     *
     * Delegates to [Algorithm.isCompatibleWith] so the testkit KMS agrees with the
     * canonical compatibility rules (in particular, RSA variants with different key
     * sizes are NOT interchangeable).
     */
    private fun isAlgorithmCompatible(requested: Algorithm, key: Algorithm): Boolean {
        return requested.isCompatibleWith(key)
    }

    /**
     * Clears all keys (useful for test cleanup).
     */
    fun clear() {
        keys.clear()
        keyMetadata.clear()
    }

    /**
     * Converts a public key to JWK format.
     *
     * Ported from the production `kms:plugins:inmemory` provider so the testkit KMS
     * produces the same, correct JWKs:
     * - Ed25519: raw 32-byte key extracted from the SPKI structure (BouncyCastle ASN.1
     *   parsing, not byte-offset slicing).
     * - secp256k1: real affine x/y coordinates from the [ECPublicKey] point, as unsigned
     *   big-endian 32-byte values.
     *
     * @throws IllegalArgumentException if the key cannot be converted
     */
    private fun publicKeyToJwk(publicKey: PublicKey, algorithm: Algorithm): Map<String, Any?> {
        return when (algorithm) {
            is Algorithm.Ed25519 -> {
                val publicKeyBytes = publicKey.encoded
                val rawKey = when {
                    publicKeyBytes.size >= 44 && publicKeyBytes[0] == 0x30.toByte() -> {
                        val spki = SubjectPublicKeyInfo.getInstance(publicKeyBytes)
                        spki.publicKeyData.bytes
                    }
                    publicKeyBytes.size == 32 && publicKey.algorithm == "Ed25519" -> {
                        publicKeyBytes
                    }
                    else -> throw IllegalArgumentException(
                        "Cannot extract Ed25519 raw key bytes: unexpected encoding " +
                            "(algorithm=${publicKey.algorithm}, size=${publicKeyBytes.size})"
                    )
                }
                mapOf(
                    "kty" to "OKP",
                    "crv" to "Ed25519",
                    "x" to Base64.getUrlEncoder().withoutPadding().encodeToString(rawKey)
                )
            }
            is Algorithm.Secp256k1 -> {
                val ecPublicKey = publicKey as? ECPublicKey
                    ?: throw IllegalArgumentException(
                        "Expected EC public key for algorithm ${algorithm.name}"
                    )

                val point = ecPublicKey.w
                val x = toUnsignedFixedWidth(point.affineX, 32)
                val y = toUnsignedFixedWidth(point.affineY, 32)

                mapOf(
                    "kty" to "EC",
                    "crv" to "secp256k1",
                    "x" to Base64.getUrlEncoder().withoutPadding().encodeToString(x),
                    "y" to Base64.getUrlEncoder().withoutPadding().encodeToString(y)
                )
            }
            else -> throw IllegalArgumentException(
                "Unsupported algorithm for JWK conversion: ${algorithm.name}"
            )
        }
    }

    /**
     * Converts a [BigInteger] to an unsigned, big-endian, fixed-length byte array.
     *
     * `BigInteger.toByteArray()` prepends a 0x00 sign byte for positive values whose MSB
     * is set; strip it before right-aligning into the fixed-length result.
     */
    private fun toUnsignedFixedWidth(bigInt: BigInteger, length: Int): ByteArray {
        val signed = bigInt.toByteArray()
        val bytes = if (signed.isNotEmpty() && signed[0] == 0.toByte()) {
            signed.copyOfRange(1, signed.size)
        } else {
            signed
        }
        require(bytes.size <= length) {
            "EC coordinate too large after sign-byte strip: ${bytes.size} > $length"
        }
        val result = ByteArray(length)
        System.arraycopy(bytes, 0, result, length - bytes.size, bytes.size)
        return result
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
        is GenerateKeyResult.Failure.DuplicateKeyId -> throw IllegalArgumentException("Key with ID '${result.keyId.value}' already exists")
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
        is GetPublicKeyResult.Failure.KeyNotFound -> throw org.trustweave.kms.exception.KmsException.KeyNotFound(
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
        is SignResult.Failure.KeyNotFound -> throw org.trustweave.kms.exception.KmsException.KeyNotFound(
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

