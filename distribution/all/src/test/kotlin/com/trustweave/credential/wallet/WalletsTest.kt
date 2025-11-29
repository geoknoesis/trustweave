package com.trustweave.credential.wallet

import com.trustweave.testkit.credential.InMemoryWallet
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Tests for Wallets factory.
 */
class WalletsTest {

    @Test
    fun `test inMemory creates wallet with holder DID`() {
        val wallet = Wallets.inMemory(holderDid = "did:key:holder")

        assertNotNull(wallet)
        assertTrue(wallet is InMemoryWallet)
        assertEquals("did:key:holder", (wallet as InMemoryWallet).holderDid)
        assertNotNull(wallet.walletId)
    }

    @Test
    fun `test inMemory with custom wallet ID`() {
        val wallet = Wallets.inMemory(
            walletId = "my-wallet-123",
            holderDid = "did:key:holder"
        )

        assertNotNull(wallet)
        assertEquals("my-wallet-123", wallet.walletId)
        assertTrue(wallet is InMemoryWallet)
        assertEquals("did:key:holder", (wallet as InMemoryWallet).holderDid)
    }

    @Test
    fun `test inMemory auto-generates wallet ID if not provided`() {
        val wallet1 = Wallets.inMemory(holderDid = "did:key:holder1")
        val wallet2 = Wallets.inMemory(holderDid = "did:key:holder2")

        assertNotEquals(wallet1.walletId, wallet2.walletId)
        assertTrue(wallet1.walletId.isNotEmpty())
        assertTrue(wallet2.walletId.isNotEmpty())
    }

    @Test
    fun `test inMemoryWithWalletDid creates wallet with separate DIDs`() {
        val wallet = Wallets.inMemoryWithWalletDid(
            walletDid = "did:key:institution",
            holderDid = "did:key:employee"
        )

        assertNotNull(wallet)
        assertTrue(wallet is InMemoryWallet)
        assertEquals("did:key:employee", (wallet as InMemoryWallet).holderDid)
        assertEquals("did:key:institution", wallet.walletId)
    }

    @Test
    fun `test inMemoryWithWalletDid uses walletDid as wallet ID by default`() {
        val wallet = Wallets.inMemoryWithWalletDid(
            walletDid = "did:key:institution",
            holderDid = "did:key:employee"
        )

        assertEquals("did:key:institution", wallet.walletId)
    }

    @Test
    fun `test inMemoryWithWalletDid with custom wallet ID`() {
        val wallet = Wallets.inMemoryWithWalletDid(
            walletDid = "did:key:institution",
            holderDid = "did:key:employee",
            walletId = "custom-id"
        )

        assertEquals("custom-id", wallet.walletId)
    }
}

