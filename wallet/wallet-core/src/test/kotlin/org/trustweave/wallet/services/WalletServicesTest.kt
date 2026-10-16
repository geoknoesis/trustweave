package org.trustweave.wallet.services

import kotlinx.coroutines.test.runTest
import org.trustweave.wallet.MapWallet
import org.trustweave.wallet.Wallet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WalletServicesTest {
    private class RecordingFactory : WalletFactory {
        var args: List<Any?> = emptyList()
        val wallet: Wallet = MapWallet("created")

        override suspend fun create(
            providerName: String,
            walletId: String?,
            walletDid: String?,
            holderDid: String?,
            options: WalletCreationOptions,
        ): Wallet {
            args = listOf(providerName, walletId, walletDid, holderDid, options)
            return wallet
        }
    }

    @Test
    fun `createInMemory delegates to the inMemory provider with every argument`() =
        runTest {
            val factory = RecordingFactory()
            val options = WalletCreationOptions(label = "x")

            val result = factory.createInMemory("w1", "did:key:w", "did:key:h", options)

            assertSame(factory.wallet, result)
            assertEquals(listOf("inMemory", "w1", "did:key:w", "did:key:h", options), factory.args)
        }

    @Test
    fun `createInMemory defaults leave identifiers unset`() =
        runTest {
            val factory = RecordingFactory()
            factory.createInMemory()
            assertEquals("inMemory", factory.args[0])
            assertNull(factory.args[1])
            assertNull(factory.args[3])
            assertEquals(WalletCreationOptions(), factory.args[4])
        }

    @Test
    fun `legacy policy never consults the capability catalog`() {
        WalletCreationOptions().validateDeployment("module:that:does:not:exist")
    }

    @Test
    fun `supported-only rejects an experimental provider`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                WalletCreationOptions(deploymentPolicy = WalletDeploymentPolicy.SUPPORTED_ONLY)
                    .validateDeployment("wallet:plugins:file")
            }
        assertTrue(error.message!!.contains("experimental"), error.message)
    }

    @Test
    fun `experimental policy admits an experimental provider that offers the required operations`() {
        WalletCreationOptions(deploymentPolicy = WalletDeploymentPolicy.EXPERIMENTAL)
            .validateDeployment("wallet:plugins:file")
    }

    @Test
    fun `experimental policy still rejects stubs and unassessed modules`() {
        val options = WalletCreationOptions(deploymentPolicy = WalletDeploymentPolicy.EXPERIMENTAL)

        assertFailsWith<IllegalArgumentException> { options.validateDeployment("reference-wallet:android") }
        assertFailsWith<IllegalArgumentException> { options.validateDeployment("wallet:plugins:unknown") }
    }

    @Test
    fun `experimental policy rejects an assessed module lacking the required operations`() {
        assertFailsWith<IllegalArgumentException> {
            WalletCreationOptions(deploymentPolicy = WalletDeploymentPolicy.EXPERIMENTAL)
                .validateDeployment("wallet:wallet-core")
        }
    }
}
