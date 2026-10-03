package org.trustweave.credential.didcomm.crypto.secret

import kotlinx.coroutines.CancellationException
import org.didcommx.didcomm.secret.Secret
import org.didcommx.didcomm.secret.SecretResolver
import org.slf4j.LoggerFactory
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.GetPublicKeyResult
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * [SecretResolver] that bridges KMS with the didcomm-java library.
 *
 * Always prefers [localKeyStore] for DIDComm keys (required for ECDH key agreement).
 * A KMS cannot supply them — cloud KMS implementations do not export private key material — so
 * a kid that is only in the KMS resolves to "not found", and a warning names the cause.
 *
 * **Threading:** didcomm-java calls [findKey] synchronously. Call [preload] from suspend code
 * before packing/unpacking so lookups are pure cache reads. A cache miss falls back to a bounded
 * blocking lookup on `Dispatchers.IO`, never on the caller's dispatcher.
 *
 * For full DIDComm functionality, populate [localKeyStore] with DIDComm key pairs
 * generated via [org.trustweave.credential.didcomm.crypto.rotation.KeyRotationManager].
 */
class KmsSecretResolver(
    private val kms: KeyManagementService,
    private val resolveDid: suspend (String) -> org.trustweave.did.model.DidDocument?,
    private val localKeyStore: LocalKeyStore? = null,
) : SecretResolver {
    private val logger = LoggerFactory.getLogger(KmsSecretResolver::class.java)
    private val keyCache = ConcurrentHashMap<String, Secret>()

    override fun findKey(kid: String): Optional<Secret> = Optional.ofNullable(resolveKey(kid))

    override fun findKeys(kids: List<String>): Set<String> = kids.filter { findKey(it).isPresent }.toSet()

    /**
     * Clears the in-memory key cache (call after key rotation).
     */
    fun clearCache() = keyCache.clear()

    /** Loads [kids] from the local key store into the cache without blocking. */
    suspend fun preload(kids: Collection<String>) {
        for (kid in kids) {
            lookupSuspending(kid)?.let { keyCache[kid] = it }
        }
    }

    private fun resolveKey(secretId: String): Secret? {
        keyCache[secretId]?.let { return it }
        return BlockingSecretLookup
            .lookup(secretId) { lookupSuspending(secretId) }
            ?.also { keyCache[secretId] = it }
    }

    private suspend fun lookupSuspending(secretId: String): Secret? {
        // Local key store (required for ECDH — cloud KMS cannot supply private keys).
        localKeyStore?.get(secretId)?.let { return it }

        // The KMS cannot hand out private keys, so the secret is unavailable either way. Check
        // whether the KMS knows the key only to make the misconfiguration diagnosable.
        val (_, keyId) = parseSecretId(secretId)
        val knownToKms =
            try {
                kms.getPublicKey(KeyId(keyId)) is GetPublicKeyResult.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.debug("KMS public-key lookup for '{}' failed: {}", keyId, e.message)
                false
            }
        if (knownToKms) {
            logger.warn(
                "DIDComm secret '{}' exists in the KMS, but its private key cannot be exported for ECDH; " +
                    "add it to the LocalKeyStore (see KeyRotationManager)",
                secretId,
            )
        }
        return null
    }

    private fun parseSecretId(secretId: String): Pair<String?, String> =
        if (secretId.contains("#")) {
            val parts = secretId.split("#", limit = 2)
            Pair(parts[0], parts[1])
        } else {
            Pair(null, secretId)
        }
}
