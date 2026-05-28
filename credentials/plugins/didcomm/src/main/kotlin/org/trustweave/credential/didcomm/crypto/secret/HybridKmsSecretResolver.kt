package org.trustweave.credential.didcomm.crypto.secret

import kotlinx.coroutines.runBlocking
import org.didcommx.didcomm.secret.Secret
import org.didcommx.didcomm.secret.SecretResolver
import org.trustweave.kms.KeyManagementService
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * Hybrid [SecretResolver] that uses a [LocalKeyStore] for DIDComm ECDH keys.
 *
 * This is the recommended production approach:
 * - DIDComm key pairs (X25519, P-256) are generated and stored in [localKeyStore]
 *   (encrypted at rest via [EncryptedFileLocalKeyStore]) so private key material
 *   is available for ECDH key agreement.
 * - An optional [cloudKms] can be provided for other operations, but it is not
 *   consulted for DIDComm secrets since cloud KMS typically cannot export private keys.
 *
 * Populate [localKeyStore] via
 * [org.trustweave.credential.didcomm.crypto.rotation.KeyRotationManager].
 */
class HybridKmsSecretResolver(
    private val localKeyStore: LocalKeyStore,
    @Suppress("unused") private val cloudKms: KeyManagementService? = null,
) : SecretResolver {

    private val keyCache = ConcurrentHashMap<String, Secret>()

    override fun findKey(kid: String): Optional<Secret> = Optional.ofNullable(resolveKey(kid))

    override fun findKeys(kids: List<String>): Set<String> =
        kids.filter { findKey(it).isPresent }.toSet()

    /**
     * Clears the in-memory key cache (call after key rotation).
     */
    fun clearCache() = keyCache.clear()

    private fun resolveKey(secretId: String): Secret? = runBlocking {
        keyCache[secretId]?.let { return@runBlocking it }
        localKeyStore.get(secretId)?.also { keyCache[secretId] = it }
    }
}
