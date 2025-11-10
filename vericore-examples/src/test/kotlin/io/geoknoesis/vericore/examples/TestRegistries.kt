package io.geoknoesis.vericore.examples

import io.geoknoesis.vericore.anchor.BlockchainAnchorClient
import io.geoknoesis.vericore.anchor.BlockchainAnchorRegistry
import io.geoknoesis.vericore.anchor.DefaultBlockchainAnchorRegistry
import io.geoknoesis.vericore.credential.proof.ProofGenerator
import io.geoknoesis.vericore.credential.proof.ProofGeneratorRegistry
import io.geoknoesis.vericore.did.DefaultDidMethodRegistry
import io.geoknoesis.vericore.did.DidMethod
import io.geoknoesis.vericore.did.DidMethodRegistry

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
    io.geoknoesis.vericore.credential.proof.ProofGeneratorRegistry().also { registry ->
        generators.forEach { registry.register(it) }
    }
