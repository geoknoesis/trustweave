package org.trustweave.credential.oidc4vp.session

import org.trustweave.credential.oidc4vp.models.PermissionRequest
import java.util.concurrent.ConcurrentHashMap

/**
 * Pluggable store for in-flight OID4VP [PermissionRequest] objects keyed by `requestId`.
 *
 * The default [InMemorySessionStore] is sufficient for single-process deployments.
 * Pass a DB-backed implementation to both [org.trustweave.credential.oidc4vp.Oidc4VpService]
 * and [org.trustweave.credential.oidc4vp.exchange.Oidc4VpExchangeProtocol] when requests
 * must survive restarts or be shared across nodes.
 */
interface SessionStore {
    suspend fun put(requestId: String, request: PermissionRequest)
    suspend fun get(requestId: String): PermissionRequest?
    suspend fun remove(requestId: String)
}

/** Thread-safe in-memory [SessionStore]. Requests are lost on process restart. */
class InMemorySessionStore : SessionStore {
    private val store = ConcurrentHashMap<String, PermissionRequest>()

    override suspend fun put(requestId: String, request: PermissionRequest) {
        store[requestId] = request
    }

    override suspend fun get(requestId: String): PermissionRequest? = store[requestId]

    override suspend fun remove(requestId: String) {
        store.remove(requestId)
    }
}
