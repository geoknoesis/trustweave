package com.geoknoesis.vericore.testkit.kms

import com.geoknoesis.vericore.kms.*
import java.security.*
import java.security.spec.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

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

    private val keys = ConcurrentHashMap<String, KeyPair>()
    private val keyMetadata = ConcurrentHashMap<String, KeyHandle>()

    override suspend fun getSupportedAlgorithms(): Set<Algorithm> = SUPPORTED_ALGORITHMS

    override suspend fun generateKey(
        algorithm: Algorithm,
        options: Map<String, Any?>
    ): KeyHandle {
        if (!supportsAlgorithm(algorithm)) {
            throw UnsupportedAlgorithmException(
                "Algorithm '${algorithm.name}' is not supported. " +
                "Supported: ${SUPPORTED_ALGORITHMS.joinToString(", ") { it.name }}"
            )
        }
        
        val keyPair = when (algorithm) {
            is Algorithm.Ed25519 -> generateEd25519KeyPair()
            is Algorithm.Secp256k1 -> generateSecp256k1KeyPair()
            else -> throw UnsupportedAlgorithmException("Algorithm ${algorithm.name} not implemented")
        }

        val keyId = options["keyId"] as? String ?: "key_${UUID.randomUUID()}"
        
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
                    throw IllegalStateException("Invalid Ed25519 public key encoding: expected at least 32 bytes, got ${encoded.size}")
                }
                
                require(rawKey.size == 32) { 
                    "Expected 32-byte Ed25519 public key, but got ${rawKey.size} bytes from encoded key of size ${encoded.size}" 
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
        return handle
    }

    override suspend fun getPublicKey(keyId: String): KeyHandle {
        return keyMetadata[keyId]
            ?: throw KeyNotFoundException("Key not found: $keyId")
    }

    override suspend fun sign(
        keyId: String,
        data: ByteArray,
        algorithm: Algorithm?
    ): ByteArray {
        val keyPair = keys[keyId]
            ?: throw KeyNotFoundException("Key not found: $keyId")

        val signAlgorithm = algorithm?.let { alg ->
            when (alg) {
                is Algorithm.Ed25519 -> "Ed25519"
                is Algorithm.Secp256k1 -> "SHA256withECDSA"
                else -> throw IllegalArgumentException("Unknown algorithm: ${alg.name}")
            }
        } ?: when (keyMetadata[keyId]?.algorithm?.uppercase()) {
            "ED25519" -> "Ed25519"
            "SECP256K1" -> "SHA256withECDSA"
            else -> throw IllegalArgumentException("Unknown algorithm for key: $keyId")
        }

        val signature = Signature.getInstance(signAlgorithm).apply {
            initSign(keyPair.private)
            update(data)
        }
        return signature.sign()
    }

    override suspend fun deleteKey(keyId: String): Boolean {
        keys.remove(keyId)
        return keyMetadata.remove(keyId) != null
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
        try {
            val keyPairGenerator = KeyPairGenerator.getInstance("EC")
            val ecGenParameterSpec = ECGenParameterSpec("secp256k1")
            keyPairGenerator.initialize(ecGenParameterSpec)
            return keyPairGenerator.generateKeyPair()
        } catch (e: Exception) {
            throw UnsupportedOperationException("secp256k1 not available: ${e.message}")
        }
    }
}

