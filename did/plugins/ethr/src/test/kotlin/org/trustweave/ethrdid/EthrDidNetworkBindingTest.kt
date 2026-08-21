package org.trustweave.ethrdid

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import org.trustweave.anchor.AnchorRef
import org.trustweave.anchor.AnchorResult
import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolutionResult
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `did:ethr` carries the network in the identifier itself — `did:ethr:sepolia:0x…` and
 * `did:ethr:0x…` name different subjects even when the address is identical, because the DID
 * document is whatever the registry on *that* chain says.
 *
 * A resolver configured for one chain must not answer for another chain's DID. Doing so would
 * return one network's state under another network's identifier, and the caller has no way to
 * tell: the address matches, the document looks well-formed, and nothing in the result says the
 * answer came from somewhere else.
 */
class EthrDidNetworkBindingTest {
    private val address = "0x7E5F4552091A69125d5DfCb7b8C2659029395Bdf"

    private class NoopAnchorClient : BlockchainAnchorClient {
        override suspend fun writePayload(
            payload: JsonElement,
            mediaType: String,
        ): AnchorResult = throw TrustWeaveException.Unknown(message = "no chain in tests")

        override suspend fun readPayload(ref: AnchorRef): AnchorResult =
            throw TrustWeaveException.NotFound(resource = "anchor ${ref.txHash}")
    }

    /** Configured for Sepolia. */
    private fun sepoliaMethod(): EthrDidMethod =
        EthrDidMethod(
            kms =
                org.trustweave.testkit.kms
                    .InMemoryKeyManagementService(),
            anchorClient = NoopAnchorClient(),
            config = EthrDidConfig.sepolia("http://localhost:8545"),
        )

    @Test
    fun `a DID naming a different network is rejected as a network mismatch`() =
        runBlocking {
            val mainnetDid = Did("did:ethr:mainnet:$address")

            val result = sepoliaMethod().resolveDid(mainnetDid)

            assertTrue(result is DidResolutionResult.Failure, "Got: $result")
            assertTrue(
                result.toString().contains("network", ignoreCase = true),
                "The failure must say the DID belongs to another network rather than reporting " +
                    "it merely absent, got: $result",
            )
        }

    @Test
    fun `a DID with no network segment is rejected by a non-mainnet resolver`() =
        runBlocking {
            // An omitted network means mainnet in did:ethr, so a Sepolia resolver must not claim it.
            val result = sepoliaMethod().resolveDid(Did("did:ethr:$address"))

            assertTrue(result is DidResolutionResult.Failure, "Got: $result")
            assertTrue(
                result.toString().contains("network", ignoreCase = true),
                "An omitted network segment means mainnet and must not be answered by Sepolia, " +
                    "got: $result",
            )
        }
}
