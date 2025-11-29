package com.trustweave.credential.didcomm.crypto.secret

import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.KeyHandle
import org.didcommx.didcomm.secret.Secret
import org.didcommx.didcomm.secret.SecretResolver
import kotlinx.coroutines.runBlocking

/**
 * Custom SecretResolver that bridges KMS with didcomm-java library.
 *
 * This resolver works with KMS that doesn't expose private keys by:
 * 1. Using KMS signing for operations that require signing
 * 2. For ECDH operations, using a local key store (if provided)
 *
 * **Note**: Full ECDH support requires private key access. If your KMS
 * doesn't support key export, you should use HybridKmsSecretResolver
 * with a local key store for DIDComm keys.
 *
 * **Example Usage:**
 * ```kotlin
 * val localKeyStore = InMemoryLocalKeyStore()
 * val resolver = KmsSecretResolver(kms, resolveDid, localKeyStore)
 * ```
 */
class KmsSecretResolver(
    private val kms: KeyManagementService,
    private val resolveDid: suspend (String) -> com.trustweave.did.DidDocument?,
    private val localKeyStore: LocalKeyStore? = null // Optional local key store
) {

    private val keyCache = mutableMapOf<String, Secret>()

    /**
     * Resolves a secret by key ID.
     *
     * The didcomm-java library calls this to get secrets for encryption/decryption.
     *
     * TODO: Implement SecretResolver interface properly - the interface signature may differ
     * (e.g., suspend function, different return type, etc.)
     *
     * @param secretId Key ID (format: did:method:id#key-id or just key-id)
     * @return Secret, or null if not found
     */
    fun resolve(secretId: String): Secret? {
        return runBlocking {
            try {
                // Check cache first
                keyCache[secretId]?.let { return@runBlocking it }

                // Try local key store first (for DIDComm-specific keys)
                localKeyStore?.get(secretId)?.let {
                    keyCache[secretId] = it
                    return@runBlocking it
                }

                // Extract DID and key ID from secretId
                val (did, keyId) = parseSecretId(secretId)

                // Get public key from KMS
                val keyHandle = try {
                    kms.getPublicKey(com.trustweave.core.types.KeyId(keyId))
                } catch (e: Exception) {
                    return@runBlocking null
                }

                val publicKeyJwk = keyHandle.publicKeyJwk
                    ?: return@runBlocking null

                // Try to get private key
                // Strategy 1: Check if KMS supports export (requires extension)
                // Strategy 2: Use local key store (recommended for DIDComm)
                val privateKeyJwk = getPrivateKeyFromKms(keyId, keyHandle)
                    ?: return@runBlocking null // Can't proceed without private key

                // TODO: Fix Secret construction - didcomm-java library Secret constructor API needs verification
                // The Secret class from org.didcommx.didcomm.secret.Secret likely requires:
                // - kid (key ID) instead of id
                // - verificationMaterial instead of type/privateKeyJwk
                // This is a placeholder - needs proper API documentation or library source inspection
                throw UnsupportedOperationException(
                    "Secret construction from KMS is not yet implemented. " +
                    "The didcomm-java library Secret constructor API needs to be verified. " +
                    "Please use HybridKmsSecretResolver with a LocalKeyStore for DIDComm operations."
                )

                // Cache for future use would go here
                // keyCache[secretId] = secret

                // return secret
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Attempts to get private key from KMS.
     *
     * This is a placeholder - actual implementation depends on KMS capabilities:
     * - Some KMS support key export (AWS KMS with exportable keys)
     * - Some KMS don't support export (HSM-backed keys)
     * - Some KMS support temporary key material access
     *
     * @param keyId Key ID
     * @param keyHandle Key handle with public key
     * @return Private key JWK, or null if not available
     */
    private suspend fun getPrivateKeyFromKms(
        keyId: String,
        keyHandle: KeyHandle
    ): Map<String, Any?>? {
        // Strategy 1: Check if KMS supports key export
        // This would require a KMS-specific extension
        // Example: (kms as? ExportableKeyManagementService)?.exportPrivateKey(keyId)

        // Strategy 2: Use a local key store for DIDComm keys
        // DIDComm keys could be stored locally while other keys use cloud KMS

        // Strategy 3: Hybrid approach - use local keys for DIDComm operations
        // This is the recommended approach for production

        // For now, return null to indicate private key not available
        // In production, implement one of the strategies above
        return null
    }

    /**
     * Parses secret ID to extract DID and key ID.
     *
     * Format: "did:method:id#key-id" or "key-id"
     */
    private fun parseSecretId(secretId: String): Pair<String?, String> {
        return if (secretId.contains("#")) {
            val parts = secretId.split("#", limit = 2)
            Pair(parts[0], parts[1])
        } else {
            Pair(null, secretId)
        }
    }
}

