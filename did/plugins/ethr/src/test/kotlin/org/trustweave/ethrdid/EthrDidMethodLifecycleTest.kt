package org.trustweave.ethrdid

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import org.trustweave.anchor.AnchorRef
import org.trustweave.anchor.AnchorResult
import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EthrDidMethodLifecycleTest {
    private val rpc = "http://localhost:8545"

    private fun method(
        anchorClient: BlockchainAnchorClient = InMemoryBlockchainAnchorClient(chainId = "eip155:11155111"),
        config: EthrDidConfig = EthrDidConfig.sepolia(rpc),
    ) = EthrDidMethod(InMemoryKeyManagementService(), anchorClient, config)

    @Test
    fun `a created DID resolves from its anchor transaction`() =
        runBlocking<Unit> {
            val method = method()
            val document = method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.SECP256K1))

            val result = assertIs<DidResolutionResult.Success>(method.resolveDid(document.id))
            assertEquals(document.id, result.document.id)
        }

    @Test
    fun `a failed anchor fails creation instead of storing the DID locally`() =
        runBlocking<Unit> {
            val failing =
                object : BlockchainAnchorClient {
                    override suspend fun writePayload(
                        payload: JsonElement,
                        mediaType: String,
                    ): AnchorResult = throw IllegalStateException("chain down")

                    override suspend fun readPayload(ref: AnchorRef): AnchorResult = throw IllegalStateException("chain down")
                }

            assertFailsWith<Exception> { method(failing).createDid(DidCreationOptions(algorithm = KeyAlgorithm.SECP256K1)) }
        }

    @Test
    fun `an unknown DID is not found`() =
        runBlocking<Unit> {
            val result = method().resolveDid(Did("did:ethr:sepolia:0x7E5F4552091A69125d5DfCb7b8C2659029395Bdf"))
            assertIs<DidResolutionResult.Failure.NotFound>(result)
        }

    @Test
    fun `a malformed private key fails construction`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                method(config = EthrDidConfig.sepolia(rpc, privateKey = "0xnot-a-key"))
            }
        assertFalse(error.message!!.contains("not-a-key"), "the error must not echo the key")
    }

    @Test
    fun `a well-formed private key is accepted`() {
        method(config = EthrDidConfig.sepolia(rpc, privateKey = "0x" + "11".repeat(32)))
    }

    @Test
    fun `config toString redacts the private key`() {
        val key = "0x" + "ab".repeat(32)
        val text = EthrDidConfig.sepolia(rpc, privateKey = key).toString()
        assertFalse(key.removePrefix("0x") in text, text)
        assertTrue("<redacted>" in text, text)
    }
}
