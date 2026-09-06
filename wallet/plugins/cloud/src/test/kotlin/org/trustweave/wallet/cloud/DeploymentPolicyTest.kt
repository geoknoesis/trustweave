package org.trustweave.wallet.cloud

import kotlinx.coroutines.runBlocking
import org.trustweave.wallet.services.WalletCreationOptions
import org.trustweave.wallet.services.WalletDeploymentPolicy
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DeploymentPolicyTest {
    @Test
    fun `production rejection precedes configuration or network access`() =
        runBlocking {
            val error =
                assertFailsWith<IllegalArgumentException> {
                    CloudWalletFactory().create(
                        "cloud",
                        options = WalletCreationOptions(deploymentPolicy = WalletDeploymentPolicy.SUPPORTED_ONLY),
                    )
                }
            assertTrue(error.message!!.contains("deployment maturity policy"))
        }
}
