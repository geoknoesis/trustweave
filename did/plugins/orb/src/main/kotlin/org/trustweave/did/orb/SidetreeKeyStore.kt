package org.trustweave.did.orb

import java.util.concurrent.ConcurrentHashMap

/**
 * Persists the Sidetree update + recovery keypairs associated with each DID this
 * plugin instance has created.
 *
 * Sidetree update and deactivate operations are cryptographically chained: each
 * operation must reveal the *previous* update / recovery public key (whose hash
 * matches the on-ledger commitment) and present a JWS signed by the corresponding
 * private key. Without access to those keys, no further updates or deactivations
 * are possible.
 *
 * Implementations may persist to disk, a database, or a secret store. The default
 * [InMemorySidetreeKeyStore] keeps keys in process memory and is appropriate for
 * tests and short-lived applications only.
 */
interface SidetreeKeyStore {
    suspend fun put(didSuffix: String, keys: SidetreeKeyPair)
    suspend fun get(didSuffix: String): SidetreeKeyPair?
    suspend fun remove(didSuffix: String)
}

/**
 * Update and recovery keypairs (private + public JWK) currently anchored for a
 * single Sidetree DID.
 *
 * The *private* JWKs contain the raw key material (`d` component for EC keys) and
 * MUST be protected; the *public* JWKs are also kept so the next update/deactivate
 * can compute the on-ledger `revealValue` without re-deriving them.
 */
data class SidetreeKeyPair(
    val updatePrivateJwk: Map<String, Any?>,
    val updatePublicJwk: Map<String, Any?>,
    val recoveryPrivateJwk: Map<String, Any?>,
    val recoveryPublicJwk: Map<String, Any?>,
)

/**
 * In-memory implementation used by default. Suitable for tests and ephemeral
 * applications. NOT suitable for production: a process restart loses the keys
 * and the DIDs become un-updatable.
 */
class InMemorySidetreeKeyStore : SidetreeKeyStore {
    private val store = ConcurrentHashMap<String, SidetreeKeyPair>()

    override suspend fun put(didSuffix: String, keys: SidetreeKeyPair) {
        store[didSuffix] = keys
    }

    override suspend fun get(didSuffix: String): SidetreeKeyPair? = store[didSuffix]

    override suspend fun remove(didSuffix: String) {
        store.remove(didSuffix)
    }
}
