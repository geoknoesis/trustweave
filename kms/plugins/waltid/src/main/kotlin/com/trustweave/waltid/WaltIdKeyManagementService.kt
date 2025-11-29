package com.trustweave.waltid

import com.trustweave.core.exception.TrustWeaveException
import com.trustweave.core.types.KeyId
import com.trustweave.kms.Algorithm
import com.trustweave.kms.KeyHandle
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.exception.KmsException
import com.trustweave.kms.UnsupportedAlgorithmException
import com.trustweave.kms.spi.KeyManagementServiceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * walt.id-based implementation of KeyManagementService.
 * Uses walt.id's crypto library for key generation, storage, and signing.
 */
class WaltIdKeyManagementService(
    private val keyStore: MutableMap<KeyId, KeyHandle> = ConcurrentHashMap()
) : KeyManagementService {

    companion object {
        /**
         * Algorithms supported by WaltIdKeyManagementService.
         */
        val SUPPORTED_ALGORITHMS = setOf(
            Algorithm.Ed25519,
            Algorithm.Secp256k1,
            Algorithm.P256,
            Algorithm.P384,
            Algorithm.P521
        )
    }

    override suspend fun getSupportedAlgorithms(): Set<Algorithm> = SUPPORTED_ALGORITHMS

    override suspend fun generateKey(
        algorithm: Algorithm,
        options: Map<String, Any?>
    ): KeyHandle = withContext(Dispatchers.IO) {
        if (!supportsAlgorithm(algorithm)) {
            throw UnsupportedAlgorithmException(
                "Algorithm '${algorithm.name}' is not supported. " +
                "Supported: ${SUPPORTED_ALGORITHMS.joinToString(", ") { it.name }}"
            )
        }
        try {
            // Use walt.id's key generation API
            // Note: This is a placeholder - actual implementation will use walt.id APIs
            val keyIdString = options["keyId"] as? String ?: "key_${System.currentTimeMillis()}"
            val keyId = KeyId(keyIdString)

            // Map algorithm to walt.id format
            val waltIdAlgorithm = when (algorithm) {
                is Algorithm.Ed25519 -> "Ed25519"
                is Algorithm.Secp256k1 -> "secp256k1"
                is Algorithm.P256 -> "P-256"
                is Algorithm.P384 -> "P-384"
                is Algorithm.P521 -> "P-521"
                else -> algorithm.name
            }

            // Generate key using walt.id (placeholder - replace with actual API call)
            // val keyPair = WaltIdCrypto.generateKey(waltIdAlgorithm)

            // For now, create a placeholder key handle
            // In real implementation, extract JWK from walt.id key
            val publicKeyJwk = mapOf(
                "kty" to when (algorithm) {
                    is Algorithm.Ed25519 -> "OKP"
                    else -> "EC"
                },
                "crv" to waltIdAlgorithm,
                "x" to "placeholder" // Replace with actual public key
            )

            val handle = KeyHandle(
                id = keyId,
                algorithm = algorithm.name,
                publicKeyJwk = publicKeyJwk
            )

            keyStore[keyId] = handle
            handle
        } catch (e: Exception) {
            val keyIdValue = options["keyId"] as? String ?: "unknown"
            throw com.trustweave.core.exception.TrustWeaveException.Unknown(
                message = "Failed to generate key: ${e.message ?: "Unknown error"}",
                context = mapOf("keyId" to keyIdValue, "algorithm" to (algorithm?.name ?: "unknown")),
                cause = e
            )
        }
    }

    override suspend fun getPublicKey(keyId: KeyId): KeyHandle = withContext(Dispatchers.IO) {
        keyStore[keyId] ?: throw KmsException.KeyNotFound(
            keyId = keyId.value,
            keyType = null
        )
    }

    override suspend fun sign(
        keyId: KeyId,
        data: ByteArray,
        algorithm: Algorithm?
    ): ByteArray = withContext(Dispatchers.IO) {
        val handle = keyStore[keyId] ?: throw KmsException.KeyNotFound(keyId = keyId.value)

        try {
            // Use walt.id's signing API
            // val signature = WaltIdCrypto.sign(keyId, data, algorithm?.name ?: handle.algorithm)
            // For now, return placeholder
            // In real implementation, use walt.id signing
            ByteArray(64) // Placeholder signature
        } catch (e: Exception) {
            throw com.trustweave.core.exception.TrustWeaveException.Unknown(
                message = "Failed to sign data: ${e.message ?: "Unknown error"}",
                context = mapOf("keyId" to keyId.value, "algorithm" to (algorithm?.name ?: "unknown")),
                cause = e
            )
        }
    }

    override suspend fun deleteKey(keyId: KeyId): Boolean = withContext(Dispatchers.IO) {
        keyStore.remove(keyId) != null
    }
}

/**
 * SPI provider for WaltIdKeyManagementService.
 */
class WaltIdKeyManagementServiceProvider : KeyManagementServiceProvider {
    override val name: String = "waltid"

    override val supportedAlgorithms: Set<Algorithm> = WaltIdKeyManagementService.SUPPORTED_ALGORITHMS

    override fun create(options: Map<String, Any?>): KeyManagementService {
        return WaltIdKeyManagementService()
    }
}

