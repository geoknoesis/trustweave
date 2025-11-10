package io.geoknoesis.vericore.spi.services

/**
 * Service abstraction that allows core modules to interact with DID registries
 * without taking a direct dependency on vericore-did. Implementations are
 * provided at runtime (e.g., by vericore-did) and can be discovered via the
 * reflective adapter loader.
 */
interface DidRegistryService {
    suspend fun resolve(did: String): Any?
    suspend fun getDocument(did: String): Any?
    suspend fun register(method: Any)
}

/**
 * Service abstraction for trust registry lookups. Retained for backwards
 * compatibility; new code can provide its own implementation and expose it via
 * the adapter loader utilities.
 */
interface TrustRegistryService {
    suspend fun isTrustedIssuer(issuerDid: String, credentialType: String): Boolean
}


