package org.trustweave.polygondid

import kotlinx.coroutines.runBlocking
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.identifiers.Did
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
}
