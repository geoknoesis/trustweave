package org.trustweave.testkit.services

import kotlinx.coroutines.runBlocking
import org.trustweave.wallet.services.WalletCreationOptions
import org.trustweave.wallet.services.WalletDeploymentPolicy
import kotlin.test.Test
import kotlin.test.assertFailsWith

class TestkitWalletDeploymentTest {
    @Test
    fun `test doubles cannot bypass deployment policy`() =
        runBlocking<Unit> {
            for (policy in listOf(WalletDeploymentPolicy.SUPPORTED_ONLY, WalletDeploymentPolicy.EXPERIMENTAL)) {
                for (provider in listOf("inMemory", "basic")) {
                    assertFailsWith<IllegalArgumentException> {
                        TestkitWalletFactory().create(
                            provider,
                            "wallet",
                            "did:key:wallet",
                            "did:key:holder",
                            WalletCreationOptions(deploymentPolicy = policy),
                        )
                    }
                }
                assertFailsWith<IllegalArgumentException> {
                    TestkitWalletFactory().createInMemory(
                        "wallet",
                        "did:key:wallet",
                        "did:key:holder",
                        WalletCreationOptions(deploymentPolicy = policy),
                    )
                }
            }
        }
}
