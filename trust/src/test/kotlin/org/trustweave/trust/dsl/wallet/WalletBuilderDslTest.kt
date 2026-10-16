package org.trustweave.trust.dsl.wallet

import kotlinx.coroutines.runBlocking
import org.trustweave.did.identifiers.Did
import org.trustweave.testkit.credential.InMemoryWallet
import org.trustweave.trust.context.WalletDslContext
import org.trustweave.wallet.Wallet
import org.trustweave.wallet.exception.WalletException
import org.trustweave.wallet.services.WalletCreationOptions
import org.trustweave.wallet.services.WalletFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WalletBuilderDslTest {
    private class CapturingFactory : WalletFactory {
        var provider: String? = null
        var walletId: String? = null
        var walletDid: String? = null
        var holderDid: String? = null
        var options: WalletCreationOptions? = null

        override suspend fun create(
            providerName: String,
            walletId: String?,
            walletDid: String?,
            holderDid: String?,
            options: WalletCreationOptions,
        ): Wallet {
            this.provider = providerName
            this.walletId = walletId
            this.walletDid = walletDid
            this.holderDid = holderDid
            this.options = options
            return InMemoryWallet(walletId!!, walletDid, holderDid)
        }
    }

    private fun context(factory: WalletFactory?) =
        object : WalletDslContext {
            override fun getWalletFactory(): WalletFactory? = factory
        }

    private fun builder(factory: WalletFactory? = CapturingFactory()) = WalletBuilder(context(factory))

    @Test
    fun `defaults produce an in-memory wallet with generated identifiers`() =
        runBlocking {
            val factory = CapturingFactory()

            val wallet = builder(factory).build()

            assertEquals("inMemory", factory.provider)
            assertEquals(factory.walletId, wallet.walletId)
            assertTrue(factory.walletId!!.isNotBlank())
            assertEquals("did:key:test-wallet-${factory.walletId}", factory.walletDid)
            assertNull(factory.holderDid)
        }

    @Test
    fun `explicit identifiers provider and options reach the factory`() =
        runBlocking {
            val factory = CapturingFactory()
            val b = WalletBuilder(context(factory))
            b.id("w-1")
            b.holder("did:key:holder")
            b.walletDid("did:key:wallet")
            b.provider("custom")
            b.option("region", "eu")
            b.enableOrganization()
            b.enablePresentation()

            b.build()

            assertEquals("custom", factory.provider)
            assertEquals("w-1", factory.walletId)
            assertEquals("did:key:wallet", factory.walletDid)
            assertEquals("did:key:holder", factory.holderDid)
            val options = factory.options!!
            assertTrue(options.enableOrganization && options.enablePresentation)
            assertEquals("eu", options.additionalProperties["region"])
        }

    @Test
    fun `provider shortcuts and a typed holder DID are honoured`() =
        runBlocking {
            val factory = CapturingFactory()
            val b = WalletBuilder(context(factory))
            b.basic()
            b.holder(Did("did:key:typed"))
            b.build()
            assertEquals("basic", factory.provider)
            assertEquals("did:key:typed", factory.holderDid)

            val again = WalletBuilder(context(factory))
            again.provider("other")
            again.inMemory()
            again.build()
            assertEquals("inMemory", factory.provider)
        }

    @Test
    fun `holder and wallet DIDs must be DIDs`() {
        val b = builder()

        assertTrue(assertFailsWith<IllegalArgumentException> { b.holder("") }.message!!.contains("cannot be blank"))
        assertTrue(
            assertFailsWith<IllegalArgumentException> { b.holder("https://example.org") }.message!!.contains("must start with 'did:'"),
        )
        assertTrue(assertFailsWith<IllegalArgumentException> { b.walletDid("  ") }.message!!.contains("cannot be blank"))
        assertTrue(assertFailsWith<IllegalArgumentException> { b.walletDid("wallet-1") }.message!!.contains("Wallet DID must start with"))
    }

    @Test
    fun `building without a configured factory fails with the wallet factory error`() {
        val error = assertFailsWith<WalletException.WalletFactoryNotConfigured> { runBlocking { builder(null).build() } }

        assertEquals("WALLET_FACTORY_NOT_CONFIGURED", error.code)
    }

    @Test
    fun `wallets from the factory are instrumented without changing their identity`() =
        runBlocking {
            val wallet = builder().build()

            assertTrue(wallet.walletId.isNotBlank())
            assertEquals(0, wallet.list().size)
        }
}
