package com.trustweave.examples

import com.trustweave.anchor.BlockchainAnchorClient
import com.trustweave.anchor.BlockchainAnchorRegistry
import com.trustweave.anchor.DefaultBlockchainAnchorRegistry
import com.trustweave.credential.proof.ProofGenerator
import com.trustweave.credential.proof.ProofGeneratorRegistry
import com.trustweave.did.DidMethod
import com.trustweave.did.registry.DefaultDidMethodRegistry
import com.trustweave.did.registry.DidMethodRegistry

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

fun createTestProofRegistry(vararg generators: ProofGenerator): ProofGeneratorRegistry =
    com.trustweave.credential.proof.ProofGeneratorRegistry().also { registry ->
        generators.forEach { registry.register(it) }
    }
