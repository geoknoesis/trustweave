package com.geoknoesis.vericore.examples

import com.geoknoesis.vericore.anchor.BlockchainAnchorClient
import com.geoknoesis.vericore.anchor.BlockchainAnchorRegistry
import com.geoknoesis.vericore.anchor.DefaultBlockchainAnchorRegistry
import com.geoknoesis.vericore.credential.proof.ProofGenerator
import com.geoknoesis.vericore.credential.proof.ProofGeneratorRegistry
import com.geoknoesis.vericore.did.DefaultDidMethodRegistry
import com.geoknoesis.vericore.did.DidMethod
import com.geoknoesis.vericore.did.DidMethodRegistry

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
    com.geoknoesis.vericore.credential.proof.ProofGeneratorRegistry().also { registry ->
        generators.forEach { registry.register(it) }
    }
