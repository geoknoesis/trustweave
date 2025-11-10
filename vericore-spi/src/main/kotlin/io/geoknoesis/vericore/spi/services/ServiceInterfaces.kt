package io.geoknoesis.vericore.spi.services

/**
 * Service abstraction for trust registry lookups. Retained for backwards
 * compatibility; new code can provide its own implementation and expose it via
 * the adapter loader utilities.
 */
interface TrustRegistryService {
    suspend fun isTrustedIssuer(issuerDid: String, credentialType: String): Boolean
}


