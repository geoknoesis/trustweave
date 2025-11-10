package io.geoknoesis.vericore

import io.geoknoesis.vericore.anchor.BlockchainAnchorRegistry
import io.geoknoesis.vericore.credential.CredentialServiceRegistry
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

        return VeriCoreConfig(
            kms = kms,
            walletFactory = walletFactory,
            didRegistry = didRegistry,
            blockchainRegistry = BlockchainAnchorRegistry(),
            credentialRegistry = CredentialServiceRegistry.create()
        )
    }
}


