package org.trustweave.wallet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WalletDirectoryIdentityTest {
    @Test
    fun `unregister removes the registered DIDs even after the wallet identity changes`() {
        val directory = WalletDirectory()
        val wallet = MutableIdentityWallet()
        directory.register(wallet)
        wallet.currentWalletDid = "did:key:updated-wallet"
        wallet.currentHolderDid = "did:key:updated-holder"

        assertTrue(directory.unregister(wallet.walletId))
        assertNull(directory.getByDid("did:key:original-wallet"))
        assertNull(directory.getByDid("did:key:original-holder"))
        assertTrue(directory.getAll().isEmpty())
    }

    @Test
    fun `registering a changed identity replaces its original snapshot`() {
        val directory = WalletDirectory()
        val wallet = MutableIdentityWallet()
        directory.register(wallet)
        wallet.currentWalletDid = "did:key:updated-wallet"
        wallet.currentHolderDid = "did:key:updated-holder"
        directory.register(wallet)

        assertNull(directory.getByDid("did:key:original-wallet"))
        assertNull(directory.getByDid("did:key:original-holder"))
        assertSame(wallet, directory.getByDid(wallet.currentWalletDid))
        assertSame(wallet, directory.getByDid(wallet.currentHolderDid))
        assertEquals(1, directory.getAll().size)
    }

    @Test
    fun `failed identity getter leaves the previous registration untouched`() {
        val directory = WalletDirectory()
        val original = MutableIdentityWallet()
        directory.register(original)
        val replacement = MutableIdentityWallet()
        replacement.failOnRead = true

        assertFailsWith<IllegalStateException> { directory.register(replacement) }
        assertSame(original, directory.get(original.walletId))
        assertSame(original, directory.getByDid("did:key:original-wallet"))
        assertSame(original, directory.getByDid("did:key:original-holder"))
        assertEquals(1, directory.getAll().size)
    }

    @Test
    fun `unregister never calls wallet identity getters`() {
        val directory = WalletDirectory()
        val wallet = MutableIdentityWallet()
        directory.register(wallet)
        wallet.failOnRead = true

        assertTrue(directory.unregister(wallet.walletId))
        assertNull(directory.getByDid("did:key:original-wallet"))
        assertNull(directory.getByDid("did:key:original-holder"))
    }

    private class MutableIdentityWallet(
        identity: RichWallet = RichWallet("wallet", "did:key:original-wallet", "did:key:original-holder"),
    ) : Wallet by identity,
        DidManagement by identity {
        var currentWalletDid = identity.walletDid
        var currentHolderDid = identity.holderDid
        var failOnRead = false

        override val walletDid: String
            get() {
                check(!failOnRead) { "Identity unavailable" }
                return currentWalletDid
            }

        override val holderDid: String
            get() {
                check(!failOnRead) { "Identity unavailable" }
                return currentHolderDid
            }
    }
}
