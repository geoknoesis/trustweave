package org.trustweave.trust.dsl

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.anchor.BlockchainAnchorRegistry
import org.trustweave.anchor.services.BlockchainService
import org.trustweave.credential.CredentialService
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.identifiers.StatusListId
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.PresentationRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.CredentialStatusInfo
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.revocation.CredentialRevocationManager
import org.trustweave.credential.revocation.RevocationManagers
import org.trustweave.credential.spi.proof.ProofEngineCapabilities
import org.trustweave.credential.trust.TrustEvaluator
import org.trustweave.did.identifiers.Did
import org.trustweave.trust.dsl.credential.IssuanceBuilder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * A status-list index allocated by `withRevocation()` is handed back on every path that does not
 * deliver the credential (failure result, exception, cancellation, failed auto-anchor) and never when
 * issuance succeeded; an index the caller supplied is never touched.
 */
class IssuanceStatusIndexReleaseTest {
    private val issuerDid = "did:key:z6MkIssuerForReleaseTests"

    private class Recording(
        private val delegate: CredentialRevocationManager = RevocationManagers.default(),
        private val releaseResult: Boolean? = null,
    ) : CredentialRevocationManager by delegate {
        val released = mutableListOf<Pair<StatusListId, Int>>()
        val assigned = mutableListOf<Int>()
        var releaseThrows = false

        override suspend fun assignCredentialIndex(
            credentialId: String,
            statusListId: StatusListId,
            index: Int?,
        ): Int = delegate.assignCredentialIndex(credentialId, statusListId, index).also { assigned += it }

        override suspend fun releaseStatusListIndex(
            statusListId: StatusListId,
            index: Int,
        ): Boolean {
            released += statusListId to index
            if (releaseThrows) error("release blew up")
            return releaseResult ?: delegate.releaseStatusListIndex(statusListId, index)
        }
    }

    private class FakeService(
        private val behaviour: suspend (IssuanceRequest) -> IssuanceResult,
    ) : CredentialService {
        override suspend fun issue(request: IssuanceRequest): IssuanceResult = behaviour(request)

        override suspend fun verify(
            c: VerifiableCredential,
            t: TrustEvaluator?,
            o: VerificationOptions,
        ): VerificationResult = error("unused")

        override suspend fun createPresentation(
            c: List<VerifiableCredential>,
            r: PresentationRequest,
        ): VerifiablePresentation = error("unused")

        override suspend fun verifyPresentation(
            p: VerifiablePresentation,
            t: TrustEvaluator?,
            o: VerificationOptions,
        ): VerificationResult = error("unused")

        override suspend fun status(
            c: VerifiableCredential,
            t: Duration,
        ): CredentialStatusInfo = error("unused")

        override fun supports(format: ProofSuiteId): Boolean = true

        override fun supportedFormats(): List<ProofSuiteId> = listOf(ProofSuiteId.VC_LD)

        override fun supportsCapability(
            format: ProofSuiteId,
            capability: ProofEngineCapabilities.() -> Boolean,
        ): Boolean = false
    }

    private val succeed: suspend (IssuanceRequest) -> IssuanceResult = { req ->
        IssuanceResult.Success(
            VerifiableCredential(
                id = req.id,
                type = req.type,
                issuer = req.issuer,
                credentialSubject = req.credentialSubject,
                credentialStatus = req.credentialStatus,
            ),
        )
    }
    private val reject: suspend (IssuanceRequest) -> IssuanceResult = {
        IssuanceResult.Failure.InvalidRequest(field = "credentialSubject", reason = "rejected by policy")
    }

    private fun builder(
        manager: CredentialRevocationManager,
        service: FakeService,
        keyId: String = "key-1",
        autoAnchor: Boolean = false,
        defaultChain: String? = null,
        anchors: BlockchainService? = null,
        callerStatus: Boolean = false,
        revocation: Boolean = true,
    ): IssuanceBuilder =
        IssuanceBuilder(
            credentialService = service,
            revocationManager = manager,
            ioDispatcher = Dispatchers.Unconfined,
            autoAnchor = autoAnchor,
            defaultChain = defaultChain,
            blockchainService = anchors,
        ).apply {
            credential {
                type("TestCredential")
                issuer(issuerDid)
                subject { id("did:key:z6MkSubject") }
                if (callerStatus) {
                    status {
                        id("https://example.com/status/1#7")
                        statusListIndex("7")
                        statusListCredential("https://example.com/status/1")
                    }
                }
            }
            signedBy(Did(issuerDid), keyId)
            if (revocation) withRevocation()
        }

    @Test
    fun `a failure result releases the allocated index exactly once and keeps the failure`() =
        runBlocking<Unit> {
            val manager = Recording()
            val result = builder(manager, FakeService(reject)).build()

            val failure = assertIs<IssuanceResult.Failure.InvalidRequest>(result)
            assertEquals("rejected by policy", failure.reason)
            assertEquals(1, manager.assigned.size)
            assertEquals(listOf(manager.assigned.single()), manager.released.map { it.second })
            assertTrue(failure.warnings.none { it.contains("status-list index") }, "warnings: ${failure.warnings}")
        }

    @Test
    fun `a manager that cannot release adds a warning naming the index`() =
        runBlocking<Unit> {
            val manager = Recording(releaseResult = false)
            val failure = assertIs<IssuanceResult.Failure.InvalidRequest>(builder(manager, FakeService(reject)).build())

            assertEquals(1, manager.released.size, "release is attempted once, never retried")
            assertTrue(failure.warnings.single().contains("could not be released"))
        }

    @Test
    fun `a manager whose release throws still yields the original failure with a warning`() =
        runBlocking<Unit> {
            val manager = Recording().apply { releaseThrows = true }
            val failure = assertIs<IssuanceResult.Failure.InvalidRequest>(builder(manager, FakeService(reject)).build())

            assertEquals("rejected by policy", failure.reason)
            assertEquals(1, manager.released.size)
            assertTrue(failure.warnings.single().contains("could not be released"))
        }

    @Test
    fun `a thrown exception releases the index and is rethrown unchanged`() =
        runBlocking<Unit> {
            val manager = Recording()
            val boom = IllegalStateException("signer exploded")
            val thrown = assertFailsWith<IllegalStateException> { builder(manager, FakeService { throw boom }).build() }

            assertEquals(boom, thrown)
            assertEquals(1, manager.released.size)
        }

    @Test
    fun `cancellation during issue releases the index and propagates cancellation`() =
        runBlocking<Unit> {
            val manager = Recording()
            val entered = CompletableDeferred<Unit>()
            val job =
                async {
                    builder(
                        manager,
                        FakeService {
                            entered.complete(Unit)
                            CompletableDeferred<Unit>().await()
                            error("unreachable")
                        },
                    ).build()
                }
            entered.await()
            job.cancelAndJoin()

            assertTrue(job.isCancelled)
            assertEquals(1, manager.released.size, "the release must run even though the caller was cancelled")
            assertFailsWith<CancellationException> { job.await() }
        }

    @Test
    fun `a successful issuance never releases`() =
        runBlocking<Unit> {
            val manager = Recording()
            val result = builder(manager, FakeService(succeed)).build()

            val credential = assertIs<IssuanceResult.Success>(result).credential
            assertEquals(1, manager.assigned.size)
            assertTrue(manager.released.isEmpty())
            assertEquals(manager.assigned.single().toString(), credential.credentialStatus?.statusListIndex)
        }

    @Test
    fun `a failed auto-anchor after a successful issue releases the index`() =
        runBlocking<Unit> {
            val manager = Recording()
            val emptyAnchors = BlockchainService(BlockchainAnchorRegistry())
            val result =
                builder(manager, FakeService(succeed), autoAnchor = true, defaultChain = "algorand:testnet", anchors = emptyAnchors).build()

            assertIs<IssuanceResult.Failure.AdapterError>(result)
            assertEquals(1, manager.released.size)
        }

    @Test
    fun `auto-anchor without a configured chain also releases`() =
        runBlocking<Unit> {
            val manager = Recording()
            val result = builder(manager, FakeService(succeed), autoAnchor = true, defaultChain = null).build()

            assertIs<IssuanceResult.Failure.InvalidRequest>(result)
            assertEquals(1, manager.released.size)
        }

    @Test
    fun `a caller supplied credentialStatus is never released`() =
        runBlocking<Unit> {
            val manager = Recording()
            val failure =
                assertIs<IssuanceResult.Failure.InvalidRequest>(builder(manager, FakeService(reject), callerStatus = true).build())

            assertTrue(manager.assigned.isEmpty(), "nothing is allocated for a caller supplied status")
            assertTrue(manager.released.isEmpty(), "an index this build did not allocate must not be released")
            assertTrue(failure.warnings.isEmpty())
        }

    @Test
    fun `an unparsable verification method is rejected before any index is allocated`() =
        runBlocking<Unit> {
            val manager = Recording()
            val result = builder(manager, FakeService(succeed), keyId = "bad key").build()

            val failure = assertIs<IssuanceResult.Failure.InvalidRequest>(result)
            assertEquals("issuerKeyId", failure.field)
            assertTrue(manager.assigned.isEmpty(), "no index may be allocated for a request rejected up front")
            assertTrue(manager.released.isEmpty())
        }

    @Test
    fun `without withRevocation nothing is allocated or released`() =
        runBlocking<Unit> {
            val manager = Recording()
            builder(manager, FakeService(reject), revocation = false).build()

            assertTrue(manager.assigned.isEmpty())
            assertTrue(manager.released.isEmpty())
        }

    @Test
    fun `a second build call allocates and releases nothing`() =
        runBlocking<Unit> {
            val manager = Recording()
            val b = builder(manager, FakeService(reject))
            b.build()
            val second = b.build()

            assertIs<IssuanceResult.Failure.InvalidRequest>(second)
            assertEquals(1, manager.assigned.size)
            assertEquals(1, manager.released.size, "double release is impossible: the guard stops the second build")
        }
}
