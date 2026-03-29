package org.trustweave.testkit.integration

import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.proof.ProofOptions
import org.trustweave.credential.revocation.RevocationManagers
import org.trustweave.did.model.DidDocument
import org.trustweave.testkit.TrustWeaveTestFixture
import org.trustweave.testkit.credential.InMemoryWallet
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.datetime.Clock
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Reusable test scenario for full credential lifecycle.
 *
 * Tests the complete flow: issuance → storage → presentation → verification → revocation.
 *
 * **Example Usage**:
 * ```kotlin
 * @Test
 * fun testCredentialLifecycle() = runBlocking {
 *     val scenario = CredentialLifecycleScenario(fixture)
 *     scenario.execute()
 * }
 * ```
 */
class CredentialLifecycleScenario(
    private val fixture: TrustWeaveTestFixture
) {

    /**
     * Executes the full credential lifecycle test.
     *
     * Steps:
     * 1. Create issuer and holder DIDs
     * 2. Issue a credential
     * 3. Store credential in wallet
     * 4. Create verifiable presentation
     * 5. Verify credential attributes
     * 6. Revoke credential and confirm status
     */
    suspend fun execute() {
        // ── Step 1: Create issuer and holder DIDs ───────────────────────────────
        val issuerDidDoc = fixture.createIssuerDid()
        val holderDidDoc = fixture.createIssuerDid()

        // ── Step 2: Issue credential ─────────────────────────────────────────────
        val credential = issueCredential(issuerDidDoc, holderDidDoc)
        assertNotNull(credential, "Issued credential must not be null")
        assertEquals(issuerDidDoc.id.value, credential.issuer.id.value, "Issuer DID must match")
        assertNotNull(credential.id, "Credential must have an ID")

        // ── Step 3: Store credential in wallet ───────────────────────────────────
        val wallet = InMemoryWallet(walletId = "test-wallet", holderDid = holderDidDoc.id.value)
        val storedId = wallet.store(credential)
        assertNotNull(storedId, "Stored credential ID must not be null")

        val retrieved = wallet.get(storedId)
        assertNotNull(retrieved, "Stored credential must be retrievable")
        assertEquals(credential.id, retrieved.id, "Retrieved credential must match stored one")

        val allCredentials = wallet.list()
        assertEquals(1, allCredentials.size, "Wallet must contain exactly one credential")

        // ── Step 4: Create presentation ──────────────────────────────────────────
        val presentation = wallet.createPresentation(
            credentialIds = listOf(storedId),
            holderDid = holderDidDoc.id.value,
            options = ProofOptions()
        )
        assertNotNull(presentation, "Presentation must not be null")
        assertEquals(1, presentation.verifiableCredential.size, "Presentation must contain one credential")
        assertEquals(holderDidDoc.id.value, presentation.holder?.value, "Presentation holder must match wallet DID")

        // ── Step 5: Verify credential attributes ─────────────────────────────────
        // Without a full proof engine wired in, we verify structural correctness:
        // the credential should have the expected type, issuer, and subject.
        assertEquals(issuerDidDoc.id.value, credential.issuer.id.value)
        assertFalse(credential.type.isEmpty(), "Credential must have at least one type")
        assertEquals(holderDidDoc.id.value, credential.credentialSubject.id?.value,
            "Credential subject must be the holder")

        // ── Step 6: Revoke credential and confirm status ──────────────────────────
        val revocationManager = RevocationManagers.default()
        val statusListId = revocationManager.createStatusList(
            issuerDid = issuerDidDoc.id.value,
            purpose = StatusPurpose.REVOCATION
        )

        val credentialId = credential.id!!.value
        val revoked = revocationManager.revokeCredential(credentialId, statusListId)
        assertFalse(!revoked, "Revocation must succeed")

        val status = revocationManager.checkStatusByCredentialId(credentialId, statusListId)
        assertFalse(!status.revoked, "Credential must be marked revoked after revocation")
    }

    /**
     * Issues a test credential for the given issuer and holder.
     */
    private suspend fun issueCredential(
        issuerDid: DidDocument,
        holderDid: DidDocument
    ): VerifiableCredential {
        val credentialSubject = fixture.createTestCredentialSubject(
            id = holderDid.id.value,
            additionalClaims = mapOf(
                "name" to "Test User",
                "email" to "test@example.com"
            )
        )

        val subjectId = (credentialSubject["id"] as? JsonPrimitive)?.content
            ?: holderDid.id.value
        val subjectClaims = credentialSubject.filterKeys { it != "id" }

        return VerifiableCredential(
            id = CredentialId("vc:test:${System.currentTimeMillis()}"),
            type = listOf(CredentialType.Custom("VerifiableCredential"), CredentialType.Custom("TestCredential")),
            issuer = Issuer.fromDid(issuerDid.id),
            credentialSubject = CredentialSubject.fromIri(subjectId, claims = subjectClaims),
            issuanceDate = Clock.System.now(),
            expirationDate = null,
            credentialStatus = null,
            credentialSchema = null,
            evidence = null,
            proof = null,
            termsOfUse = null,
            refreshService = null
        )
    }
}

