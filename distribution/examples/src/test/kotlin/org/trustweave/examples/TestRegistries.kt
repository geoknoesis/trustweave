package org.trustweave.examples

import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.anchor.BlockchainAnchorRegistry
import org.trustweave.anchor.DefaultBlockchainAnchorRegistry
import org.trustweave.did.DidMethod
import org.trustweave.did.exception.DidException
import org.trustweave.did.registry.DidMethodRegistry

fun createTestDidRegistry(vararg methods: DidMethod): DidMethodRegistry {
    return DidMethodRegistry().apply {
        methods.forEach { register(it) }
    }
}

/**
 * Returns true if the DID resolves successfully, false if the DID is not found.
 * Re-throws any other exception so resolution infrastructure failures are not silently swallowed.
 */
suspend fun DidMethodRegistry.canResolveDid(did: String): Boolean = try {
    resolve(did)
    true
} catch (e: DidException.DidNotFound) {
    false
} catch (e: DidException.DidMethodNotRegistered) {
    false
}

fun createTestBlockchainRegistry(vararg clients: Pair<String, BlockchainAnchorClient>): BlockchainAnchorRegistry {
    return DefaultBlockchainAnchorRegistry().apply {
        clients.forEach { (chainId, client) -> register(chainId, client) }
    }
}

fun BlockchainAnchorRegistry.hasChain(chainId: String): Boolean = get(chainId) != null

