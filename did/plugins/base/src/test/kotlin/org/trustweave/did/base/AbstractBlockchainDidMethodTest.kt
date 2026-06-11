package org.trustweave.did.base

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import org.trustweave.anchor.AnchorRef
import org.trustweave.anchor.AnchorResult
import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.kms.KeyManagementService
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [AbstractBlockchainDidMethod] resolution metadata, in particular that the
 * stored-document fallback paths of `resolveFromBlockchain` surface the `deactivated`
 * flag recorded by `deactivateDocumentOnBlockchain` (W3C DID Core §7.3) instead of
 * silently reporting the DID as active.
 *
 * Uses a minimal concrete subclass (mirroring the pattern of the web DID tests) backed
 * by the testkit in-memory anchor client.
 */
class AbstractBlockchainDidMethodTest {

    private companion object {
        const val CHAIN_ID = "test:1"
        const val DID = "did:testchain:abc123"
    }

    /**
     * Minimal concrete subclass exposing the protected blockchain helpers under test.
     */
    private class TestBlockchainDidMethod(
        kms: KeyManagementService,
        private val anchorClient: BlockchainAnchorClient,
        private val txHashLookup: (String) -> String? = { null }
    ) : AbstractBlockchainDidMethod("testchain", kms) {

        override fun getBlockchainAnchorClient(): BlockchainAnchorClient = anchorClient

        override fun getChainId(): String = CHAIN_ID

        override suspend fun findDocumentTxHash(did: String): String? = txHashLookup(did)

        override suspend fun createDid(options: DidCreationOptions): DidDocument =
            throw UnsupportedOperationException("Not needed for these tests")

        override suspend fun resolveDid(did: Did): DidResolutionResult =
            resolveFromBlockchain(did.value)

        suspend fun anchor(document: DidDocument): String = anchorDocument(document)

        suspend fun deactivate(did: String, deactivatedDocument: DidDocument): Boolean =
            deactivateDocumentOnBlockchain(did, deactivatedDocument)
    }

    /** Anchor client whose reads always fail, to force the exception-fallback path. */
    private class FailingReadAnchorClient(
        private val delegate: BlockchainAnchorClient
    ) : BlockchainAnchorClient {
        override suspend fun writePayload(payload: JsonElement, mediaType: String): AnchorResult =
            delegate.writePayload(payload, mediaType)

        override suspend fun readPayload(ref: AnchorRef): AnchorResult =
            throw IllegalStateException("chain unavailable")
    }

    private fun document(did: String): DidDocument = DidMethodUtils.buildDidDocument(
        did = did,
        verificationMethod = listOf(
            DidMethodUtils.createVerificationMethod(
                did = did,
                keyHandle = org.trustweave.kms.KeyHandle(
                    id = org.trustweave.core.identifiers.KeyId("key-1"),
                    algorithm = "Ed25519",
                    publicKeyMultibase = "z6Mk"
                ),
                algorithm = "Ed25519"
            )
        )
    )

    private fun deactivatedCopy(document: DidDocument): DidDocument = document.copy(
        verificationMethod = emptyList(),
        authentication = emptyList(),
        assertionMethod = emptyList(),
        keyAgreement = emptyList(),
        capabilityInvocation = emptyList(),
        capabilityDelegation = emptyList()
    )

    @Test
    fun `stored-document fallback reports deactivated=false for an active did`() = runBlocking {
        val method = TestBlockchainDidMethod(
            InMemoryKeyManagementService(),
            InMemoryBlockchainAnchorClient(chainId = CHAIN_ID)
        )
        method.anchor(document(DID))

        // findDocumentTxHash returns null → stored-document fallback path
        val result = method.resolveDid(Did(DID))

        assertTrue(result is DidResolutionResult.Success, "expected successful resolution, got $result")
        assertFalse(result.documentMetadata.deactivated, "active DID must not be reported as deactivated")
    }

    @Test
    fun `stored-document fallback surfaces deactivated=true after deactivation`() = runBlocking {
        val method = TestBlockchainDidMethod(
            InMemoryKeyManagementService(),
            InMemoryBlockchainAnchorClient(chainId = CHAIN_ID)
        )
        val doc = document(DID)
        method.anchor(doc)
        method.deactivate(DID, deactivatedCopy(doc))

        // findDocumentTxHash returns null → stored-document fallback path
        val result = method.resolveDid(Did(DID))

        assertTrue(result is DidResolutionResult.Success, "expected successful resolution, got $result")
        assertTrue(
            result.documentMetadata.deactivated,
            "resolution after deactivation must report deactivated = true"
        )
    }

    @Test
    fun `exception fallback surfaces deactivated=true after deactivation`() = runBlocking {
        // findDocumentTxHash returns a hash, but the chain read blows up →
        // exercises the catch-Exception stored-document fallback path.
        val method = TestBlockchainDidMethod(
            InMemoryKeyManagementService(),
            FailingReadAnchorClient(InMemoryBlockchainAnchorClient(chainId = CHAIN_ID)),
            txHashLookup = { "tx_known" }
        )
        val doc = document(DID)
        method.anchor(doc)
        method.deactivate(DID, deactivatedCopy(doc))

        val result = method.resolveDid(Did(DID))

        assertTrue(result is DidResolutionResult.Success, "expected successful resolution, got $result")
        assertTrue(
            result.documentMetadata.deactivated,
            "resolution after deactivation must report deactivated = true"
        )
    }
}
