package org.trustweave.wallet.exception

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WalletExceptionsTest {
    @Test
    fun `creation failure keeps only the context values that were supplied`() {
        val partial = WalletException.WalletCreationFailed(reason = "no disk")
        assertEquals("WALLET_CREATION_FAILED", partial.code)
        assertEquals("Wallet creation failed: no disk", partial.message)
        assertEquals(mapOf<String, Any?>("reason" to "no disk"), partial.context)

        val full = WalletException.WalletCreationFailed("boom", provider = "file", walletId = "w1")
        assertEquals(mapOf<String, Any?>("reason" to "boom", "provider" to "file", "walletId" to "w1"), full.context)
    }

    @Test
    fun `factory-not-configured defaults to guidance and accepts a custom reason`() {
        val default = WalletException.WalletFactoryNotConfigured()
        assertEquals("WALLET_FACTORY_NOT_CONFIGURED", default.code)
        assertTrue(default.message.contains("Wallet factory not configured"))
        assertEquals(default.message, default.context["reason"])

        val custom = WalletException.WalletFactoryNotConfigured("pick one")
        assertEquals("pick one", custom.message)
    }

    @Test
    fun `invalid holder DID names the DID and the reason`() {
        val error = WalletException.InvalidHolderDid("did:bad", "missing method")
        assertEquals("INVALID_HOLDER_DID", error.code)
        assertEquals("Invalid holder DID 'did:bad': missing method", error.message)
        assertEquals("did:bad", error.context["holderDid"])
        assertEquals("missing method", error.context["reason"])
    }

    @Test
    fun `storage error carries the operation and the underlying cause`() {
        val root = IllegalStateException("disk full")
        val error = WalletException.StorageError("store", "write failed", root)
        assertEquals("WALLET_STORAGE_ERROR", error.code)
        assertEquals("Wallet storage error during 'store': write failed", error.message)
        assertEquals("store", error.context["operation"])
        assertSame(root, error.cause)

        assertFalse(WalletException.StorageError("get", "x").cause != null)
    }

    @Test
    fun `data class equality distinguishes the failing detail`() {
        assertEquals(WalletException.InvalidHolderDid("a", "b"), WalletException.InvalidHolderDid("a", "b"))
        assertFalse(WalletException.InvalidHolderDid("a", "b") == WalletException.InvalidHolderDid("a", "c"))
    }
}
