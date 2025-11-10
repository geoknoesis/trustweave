package io.geoknoesis.vericore

import io.geoknoesis.vericore.anchor.BlockchainAnchorRegistry
import io.geoknoesis.vericore.credential.CredentialServiceRegistry
import io.geoknoesis.vericore.credential.proof.Ed25519ProofGenerator
import io.geoknoesis.vericore.credential.proof.ProofGeneratorRegistry
import io.geoknoesis.vericore.did.DidMethodRegistry
import io.geoknoesis.vericore.testkit.did.DidKeyMockMethod
import io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import io.geoknoesis.vericore.testkit.services.TestkitWalletFactory

/**
 * Convenience factory for creating opinionated VeriCore configurations that depend on the
 * in-memory testkit implementations. This is useful for samples, quick starts, and tests
 * but should not be used for production deployments.
 */
object VeriCoreDefaults {

    /**
     * Builds an in-memory configuration backed by the testkit components.
     */
    fun inMemoryTest(): VeriCoreConfig {
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

        return VeriCoreConfig(
            kms = kms,
            walletFactory = walletFactory,
            didRegistry = didRegistry,
            blockchainRegistry = BlockchainAnchorRegistry(),
            credentialRegistry = CredentialServiceRegistry.create(),
            proofRegistry = proofRegistry
        )
    }
}


