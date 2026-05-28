package org.trustweave.credential.didcomm.crypto.secret

import kotlinx.coroutines.runBlocking
import org.didcommx.didcomm.secret.Secret
import org.didcommx.didcomm.secret.SecretResolver
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.KeyManagementService
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * [SecretResolver] that bridges KMS with the didcomm-java library.
 *
 * Always prefers [localKeyStore] for DIDComm keys (required for ECDH key agreement).
 * Falls back to KMS for public-key lookup but returns `null` if no private key is
 * available — cloud KMS implementations typically do not export private key material.
 *
 * For full DIDComm functionality, populate [localKeyStore] with DIDComm key pairs
 * generated via [org.trustweave.credential.didcomm.crypto.rotation.KeyRotationManager].
 */
class KmsSecretResolver(
    private val kms: KeyManagementService,
    private val resolveDid: suspend (String) -> org.trustweave.did.model.DidDocument?,
    private val localKeyStore: LocalKeyStore? = null,
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
        // 1. In-memory cache
        keyCache[secretId]?.let { return@runBlocking it }

        // 2. Local key store (required for ECDH — cloud KMS cannot supply private keys)
        localKeyStore?.get(secretId)?.let { secret ->
            keyCache[secretId] = secret
            return@runBlocking secret
        }

        // 3. KMS can supply public keys but not private keys for ECDH.
        //    Validate the key exists in KMS so callers know the kid is known,
        //    but return null because we cannot complete ECDH without the private key.
        val (_, keyId) = parseSecretId(secretId)
        runCatching {
            kms.getPublicKey(KeyId(keyId))
        }.getOrNull()

        // Private key not available — return null so the caller falls through.
        null
    }

    private fun parseSecretId(secretId: String): Pair<String?, String> =
        if (secretId.contains("#")) {
            val parts = secretId.split("#", limit = 2)
            Pair(parts[0], parts[1])
        } else {
            Pair(null, secretId)
        }
}
