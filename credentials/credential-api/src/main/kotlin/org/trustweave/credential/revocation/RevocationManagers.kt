package org.trustweave.credential.revocation

import org.trustweave.credential.revocation.internal.InMemoryCredentialRevocationManager

/**
 * Factory object for creating CredentialRevocationManager instances.
 * 
 * Provides controlled construction of credential revocation manager instances.
 */
object RevocationManagers {
    /**
     * Create a default in-memory credential revocation manager.
     * 
     * Suitable for testing and development. For production use,
     * consider implementing a persistent manager.
     */
    fun default(): CredentialRevocationManager {
        return InMemoryCredentialRevocationManager()
    }
}

