package org.trustweave.polygondid

import kotlinx.coroutines.runBlocking
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.identifiers.Did
import org.trustweave.did.representation.DidDocumentJsonProducer
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PolygonDidMethodTest {
    private val config = PolygonDidConfig.mainnet("http://localhost:8545")

    private fun method() =
        PolygonDidMethod(InMemoryKeyManagementService(), InMemoryBlockchainAnchorClient(chainId = config.chainId), config)

    @Test
    fun `a created DID is a did-polygon address DID and resolves`() =
        runBlocking<Unit> {
            val method = method()
            val document = method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.SECP256K1))

            assertTrue(Regex("^did:polygon:mainnet:0x[0-9a-fA-F]{40}$").matches(document.id.value), document.id.value)
            val result = assertIs<DidResolutionResult.Success>(method.resolveDid(document.id))
            assertEquals(document.id, result.document.id)
        }

    @Test
    fun `a DID nobody anchored is not found rather than resolved from a fake transaction`() =
        runBlocking<Unit> {
            val result = method().resolveDid(Did("did:polygon:mainnet:0x7E5F4552091A69125d5DfCb7b8C2659029395Bdf"))
            assertIs<DidResolutionResult.Failure.NotFound>(result)
        }

    @Test
    fun `config toString redacts the private key`() {
        val key = "0x" + "cd".repeat(32)
        val text = PolygonDidConfig.mainnet(privateKey = key).toString()
        assertFalse(key.removePrefix("0x") in text, text)
        assertTrue("<redacted>" in text, text)
    }

    private fun assertNoEthrLeak(document: org.trustweave.did.model.DidDocument) {
        val did = document.id.value
        assertTrue(document.verificationMethod.isNotEmpty())
        document.verificationMethod.forEach {
            assertTrue(it.id.value.startsWith("$did#"), "vm id ${it.id.value}")
            assertEquals(did, it.controller.value)
        }
        val refs =
            document.authentication + document.assertionMethod + document.keyAgreement +
                document.capabilityInvocation + document.capabilityDelegation
        assertTrue(refs.isNotEmpty())
        refs.forEach { assertTrue(it.value.startsWith("$did#"), "ref ${it.value}") }
        assertFalse("did:ethr" in document.toString(), document.toString())
    }

    @Test
    fun `created and resolved documents are consistently rewritten to did-polygon`() =
        runBlocking<Unit> {
            val method = method()
            val created = method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.SECP256K1))
            assertNoEthrLeak(created)
            val resolved = assertIs<DidResolutionResult.Success>(method.resolveDid(created.id))
            assertNoEthrLeak(resolved.document)
        }

    private class SwappingAnchorClient(
        private val inner: InMemoryBlockchainAnchorClient,
    ) : org.trustweave.anchor.BlockchainAnchorClient {
        var swapTo: org.trustweave.did.model.DidDocument? = null

        override suspend fun writePayload(
            payload: kotlinx.serialization.json.JsonElement,
            mediaType: String,
        ) = inner.writePayload(payload, mediaType)

        override suspend fun readPayload(ref: org.trustweave.anchor.AnchorRef): org.trustweave.anchor.AnchorResult {
            val real = inner.readPayload(ref)
            val other = swapTo ?: return real
            val json = DidDocumentJsonProducer.toJsonObject(other, true)
            return real.copy(payload = json)
        }
    }

    @Test
    fun `an anchored document for a different DID is rejected`() =
        runBlocking<Unit> {
            val client = SwappingAnchorClient(InMemoryBlockchainAnchorClient(chainId = config.chainId))
            val method = PolygonDidMethod(InMemoryKeyManagementService(), client, config)
            val created = method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.SECP256K1))
            client.swapTo =
                org.trustweave.did.model.DidDocument(
                    id = Did("did:ethr:mainnet:0x7E5F4552091A69125d5DfCb7b8C2659029395Bdf"),
                )

            val result = method.resolveDid(created.id)

            assertTrue(result !is DidResolutionResult.Success, "a foreign document must never resolve: $result")
        }

    @Test
    fun `a DID naming a different network is refused with a clear network mismatch`() =
        runBlocking<Unit> {
            val result = method().resolveDid(Did("did:polygon:amoy:0x7E5F4552091A69125d5DfCb7b8C2659029395Bdf"))
            val failure = assertIs<DidResolutionResult.Failure>(result)
            assertTrue(failure.toString().contains("network mismatch"), failure.toString())
        }

    @Test
    fun `a DID without a network segment means the polygon mainnet`() =
        runBlocking<Unit> {
            // mainnet-configured: not a network error, just not anchored.
            val result = method().resolveDid(Did("did:polygon:0x7E5F4552091A69125d5DfCb7b8C2659029395Bdf"))
            assertIs<DidResolutionResult.Failure.NotFound>(result)
            // ...but a testnet-configured resolver refuses to answer for it.
            val amoy = PolygonDidConfig.mainnet("http://localhost:8545").copy(network = "amoy", chainId = "eip155:80002")
            val anchors = InMemoryBlockchainAnchorClient(chainId = amoy.chainId)
            val testnet = PolygonDidMethod(InMemoryKeyManagementService(), anchors, amoy)
            val refused = testnet.resolveDid(Did("did:polygon:0x7E5F4552091A69125d5DfCb7b8C2659029395Bdf"))
            assertTrue(refused.toString().contains("network mismatch"), refused.toString())
        }

    @Test
    fun `a configuration whose chain id and network disagree is rejected`() {
        val bad = PolygonDidConfig.mainnet("http://localhost:8545").copy(network = "sepolia")
        val e =
            kotlin.test.assertFailsWith<IllegalArgumentException> {
                PolygonDidMethod(InMemoryKeyManagementService(), InMemoryBlockchainAnchorClient(chainId = bad.chainId), bad)
            }
        assertTrue(e.message!!.contains("chainId"), e.message)
    }
}
