package org.trustweave.wallet

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WalletResourceOwnershipTest {
    @Test
    fun `default close leaves externally managed credential storage usable`() =
        runTest {
            val storage = MapWallet("external-storage")
            val wallet =
                object : Wallet, CredentialStorage by storage {
                    override val walletId = "borrowed-storage-wallet"
                }
            val credential = sampleCredential("credential")

            wallet.use {
                it.store(credential)
                assertTrue(it.capabilities.credentialStorage)
                assertEquals(1, it.getStatistics().totalCredentials)
            }

            assertFalse(storage.closed)
            assertSame(credential, storage.get("credential"))
            storage.close()
            assertTrue(storage.closed)
        }
}
