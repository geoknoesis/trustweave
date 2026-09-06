package org.trustweave.credential.anchor

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
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
}
