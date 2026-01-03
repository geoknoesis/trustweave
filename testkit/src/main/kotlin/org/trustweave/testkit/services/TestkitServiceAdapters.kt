package org.trustweave.testkit.services

import org.trustweave.credential.revocation.CredentialRevocationManager
import org.trustweave.credential.revocation.RevocationManagers
import org.trustweave.revocation.services.StatusListRegistryFactory
import org.trustweave.testkit.trust.InMemoryTrustRegistry
import org.trustweave.trust.TrustRegistry
import org.trustweave.trust.services.TrustRegistryFactory

/**
 * Factory implementations for testkit services.
 *
 * Note: KMS, DID methods, and Anchor clients are now auto-discovered via SPI.
 * Only factories for services without SPI equivalents are kept here.
 */

/**
 * StatusListRegistry Factory implementation.
 */
class TestkitStatusListRegistryFactory : StatusListRegistryFactory {
    override suspend fun create(providerName: String): CredentialRevocationManager {
        if (providerName == "inMemory") {
            return RevocationManagers.default()
        }
        throw IllegalStateException(
            "StatusListManager provider '$providerName' not found. " +
            "Use 'inMemory' for testing."
        )
    }
}

/**
 * TrustRegistry Factory implementation.
 */
class TestkitTrustRegistryFactory : TrustRegistryFactory {
    override suspend fun create(providerName: String): TrustRegistry {
        if (providerName == "inMemory") {
            return InMemoryTrustRegistry()
        }
        throw IllegalStateException(
            "TrustRegistry provider '$providerName' not found. " +
            "Use 'inMemory' for testing."
        )
    }
}


