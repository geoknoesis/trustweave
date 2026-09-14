package org.trustweave.credential.exchange

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.CredentialService
import org.trustweave.credential.exchange.capability.ExchangeProtocolCapabilities
import org.trustweave.credential.exchange.model.CredentialPreview
import org.trustweave.credential.exchange.model.ExchangeMessageEnvelope
import org.trustweave.credential.exchange.model.ExchangeMessageType
import org.trustweave.credential.exchange.registry.ExchangeProtocolRegistries
import org.trustweave.credential.exchange.request.AttributeRequest
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.request.ProofExchangeRequest
import org.trustweave.credential.exchange.request.ProofRequest
import org.trustweave.credential.exchange.response.ExchangeResponse
import org.trustweave.credential.exchange.result.ExchangeResult
import org.trustweave.credential.identifiers.ExchangeProtocolName
import org.trustweave.credential.identifiers.OfferId
import org.trustweave.credential.identifiers.RequestId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The exchange service is a router: it picks a protocol, checks the protocol will do the thing,
 * and turns whatever the protocol throws into a result a caller can branch on.
 *
 * Which means the interesting paths are all the refusals. A router that returns Success for an
 * unregistered protocol, or that flattens a network timeout and a malformed request into the same
 * failure, pushes the diagnosis onto whoever is reading logs at 3am.
 */
class DefaultExchangeServiceTest {
    private val protocolName = ExchangeProtocolName("test-protocol")
    private val issuerDid = Did("did:example:issuer")
    private val holderDid = Did("did:example:holder")

    private fun envelope(type: ExchangeMessageType) =
        ExchangeMessageEnvelope(
            protocolName = protocolName,
            messageType = type,
            messageData = JsonPrimitive("payload"),
        )

    private val credential =
        VerifiableCredential(
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            issuer = Issuer.IriIssuer(Iri("did:example:issuer")),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject(id = Iri("did:example:holder"), claims = emptyMap()),
        )

    private val presentation =
        VerifiablePresentation(
            type = listOf(CredentialType.fromString("VerifiablePresentation")),
            holder = Iri("did:example:holder"),
            verifiableCredential = listOf(credential),
        )

    /** A protocol that does what it is told, so the router is the only thing under test. */
    private class FakeProtocol(
        override val protocolName: ExchangeProtocolName,
        operations: Set<ExchangeOperation>,
        private val envelope: (ExchangeMessageType) -> ExchangeMessageEnvelope,
        private val credential: VerifiableCredential,
        private val presentation: VerifiablePresentation,
        private val failWith: Throwable? = null,
    ) : CredentialExchangeProtocol {
        override val capabilities = ExchangeProtocolCapabilities(supportedOperations = operations)

        private fun boom(): Nothing = throw failWith!!

        override suspend fun offer(request: ExchangeRequest.Offer): ExchangeMessageEnvelope {
            failWith?.let { boom() }
            return envelope(ExchangeMessageType.Offer)
        }

        override suspend fun request(request: ExchangeRequest.Request): ExchangeMessageEnvelope {
            failWith?.let { boom() }
            return envelope(ExchangeMessageType.Request)
        }

        override suspend fun issue(request: ExchangeRequest.Issue): Pair<VerifiableCredential, ExchangeMessageEnvelope> {
            failWith?.let { boom() }
            return credential to envelope(ExchangeMessageType.Issue)
        }

        override suspend fun requestProof(request: ProofExchangeRequest.Request): ExchangeMessageEnvelope {
            failWith?.let { boom() }
            return envelope(ExchangeMessageType.ProofRequest)
        }

        override suspend fun presentProof(
            request: ProofExchangeRequest.Presentation,
        ): Pair<VerifiablePresentation, ExchangeMessageEnvelope> {
            failWith?.let { boom() }
            return presentation to envelope(ExchangeMessageType.ProofPresentation)
        }
    }

    private fun service(
        operations: Set<ExchangeOperation> = ExchangeOperation.entries.toSet(),
        failWith: Throwable? = null,
        register: Boolean = true,
    ): ExchangeService {
        val registry = ExchangeProtocolRegistries.default()
        if (register) {
            registry.register(FakeProtocol(protocolName, operations, ::envelope, credential, presentation, failWith))
        }
        return ExchangeServices.createExchangeService(
            protocolRegistry = registry,
            // The router never calls through to either of these on the paths under test; they are
            // constructor dependencies of the implementation, not collaborators of the routing.
            credentialService = UnusedCredentialService,
            didResolver = DidResolver { DidResolutionResult.Failure.NotFound(it) },
        )
    }

    private fun offerRequest() =
        ExchangeRequest.Offer(
            protocolName = protocolName,
            issuerDid = issuerDid,
            holderDid = holderDid,
            credentialPreview = CredentialPreview(attributes = emptyList()),
        )

    private fun requestRequest() =
        ExchangeRequest.Request(
            protocolName = protocolName,
            holderDid = holderDid,
            issuerDid = issuerDid,
            offerId = OfferId("offer-1"),
        )

    private fun issueRequest() =
        ExchangeRequest.Issue(
            protocolName = protocolName,
            issuerDid = issuerDid,
            holderDid = holderDid,
            credential = credential,
            requestId = RequestId("request-1"),
        )

    private fun proofRequest() =
        ProofExchangeRequest.Request(
            protocolName = protocolName,
            verifierDid = issuerDid,
            proverDid = holderDid,
            proofRequest =
                ProofRequest(
                    name = "proof",
                    requestedAttributes = mapOf("name" to AttributeRequest(name = "name")),
                ),
        )

    private fun presentationRequest() =
        ProofExchangeRequest.Presentation(
            protocolName = protocolName,
            proverDid = holderDid,
            verifierDid = issuerDid,
            presentation = presentation,
            requestId = RequestId("request-1"),
        )

    // ---------------------------------------------------------------- the happy path

    @Test
    fun `each operation routes to the protocol and returns its envelope`() =
        runBlocking<Unit> {
            val subject = service()
            val offer = assertIs<ExchangeResult.Success<ExchangeResponse.Offer>>(subject.offer(offerRequest()))
            assertEquals(ExchangeMessageType.Offer, offer.value.messageEnvelope.messageType)
            assertEquals(protocolName, offer.value.protocolName)

            assertIs<ExchangeResult.Success<*>>(subject.request(requestRequest()))
            val issued = assertIs<ExchangeResult.Success<ExchangeResponse.Issue>>(subject.issue(issueRequest()))
            assertEquals(credential, issued.value.credential)
            assertIs<ExchangeResult.Success<*>>(subject.requestProof(proofRequest()))
            assertIs<ExchangeResult.Success<*>>(subject.presentProof(presentationRequest()))
        }

    @Test
    fun `every exchange gets its own identifier`() =
        runBlocking<Unit> {
            val subject = service()
            val first = assertIs<ExchangeResult.Success<ExchangeResponse.Offer>>(subject.offer(offerRequest()))
            val second = assertIs<ExchangeResult.Success<ExchangeResponse.Offer>>(subject.offer(offerRequest()))
            assertTrue(first.value.offerId != second.value.offerId, "two offers must not share an id")
        }

    // ---------------------------------------------------------------- unregistered protocol

    @Test
    fun `an unregistered protocol is refused, and the refusal lists what is available`() =
        runBlocking<Unit> {
            val subject = service(register = false)
            val failure = assertIs<ExchangeResult.Failure.ProtocolNotSupported>(subject.offer(offerRequest()))
            assertEquals(protocolName, failure.protocolName)
            assertTrue(protocolName !in failure.availableProtocols)
        }

    @Test
    fun `every operation refuses an unregistered protocol, not just the first`() =
        runBlocking<Unit> {
            val subject = service(register = false)
            assertIs<ExchangeResult.Failure.ProtocolNotSupported>(subject.offer(offerRequest()))
            assertIs<ExchangeResult.Failure.ProtocolNotSupported>(subject.request(requestRequest()))
            assertIs<ExchangeResult.Failure.ProtocolNotSupported>(subject.issue(issueRequest()))
            assertIs<ExchangeResult.Failure.ProtocolNotSupported>(subject.requestProof(proofRequest()))
            assertIs<ExchangeResult.Failure.ProtocolNotSupported>(subject.presentProof(presentationRequest()))
        }

    // ---------------------------------------------------------------- unsupported operation

    @Test
    fun `a protocol that does not support the operation is refused before being called`() =
        runBlocking<Unit> {
            // Registered, but declares only OFFER_CREDENTIAL. Asking it to issue must not reach it.
            val subject = service(operations = setOf(ExchangeOperation.OFFER_CREDENTIAL))
            val failure = assertIs<ExchangeResult.Failure.OperationNotSupported>(subject.issue(issueRequest()))
            assertEquals(ExchangeOperation.ISSUE_CREDENTIAL, failure.operation)
            assertEquals(listOf(ExchangeOperation.OFFER_CREDENTIAL), failure.supportedOperations)

            // The one it does declare still works.
            assertIs<ExchangeResult.Success<*>>(subject.offer(offerRequest()))
        }

    @Test
    fun `each operation checks its own capability`() =
        runBlocking<Unit> {
            val none = service(operations = emptySet())
            assertIs<ExchangeResult.Failure.OperationNotSupported>(none.offer(offerRequest()))
            assertIs<ExchangeResult.Failure.OperationNotSupported>(none.request(requestRequest()))
            assertIs<ExchangeResult.Failure.OperationNotSupported>(none.issue(issueRequest()))
            assertIs<ExchangeResult.Failure.OperationNotSupported>(none.requestProof(proofRequest()))
            assertIs<ExchangeResult.Failure.OperationNotSupported>(none.presentProof(presentationRequest()))
        }

    // ---------------------------------------------------------------- failure mapping

    @Test
    fun `a malformed request and a network timeout do not collapse into the same failure`() =
        runBlocking<Unit> {
            // The whole point of the mapping: a caller retries a NetworkError and fixes an
            // InvalidRequest, and cannot do either if both arrive as Unknown.
            val invalid = service(failWith = IllegalArgumentException("bad field"))
            val network = service(failWith = TimeoutException("timed out"))
            assertIs<ExchangeResult.Failure.InvalidRequest>(invalid.offer(offerRequest()))
            assertIs<ExchangeResult.Failure.NetworkError>(network.offer(offerRequest()))
        }

    @Test
    fun `each recognised exception maps to its own failure shape`() =
        runBlocking<Unit> {
            assertIs<ExchangeResult.Failure.InvalidRequest>(
                service(failWith = IllegalStateException("wrong state")).offer(offerRequest()),
            )
            assertIs<ExchangeResult.Failure.MessageNotFound>(
                service(failWith = NoSuchElementException("gone")).offer(offerRequest()),
            )
            val transports =
                listOf(
                    UnknownHostException("no host"),
                    ConnectException("refused"),
                    SocketTimeoutException("slow"),
                    IOException("broken pipe"),
                )
            for (transport in transports) {
                assertIs<ExchangeResult.Failure.NetworkError>(
                    service(failWith = transport).offer(offerRequest()),
                    "expected $transport to map to NetworkError",
                )
            }
            assertIs<ExchangeResult.Failure.Unknown>(
                service(failWith = RuntimeException("surprise")).offer(offerRequest()),
            )
        }

    @Test
    fun `a network failure keeps its cause so a caller can see what actually happened`() =
        runBlocking<Unit> {
            val cause = ConnectException("connection refused")
            val failure =
                assertIs<ExchangeResult.Failure.NetworkError>(service(failWith = cause).offer(offerRequest()))
            assertEquals(cause, failure.cause)
            assertTrue("refused" in failure.reason, failure.reason)
        }

    @Test
    fun `the failure names the operation that failed`() =
        runBlocking<Unit> {
            val failure =
                assertIs<ExchangeResult.Failure.InvalidRequest>(
                    service(failWith = IllegalArgumentException("bad")).issue(issueRequest()),
                )
            assertTrue(failure.errors.any { "issue" in it.lowercase() }, failure.errors.toString())
        }

    @Test
    fun `failures are mapped on every operation, not only offer`() =
        runBlocking<Unit> {
            val subject = service(failWith = IOException("broken pipe"))
            assertIs<ExchangeResult.Failure.NetworkError>(subject.offer(offerRequest()))
            assertIs<ExchangeResult.Failure.NetworkError>(subject.request(requestRequest()))
            assertIs<ExchangeResult.Failure.NetworkError>(subject.issue(issueRequest()))
            assertIs<ExchangeResult.Failure.NetworkError>(subject.requestProof(proofRequest()))
            assertIs<ExchangeResult.Failure.NetworkError>(subject.presentProof(presentationRequest()))
        }

    // ---------------------------------------------------------------- cancellation

    @Test
    fun `cancellation propagates rather than becoming a failure result`() =
        runBlocking<Unit> {
            // A cancelled caller is not a failed exchange. Turning it into a Failure would make
            // the caller retry work the coroutine scope has already abandoned.
            val subject = service(failWith = CancellationException("cancelled"))
            assertFailsWith<CancellationException> { subject.offer(offerRequest()) }
            assertFailsWith<CancellationException> { subject.request(requestRequest()) }
            assertFailsWith<CancellationException> { subject.issue(issueRequest()) }
            assertFailsWith<CancellationException> { subject.requestProof(proofRequest()) }
            assertFailsWith<CancellationException> { subject.presentProof(presentationRequest()) }
        }

    /**
     * Never invoked by the routing paths; present only to satisfy the constructor.
     *
     * Every member throws rather than returning a plausible value, so if the router ever does
     * start calling through, the test says so instead of quietly passing on a fake answer.
     */
    private object UnusedCredentialService : CredentialService {
        private fun unreachable(): Nothing =
            throw UnsupportedOperationException("the exchange router should not call the credential service")

        override suspend fun issue(request: org.trustweave.credential.requests.IssuanceRequest) = unreachable()

        override suspend fun verify(
            credential: VerifiableCredential,
            trustPolicy: org.trustweave.credential.trust.TrustEvaluator?,
            options: org.trustweave.credential.requests.VerificationOptions,
        ) = unreachable()

        override suspend fun createPresentation(
            credentials: List<VerifiableCredential>,
            request: org.trustweave.credential.requests.PresentationRequest,
        ) = unreachable()

        override suspend fun verifyPresentation(
            presentation: VerifiablePresentation,
            trustPolicy: org.trustweave.credential.trust.TrustEvaluator?,
            options: org.trustweave.credential.requests.VerificationOptions,
        ) = unreachable()

        override suspend fun status(
            credential: VerifiableCredential,
            clockSkewTolerance: kotlin.time.Duration,
        ) = unreachable()

        override fun supports(format: org.trustweave.credential.format.ProofSuiteId): Boolean = unreachable()

        override fun supportedFormats(): List<org.trustweave.credential.format.ProofSuiteId> = unreachable()

        override fun supportsCapability(
            format: org.trustweave.credential.format.ProofSuiteId,
            capability: org.trustweave.credential.spi.proof.ProofEngineCapabilities.() -> Boolean,
        ): Boolean = unreachable()
    }
}
