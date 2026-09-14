package org.trustweave.credential.revocation

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.identifiers.StatusListId
import org.trustweave.credential.internal.RevocationChecker
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.credential.model.vc.CredentialStatus
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.requests.RevocationFailurePolicy
import org.trustweave.credential.results.VerificationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The point of making an unknown status list throw: the host's policy gets consulted.
 *
 * Returning "not revoked" did not merely guess — it guessed *around* `RevocationFailurePolicy`.
 * A deployment that had explicitly chosen FAIL_CLOSED still admitted a credential whose status
 * could not be determined, and never learned that it had. These tests are the evidence that the
 * three configured answers now actually differ.
 */
class UnknownStatusListPolicyTest {
    private val unknownList = StatusListId("https://issuer.example/status/3")

    private fun credential(status: CredentialStatus?) =
        VerifiableCredential(
            id = CredentialId("urn:cred:1"),
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            issuer = Issuer.IriIssuer(Iri("did:example:issuer")),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject(id = Iri("did:example:subject"), claims = emptyMap()),
            credentialStatus = status,
        )

    private val entry =
        CredentialStatus(
            id = unknownList,
            type = "BitstringStatusListEntry",
            statusPurpose = StatusPurpose.REVOCATION,
            statusListIndex = "94567",
            statusListCredential = unknownList,
        )

    private fun check(policy: RevocationFailurePolicy) =
        runBlocking {
            RevocationChecker.checkRevocationStatus(
                credential = credential(entry),
                revocationManager = RevocationManagers.default(),
                policy = policy,
            )
        }

    @Test
    fun `FAIL_CLOSED rejects a credential whose status cannot be determined`() {
        val (failure, warnings) = check(RevocationFailurePolicy.FAIL_CLOSED)
        assertNotNull(failure, "a host that chose FAIL_CLOSED must not admit an unverifiable credential")
        assertTrue(failure is VerificationResult.Invalid, "got $failure")
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `FAIL_WITH_WARNING admits the credential and says why`() {
        val (failure, warnings) = check(RevocationFailurePolicy.FAIL_WITH_WARNING)
        assertNull(failure)
        assertTrue(warnings.isNotEmpty(), "the host asked to be told")
        assertTrue(
            warnings.any { "evocation" in it },
            "the warning must name what could not be checked: $warnings",
        )
    }

    @Test
    fun `FAIL_OPEN admits the credential silently, because that is what it asked for`() {
        val (failure, warnings) = check(RevocationFailurePolicy.FAIL_OPEN)
        assertNull(failure)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `the three policies actually differ, which they did not before`() {
        val closed = check(RevocationFailurePolicy.FAIL_CLOSED)
        val warned = check(RevocationFailurePolicy.FAIL_WITH_WARNING)
        val open = check(RevocationFailurePolicy.FAIL_OPEN)

        assertNotNull(closed.first)
        assertNull(warned.first)
        assertNull(open.first)
        assertTrue(warned.second.isNotEmpty() && open.second.isEmpty())
    }

    @Test
    fun `a known list still answers without involving the policy at all`() =
        runBlocking<Unit> {
            val manager = RevocationManagers.default()
            val list = manager.createStatusList("did:example:issuer", StatusPurpose.REVOCATION, 128, customId = "known")
            manager.updateStatusListBatch(list, listOf(StatusUpdate(index = 7, revoked = true)))

            val known =
                CredentialStatus(
                    id = list,
                    type = "BitstringStatusListEntry",
                    statusListIndex = "7",
                    statusListCredential = list,
                )
            val (failure, warnings) =
                RevocationChecker.checkRevocationStatus(
                    credential = credential(known),
                    revocationManager = manager,
                    // Even the most permissive policy cannot rescue a credential that is
                    // genuinely revoked: the policy only governs checks that could not be made.
                    policy = RevocationFailurePolicy.FAIL_OPEN,
                )
            assertTrue(failure is VerificationResult.Invalid.Revoked, "got $failure")
            assertTrue(warnings.isEmpty())

            val notRevoked = known.copy(statusListIndex = "8")
            assertEquals(
                null,
                RevocationChecker
                    .checkRevocationStatus(
                        credential = credential(notRevoked),
                        revocationManager = manager,
                        policy = RevocationFailurePolicy.FAIL_CLOSED,
                    ).first,
            )
        }

    @Test
    fun `a credential with no status is unaffected`() =
        runBlocking<Unit> {
            val (failure, warnings) =
                RevocationChecker.checkRevocationStatus(
                    credential = credential(status = null),
                    revocationManager = RevocationManagers.default(),
                    policy = RevocationFailurePolicy.FAIL_CLOSED,
                )
            assertNull(failure, "nothing claims this credential can be revoked, so there is nothing to check")
            assertTrue(warnings.isEmpty())
        }
}
