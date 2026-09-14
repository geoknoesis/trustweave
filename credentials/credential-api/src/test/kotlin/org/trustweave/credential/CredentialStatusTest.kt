package org.trustweave.credential

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.identifiers.StatusListId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.credential.model.vc.CredentialStatus
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.revocation.CredentialRevocationManager
import org.trustweave.credential.revocation.RevocationManagers
import org.trustweave.credential.revocation.RevocationStatus
import org.trustweave.credential.revocation.StatusListMetadata
import org.trustweave.credential.revocation.StatusListStatistics
import org.trustweave.credential.revocation.StatusUpdate
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * `status()` answers "does this credential still count?", and had no tests.
 *
 * It carries two fixes whose whole point is a distinction a test has to hold in place. SEC-03: a
 * VC 2.0 credential's expiry is `validUntil` and a VC 1.1 credential's is `expirationDate`, and
 * reading the wrong one either expires a live credential or honours a dead one. SEC-05: the same
 * clock-skew tolerance `verify()` applies has to apply here too, or the two disagree about the
 * same credential at the same moment.
 */
class CredentialStatusTest {
    private val now = Clock.System.now()

    private fun service(revocationManager: CredentialRevocationManager? = null) =
        credentialService(
            didResolver = DidResolver { DidResolutionResult.Success(DidDocument(id = it)) },
            revocationManager = revocationManager,
        )

    private fun credential(
        context: List<String> = listOf("https://www.w3.org/ns/credentials/v2"),
        validUntil: Instant? = null,
        expirationDate: Instant? = null,
        validFrom: Instant? = null,
        issuanceDate: Instant = now.minus(10.minutes),
        status: CredentialStatus? = null,
    ) = VerifiableCredential(
        context = context,
        id = CredentialId("urn:uuid:1"),
        type = listOf(CredentialType.fromString("VerifiableCredential")),
        issuer = Issuer.IriIssuer(Iri("did:example:issuer")),
        issuanceDate = issuanceDate,
        validFrom = validFrom,
        validUntil = validUntil,
        expirationDate = expirationDate,
        credentialSubject = CredentialSubject(id = Iri("did:example:subject"), claims = emptyMap()),
        credentialStatus = status,
    )

    // ---------------------------------------------------------------- the plain cases

    @Test
    fun `a current credential is valid`() =
        runBlocking<Unit> {
            val info = service().status(credential())
            assertTrue(info.valid)
            assertFalse(info.expired)
            assertFalse(info.revoked)
        }

    @Test
    fun `an expired credential is not valid, and says which reason`() =
        runBlocking<Unit> {
            val info = service().status(credential(validUntil = now.minus(60.minutes)))
            assertTrue(info.expired)
            assertFalse(info.revoked, "expiry is not revocation; a caller may treat them differently")
            assertFalse(info.valid)
        }

    @Test
    fun `a credential whose validity has not started is not valid`() =
        runBlocking<Unit> {
            val info = service().status(credential(validFrom = now.plus(60.minutes)))
            assertTrue(info.notYetValid)
            assertFalse(info.valid)
            assertFalse(info.expired, "not yet valid is not the same as expired")
        }

    // ---------------------------------------------------------------- SEC-03, version-aware expiry

    @Test
    fun `a VC 2 credential expires on validUntil and ignores expirationDate`() =
        runBlocking<Unit> {
            // expirationDate is a VC 1.1 field. Honouring it on a VC 2.0 credential would expire
            // a credential that is still live by its own version's rules.
            val info =
                service().status(
                    credential(
                        context = listOf("https://www.w3.org/ns/credentials/v2"),
                        validUntil = now.plus(60.minutes),
                        expirationDate = now.minus(60.minutes),
                    ),
                )
            assertFalse(info.expired, "a VC 2.0 credential must be judged on validUntil")
            assertTrue(info.valid)
        }

    @Test
    fun `a VC 1 credential falls back to expirationDate`() =
        runBlocking<Unit> {
            val info =
                service().status(
                    credential(
                        context = listOf("https://www.w3.org/2018/credentials/v1"),
                        expirationDate = now.minus(60.minutes),
                    ),
                )
            assertTrue(info.expired, "a VC 1.1 credential's expiry lives in expirationDate")
        }

    @Test
    fun `a credential with neither expiry field never expires`() =
        runBlocking<Unit> {
            assertFalse(service().status(credential()).expired)
        }

    // ---------------------------------------------------------------- SEC-05, clock skew

    @Test
    fun `a credential just inside the skew tolerance is not yet expired`() =
        runBlocking<Unit> {
            // One minute past expiry with five minutes of tolerance: still good, and it has to
            // agree with verify(), which applies the same tolerance to the same field.
            val info = service().status(credential(validUntil = now.minus(1.minutes)), clockSkewTolerance = 5.minutes)
            assertFalse(info.expired)
        }

    @Test
    fun `a credential past the skew tolerance is expired`() =
        runBlocking<Unit> {
            val info = service().status(credential(validUntil = now.minus(10.minutes)), clockSkewTolerance = 5.minutes)
            assertTrue(info.expired)
        }

    @Test
    fun `skew tolerance applies to the not-yet-valid edge too`() =
        runBlocking<Unit> {
            val inside = service().status(credential(validFrom = now.plus(1.minutes)), clockSkewTolerance = 5.minutes)
            assertFalse(inside.notYetValid, "a clock one minute behind must not reject a live credential")

            val outside = service().status(credential(validFrom = now.plus(10.minutes)), clockSkewTolerance = 5.minutes)
            assertTrue(outside.notYetValid)
        }

    // ---------------------------------------------------------------- revocation

    @Test
    fun `with no revocation manager a credential is never reported revoked`() =
        runBlocking<Unit> {
            val info = service(revocationManager = null).status(credential(status = entry(StatusListId("list"))))
            assertFalse(info.revoked)
            assertTrue(info.valid)
        }

    @Test
    fun `a credential with no credentialStatus is never reported revoked`() =
        runBlocking<Unit> {
            val manager = RevocationManagers.default()
            assertFalse(service(manager).status(credential(status = null)).revoked)
        }

    @Test
    fun `a revoked credential is reported revoked and not valid`() =
        runBlocking<Unit> {
            val manager = RevocationManagers.default()
            val list = manager.createStatusList("did:example:issuer", StatusPurpose.REVOCATION, 64, customId = "list")
            manager.updateStatusListBatch(list, listOf(StatusUpdate(index = 3, revoked = true)))

            val info = service(manager).status(credential(status = entry(list, index = "3")))
            assertTrue(info.revoked)
            assertFalse(info.valid)
        }

    @Test
    fun `a suspended credential is also withdrawn`() =
        runBlocking<Unit> {
            // status() reports one boolean for both, because both mean "do not rely on this now".
            val manager = RevocationManagers.default()
            val list = manager.createStatusList("did:example:issuer", StatusPurpose.SUSPENSION, 64, customId = "susp")
            manager.updateStatusListBatch(list, listOf(StatusUpdate(index = 1, suspended = true)))

            assertTrue(service(manager).status(credential(status = entry(list, index = "1"))).revoked)
        }

    @Test
    fun `an unrevoked entry on a known list is valid`() =
        runBlocking<Unit> {
            val manager = RevocationManagers.default()
            val list = manager.createStatusList("did:example:issuer", StatusPurpose.REVOCATION, 64, customId = "clean")
            assertTrue(service(manager).status(credential(status = entry(list, index = "7"))).valid)
        }

    @Test
    fun `a revocation check that cannot answer fails closed`() =
        runBlocking<Unit> {
            // The important one. A status endpoint that stops answering must not quietly turn
            // every credential it covers into a good one.
            val info = service(ThrowingRevocationManager).status(credential(status = entry(StatusListId("unknown"))))
            assertTrue(info.revoked, "an unanswerable revocation question must not read as 'not revoked'")
            assertFalse(info.valid)
        }

    @Test
    fun `expiry and revocation are reported independently`() =
        runBlocking<Unit> {
            val manager = RevocationManagers.default()
            val list = manager.createStatusList("did:example:issuer", StatusPurpose.REVOCATION, 64, customId = "both")
            manager.updateStatusListBatch(list, listOf(StatusUpdate(index = 0, revoked = true)))

            val info =
                service(manager).status(
                    credential(validUntil = now.minus(60.minutes), status = entry(list, index = "0")),
                )
            assertTrue(info.expired)
            assertTrue(info.revoked)
            assertEquals(false, info.valid)
        }

    // ---------------------------------------------------------------- fixtures

    private fun entry(
        list: StatusListId,
        index: String = "0",
    ) = CredentialStatus(
        id = list,
        type = "BitstringStatusListEntry",
        statusPurpose = StatusPurpose.REVOCATION,
        statusListIndex = index,
        statusListCredential = list,
    )

    /** Stands in for a status service that has stopped answering. */
    private object ThrowingRevocationManager : CredentialRevocationManager {
        private fun down(): Nothing = throw java.io.IOException("status endpoint unreachable")

        override suspend fun checkRevocationStatus(credential: VerifiableCredential): RevocationStatus = down()

        override suspend fun createStatusList(
            issuerDid: String,
            purpose: StatusPurpose,
            size: Int,
            customId: String?,
        ): StatusListId = down()

        override suspend fun revokeCredential(
            credentialId: String,
            statusListId: StatusListId,
        ): Boolean = down()

        override suspend fun suspendCredential(
            credentialId: String,
            statusListId: StatusListId,
        ): Boolean = down()

        override suspend fun unrevokeCredential(
            credentialId: String,
            statusListId: StatusListId,
        ): Boolean = down()

        override suspend fun unsuspendCredential(
            credentialId: String,
            statusListId: StatusListId,
        ): Boolean = down()

        override suspend fun checkStatusByIndex(
            statusListId: StatusListId,
            index: Int,
        ): RevocationStatus = down()

        override suspend fun checkStatusByCredentialId(
            credentialId: String,
            statusListId: StatusListId,
        ): RevocationStatus = down()

        override suspend fun getCredentialIndex(
            credentialId: String,
            statusListId: StatusListId,
        ): Int? = down()

        override suspend fun assignCredentialIndex(
            credentialId: String,
            statusListId: StatusListId,
            index: Int?,
        ): Int = down()

        override suspend fun revokeCredentials(
            credentialIds: List<String>,
            statusListId: StatusListId,
        ): Map<String, Boolean> = down()

        override suspend fun updateStatusListBatch(
            statusListId: StatusListId,
            updates: List<StatusUpdate>,
        ): Unit = down()

        override suspend fun getStatusListStatistics(statusListId: StatusListId): StatusListStatistics? = down()

        override suspend fun getStatusList(statusListId: StatusListId): StatusListMetadata? = down()

        override suspend fun listStatusLists(issuerDid: String?): List<StatusListMetadata> = down()

        override suspend fun deleteStatusList(statusListId: StatusListId): Boolean = down()

        override suspend fun expandStatusList(
            statusListId: StatusListId,
            additionalSize: Int,
        ): Unit = down()
    }
}
