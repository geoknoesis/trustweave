package org.trustweave.examples

import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.anchor.BlockchainAnchorRegistry
import org.trustweave.anchor.DefaultBlockchainAnchorRegistry
import org.trustweave.did.DidMethod
import org.trustweave.did.registry.DefaultDidMethodRegistry
import org.trustweave.did.registry.DidMethodRegistry

fun createTestDidRegistry(vararg methods: DidMethod): DidMethodRegistry {
    return DefaultDidMethodRegistry().apply {
        methods.forEach { register(it) }
    }
}

suspend fun DidMethodRegistry.canResolveDid(did: String): Boolean = try {
    resolve(did)
    true
} catch (_: Exception) {
    false
}

fun createTestBlockchainRegistry(vararg clients: Pair<String, BlockchainAnchorClient>): BlockchainAnchorRegistry {
    return DefaultBlockchainAnchorRegistry().apply {
        clients.forEach { (chainId, client) -> register(chainId, client) }
    }
}

fun BlockchainAnchorRegistry.hasChain(chainId: String): Boolean = get(chainId) != null

