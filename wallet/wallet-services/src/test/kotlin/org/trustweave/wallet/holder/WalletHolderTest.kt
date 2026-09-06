package org.trustweave.wallet.holder

import kotlinx.coroutines.runBlocking
import org.trustweave.did.identifiers.Did
import org.trustweave.testkit.credential.BasicWallet
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WalletHolderTest {
    @Test
    fun `unsupported offers and missing presentation service never mutate wallet`() =
        runBlocking<Unit> {
            val wallet = BasicWallet()
            val holder = WalletHolder(wallet, Did("did:key:holder"))
            assertFailsWith<IllegalArgumentException> { holder.acceptCredentialOffer("not-a-credential-offer") }
            assertFailsWith<IllegalArgumentException> { holder.handlePresentationRequest("openid4vp://authorize?nonce=test") }
            assertTrue(wallet.list().isEmpty())
        }
}
