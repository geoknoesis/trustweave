package org.trustweave.anchor.algorand

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.trustweave.anchor.exceptions.TreasuryException
import org.trustweave.anchor.payment.FeeStrategy
import org.trustweave.anchor.payment.PaymentContext
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Verifies the fee-payer atomic-group path consults the [SponsorRegistry]
 * before constructing the sponsor txn. We don't reach the Algorand network:
 * the stub returns `null`, so the plugin short-circuits with
 * [TreasuryException.SponsorNotAllowed] — that's all we need to assert the
 * registry was invoked with the right sponsor DID.
 */
class AlgorandSponsoredFeeTest {

    private class RecordingSponsorRegistry : SponsorRegistry {
        val resolveCalls = mutableListOf<String>()

        override fun resolve(sponsorDid: String): SponsorEntry? {
            resolveCalls += sponsorDid
            return null
        }
    }

    private fun base64Seed(byte: Byte): String {
        val seed = ByteArray(32) { byte }
        return Base64.getEncoder().encodeToString(seed)
    }

    @Test
    fun `sponsored fee strategy consults SponsorRegistry for sponsor DID`() = runBlocking<Unit> {
        val registry = RecordingSponsorRegistry()
        val chainId = AlgorandBlockchainAnchorClient.TESTNET
        val client = AlgorandBlockchainAnchorClient(
            chainId = chainId,
            options = mapOf("privateKey" to base64Seed(0x11)),
            sponsorRegistry = registry,
        )

        val payload = buildJsonObject { put("ctx", "sponsored") }
        val sponsorDid = "did:example:sponsor-acme"
        val ctx = PaymentContext(
            domainId = "domain.test",
            payerDid = sponsorDid,
            chainId = chainId,
            feeStrategy = FeeStrategy.Sponsored(sponsorDid),
        )

        val ex = assertFailsWith<TreasuryException.SponsorNotAllowed> {
            client.writePayload(payload, ctx)
        }

        assertEquals(listOf(sponsorDid), registry.resolveCalls)
        assertEquals("domain.test", ex.domainId)
        assertEquals(sponsorDid, ex.sponsorDid)
    }

    @Test
    fun `non-sponsored strategies bypass SponsorRegistry`() = runBlocking<Unit> {
        val registry = RecordingSponsorRegistry()
        val chainId = AlgorandBlockchainAnchorClient.TESTNET
        // No privateKey → account is null → managed write throws IllegalStateException
        // before any sponsor lookup. We assert the registry stayed untouched.
        val client = AlgorandBlockchainAnchorClient(
            chainId = chainId,
            options = emptyMap(),
            sponsorRegistry = registry,
        )

        val ctx = PaymentContext(
            domainId = "domain.test",
            payerDid = "did:example:payer",
            chainId = chainId,
            feeStrategy = FeeStrategy.DomainPays,
        )

        runCatching { client.writePayload(buildJsonObject { put("k", "v") }, ctx) }
        assertTrue(registry.resolveCalls.isEmpty(), "DomainPays must not consult SponsorRegistry")
    }
}
