package org.trustweave.credential.anchor

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.trustweave.anchor.AbstractBlockchainAnchorClient
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.did.identifiers.Did
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CredentialAnchorServiceTest {
    @Test
    fun `round trip validates the ledger payload and rejects changed credentials`() =
        runBlocking<Unit> {
            val client = InMemoryBlockchainAnchorClient("test:ledger")
            val service = CredentialAnchorService(client)
            val credential =
                VerifiableCredential(
                    id = CredentialId("urn:uuid:original"),
                    type = listOf(CredentialType.Custom("Employee")),
                    issuer = Issuer.fromDid(Did("did:key:issuer")),
                    credentialSubject = CredentialSubject.fromIri("did:key:holder"),
                    issuanceDate = Clock.System.now(),
                )
            val anchored = service.anchorCredential(credential, "test:ledger")
            assertTrue(service.verifyAnchoredCredential(anchored.credential, "test:ledger"))
            assertFalse(service.verifyAnchoredCredential(anchored.credential.copy(id = CredentialId("urn:uuid:changed")), "test:ledger"))
            client.clear()
            assertFalse(service.verifyAnchoredCredential(anchored.credential, "test:ledger"))
        }

    @Test
    fun `digest mode verifies without recovering original credential from chain`() =
        runBlocking<Unit> {
            val client =
                object : AbstractBlockchainAnchorClient(
                    "test:ledger",
                    mapOf("inMemoryTestMode" to true, "payloadMode" to "digest"),
                ) {
                    override fun canSubmitTransaction() = false

                    override suspend fun submitTransactionToBlockchain(payloadBytes: ByteArray): String = error("unused")

                    override suspend fun readTransactionFromBlockchain(txHash: String): org.trustweave.anchor.AnchorResult =
                        error("offline")

                    override fun generateTestTxHash() = "test-digest"

                    override fun getBlockchainName() = "test"
                }
            val service = CredentialAnchorService(client)
            val credential =
                VerifiableCredential(
                    id = CredentialId("urn:uuid:digest"),
                    type = listOf(CredentialType.Custom("Employee")),
                    issuer = Issuer.fromDid(Did("did:key:issuer")),
                    credentialSubject = CredentialSubject.fromIri("did:key:holder"),
                    issuanceDate = Clock.System.now(),
                )
            val result = service.anchorCredential(credential, "test:ledger")
            assertTrue(
                org.trustweave.anchor.AnchorDigest
                    .isEnvelope(client.readPayload(result.anchorRef).payload),
            )
            assertTrue(service.verifyAnchoredCredential(result.credential, "test:ledger"))
            assertFalse(service.verifyAnchoredCredential(result.credential.copy(id = CredentialId("urn:uuid:tampered")), "test:ledger"))
        }
}
