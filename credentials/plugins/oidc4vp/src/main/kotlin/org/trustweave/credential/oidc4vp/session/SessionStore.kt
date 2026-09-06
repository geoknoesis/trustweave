package org.trustweave.credential.oidc4vp.session

import org.trustweave.core.util.ExpiringStore
import org.trustweave.credential.oidc4vp.models.PermissionRequest

/**
 * Pluggable store for in-flight OID4VP [PermissionRequest] objects keyed by `requestId`.
 *
 * The default [InMemorySessionStore] is sufficient for single-process deployments.
 * Pass a DB-backed implementation to both [org.trustweave.credential.oidc4vp.Oidc4VpService]
 * and [org.trustweave.credential.oidc4vp.exchange.Oidc4VpExchangeProtocol] when requests
 * must survive restarts or be shared across nodes.
 */
interface SessionStore {
    suspend fun put(
        requestId: String,
        request: PermissionRequest,
    )

    suspend fun get(requestId: String): PermissionRequest?

    suspend fun remove(requestId: String)
}

/** Thread-safe in-memory [SessionStore]. Requests are lost on process restart. */
class InMemorySessionStore(
    capacity: Int = 1_000,
    ttlMillis: Long = 300_000,
    clock: java.time.Clock = java.time.Clock.systemUTC(),
) : SessionStore {
    private val store = ExpiringStore<PermissionRequest>(clock, ttlMillis, capacity)

    override suspend fun put(
        requestId: String,
        request: PermissionRequest,
    ) {
        store[requestId] = request
    }

    override suspend fun get(requestId: String): PermissionRequest? = store[requestId]

    override suspend fun remove(requestId: String) {
        store.remove(requestId)
    }
}
