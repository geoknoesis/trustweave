package org.trustweave.ensdid

import kotlinx.coroutines.runBlocking
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidErrorType
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EnsDidMethodTest {
    private val config = EnsDidConfig.mainnet("http://localhost:8545")

    private fun method() = EnsDidMethod(InMemoryKeyManagementService(), InMemoryBlockchainAnchorClient(chainId = "eip155:1"), config)

    @Test
    fun `resolution reports the unimplemented ENS lookup as method-not-supported`() =
        runBlocking<Unit> {
            val result = method().resolveDid(Did("did:ens:example.eth"))

            val failure = assertIs<DidResolutionResult.Failure.ResolutionError>(result)
            assertEquals(DidErrorType.METHOD_NOT_SUPPORTED, failure.resolutionMetadata.error?.type)
            assertTrue(failure.reason.contains("not implemented"), failure.reason)
        }

    @Test
    fun `create, update and deactivate are refused`() =
        runBlocking<Unit> {
            val method = method()
            assertFailsWith<TrustWeaveException> { method.createDid(DidCreationOptions()) }
            assertFailsWith<TrustWeaveException> { method.updateDid(Did("did:ens:example.eth")) { it } }
            assertFailsWith<TrustWeaveException> { method.deactivateDid(Did("did:ens:example.eth")) }
        }

    @Test
    fun `config toString redacts the private key`() {
        val key = "0x" + "ef".repeat(32)
        val text = EnsDidConfig.mainnet("http://localhost:8545", privateKey = key).toString()
        assertFalse(key.removePrefix("0x") in text, text)
        assertTrue("<redacted>" in text, text)
    }
}
