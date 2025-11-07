package io.geoknoesis.vericore.testkit.kms

import io.geoknoesis.vericore.kms.*
import java.security.*
import java.security.spec.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of KeyManagementService for testing.
 * Generates and stores keys in memory using Java crypto APIs.
 */
class InMemoryKeyManagementService : KeyManagementService {

    private val keys = ConcurrentHashMap<String, KeyPair>()
    private val keyMetadata = ConcurrentHashMap<String, KeyHandle>()

    override suspend fun generateKey(
        algorithm: String,
        options: Map<String, Any?>
    ): KeyHandle {
        val keyPair = when (algorithm.uppercase()) {
            "ED25519" -> generateEd25519KeyPair()
            "SECP256K1" -> generateSecp256k1KeyPair()
            else -> throw IllegalArgumentException("Unsupported algorithm: $algorithm")
        }

        val keyId = options["keyId"] as? String ?: "key_${UUID.randomUUID()}"
        
        keys[keyId] = keyPair
        
        val publicKeyJwk = when (algorithm.uppercase()) {
            "ED25519" -> mapOf(
                "kty" to "OKP",
                "crv" to "Ed25519",
                "x" to Base64.getUrlEncoder().withoutPadding().encodeToString(keyPair.public.encoded)
            )
            "SECP256K1" -> mapOf(
                "kty" to "EC",
                "crv" to "secp256k1",
                "x" to Base64.getUrlEncoder().withoutPadding().encodeToString(keyPair.public.encoded.take(32).toByteArray()),
                "y" to Base64.getUrlEncoder().withoutPadding().encodeToString(keyPair.public.encoded.drop(32).take(32).toByteArray())
            )
            else -> emptyMap()
        }

        val handle = KeyHandle(
            id = keyId,
            algorithm = algorithm,
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
        algorithm: String?
    ): ByteArray {
        val keyPair = keys[keyId]
            ?: throw KeyNotFoundException("Key not found: $keyId")

        val signAlgorithm = algorithm ?: when (keyMetadata[keyId]?.algorithm?.uppercase()) {
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

