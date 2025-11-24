package com.trustweave

import com.trustweave.anchor.BlockchainAnchorRegistry
import com.trustweave.credential.CredentialServiceRegistry
import com.trustweave.credential.proof.Ed25519ProofGenerator
import com.trustweave.credential.proof.ProofGeneratorRegistry
import com.trustweave.did.registry.DidMethodRegistry
import com.trustweave.testkit.did.DidKeyMockMethod
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import com.trustweave.testkit.services.TestkitWalletFactory

/**
 * Convenience factory for creating opinionated TrustWeave configurations that depend on the
 * in-memory testkit implementations. This is useful for samples, quick starts, and tests
 * but should not be used for production deployments.
 */
object TrustWeaveDefaults {

    /**
     * Builds an in-memory configuration backed by the testkit components.
     * 
     * This is useful for development, testing, and quick starts.
     * For production, use [TrustWeave.create] with custom configuration.
     */
    fun inMemory(): TrustWeaveConfig {
        val kms = InMemoryKeyManagementService()
        val walletFactory = TestkitWalletFactory()
        val didRegistry = DidMethodRegistry()
        val didMethod = DidKeyMockMethod(kms)
        didRegistry.register(didMethod)
        val proofRegistry = ProofGeneratorRegistry().apply {
            register(
                Ed25519ProofGenerator(
                    signer = { data, keyId ->
                        kms.sign(keyId, data)
                    }
                )
            )
        }

        return TrustWeaveConfig(
            kms = kms,
            walletFactory = walletFactory,
            didRegistry = didRegistry,
            blockchainRegistry = BlockchainAnchorRegistry(),
            credentialRegistry = CredentialServiceRegistry.create(),
            proofRegistry = proofRegistry
        )
    }
}


