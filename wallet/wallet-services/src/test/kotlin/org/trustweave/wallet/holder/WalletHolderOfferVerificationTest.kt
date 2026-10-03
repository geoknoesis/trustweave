package org.trustweave.wallet.holder

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.CredentialService
import org.trustweave.credential.exchange.CredentialExchangeProtocol
import org.trustweave.credential.exchange.ExchangeOperation
import org.trustweave.credential.exchange.capability.ExchangeProtocolCapabilities
import org.trustweave.credential.exchange.model.ExchangeMessageEnvelope
import org.trustweave.credential.exchange.model.ExchangeMessageType
import org.trustweave.credential.exchange.registry.ExchangeProtocolRegistries
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.request.ProofExchangeRequest
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.identifiers.ExchangeProtocolName
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.PresentationRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.CredentialStatusInfo
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.spi.proof.ProofEngineCapabilities
import org.trustweave.credential.trust.TrustEvaluator
import org.trustweave.did.identifiers.Did
import org.trustweave.testkit.credential.BasicWallet
import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration

/** A credential received through an OIDC4VCI offer must be verified and bound before it is stored. */
class WalletHolderOfferVerificationTest {
    private val holderDid = Did("did:key:z6MkHolder")
    private val issuerUrl = "https://issuer.example.com"
    private val issuerDid = "did:web:issuer.example.com"

    private val offerUrl =
        "openid-credential-offer://?credential_offer=" +
            URLEncoder.encode(
                """{"credential_issuer":"$issuerUrl","credential_configuration_ids":["UniversityDegree"]}""",
                Charsets.UTF_8,
            )

    private fun credential(
        issuer: String = issuerDid,
        subject: String? = holderDid.value,
    ) = VerifiableCredential(
        id = CredentialId("urn:uuid:issued-1"),
        type = listOf(CredentialType.VerifiableCredential, CredentialType.fromString("UniversityDegree")),
        issuer = Issuer.from(issuer),
        issuanceDate = Clock.System.now(),
        credentialSubject = CredentialSubject(id = subject?.let { Iri(it) }),
    )

    /** OIDC4VCI stand-in that hands back a fixed credential from issue(). */
    private class FixedIssuer(
        private val issued: (ExchangeRequest.Issue) -> VerifiableCredential,
    ) : CredentialExchangeProtocol {
        override val protocolName = ExchangeProtocolName.Oidc4Vci
        override val capabilities =
            ExchangeProtocolCapabilities(
                supportedOperations =
                    setOf(ExchangeOperation.OFFER_CREDENTIAL, ExchangeOperation.REQUEST_CREDENTIAL, ExchangeOperation.ISSUE_CREDENTIAL),
            )

        private fun envelope(
            type: ExchangeMessageType,
            key: String,
        ) = ExchangeMessageEnvelope(protocolName, type, JsonObject(emptyMap()), mapOf(key to JsonPrimitive("$key-1")))

        override suspend fun offer(request: ExchangeRequest.Offer) = envelope(ExchangeMessageType.Offer, "offerId")

        override suspend fun request(request: ExchangeRequest.Request) = envelope(ExchangeMessageType.Request, "requestId")

        override suspend fun issue(request: ExchangeRequest.Issue) = issued(request) to envelope(ExchangeMessageType.Issue, "issueId")

        override suspend fun requestProof(request: ProofExchangeRequest.Request): ExchangeMessageEnvelope = error("not used")

        override suspend fun presentProof(
            request: ProofExchangeRequest.Presentation,
        ): Pair<VerifiablePresentation, ExchangeMessageEnvelope> = error("not used")
    }

    private class Verifier(
        private val valid: Boolean,
    ) : CredentialService {
        override suspend fun verify(
            credential: VerifiableCredential,
            trustPolicy: TrustEvaluator?,
            options: VerificationOptions,
        ): VerificationResult =
            if (valid) {
                VerificationResult.Valid(credential, credential.issuer.id, credential.credentialSubject.id, Clock.System.now(), null)
            } else {
                VerificationResult.Invalid.InvalidProof(credential, "bad signature", errors = listOf("bad signature"))
            }

        override suspend fun issue(request: IssuanceRequest): IssuanceResult = error("not used")

        override suspend fun createPresentation(
            credentials: List<VerifiableCredential>,
            request: PresentationRequest,
        ): VerifiablePresentation = error("not used")

        override suspend fun verifyPresentation(
            presentation: VerifiablePresentation,
            trustPolicy: TrustEvaluator?,
            options: VerificationOptions,
        ): VerificationResult = error("not used")

        override suspend fun status(
            credential: VerifiableCredential,
            clockSkewTolerance: Duration,
        ): CredentialStatusInfo = error("not used")

        override fun supports(format: ProofSuiteId) = true

        override fun supportedFormats() = listOf(ProofSuiteId.VC_LD)

        override fun supportsCapability(
            format: ProofSuiteId,
            capability: ProofEngineCapabilities.() -> Boolean,
        ) = false
    }

    private fun holder(
        wallet: BasicWallet,
        issued: (ExchangeRequest.Issue) -> VerifiableCredential,
        verifier: CredentialService? = Verifier(valid = true),
    ): WalletHolder {
        val registry = ExchangeProtocolRegistries.default().apply { register(FixedIssuer(issued)) }
        return WalletHolder(wallet, holderDid, registry, null, verifier)
    }

    @Test
    fun `a verified credential from the offer's issuer bound to the holder is stored`() =
        runBlocking<Unit> {
            val wallet = BasicWallet()
            val accepted = holder(wallet, { credential() }).acceptCredentialOffer(offerUrl)
            assertEquals(listOf(accepted), wallet.list())
        }

    @Test
    fun `a credential that fails verification is not stored`() =
        runBlocking<Unit> {
            val wallet = BasicWallet()
            assertFailsWith<CredentialRejectedException> {
                holder(wallet, { credential() }, Verifier(valid = false)).acceptCredentialOffer(offerUrl)
            }
            assertTrue(wallet.list().isEmpty())
        }

    @Test
    fun `a credential from a different issuer is not stored`() =
        runBlocking<Unit> {
            val wallet = BasicWallet()
            assertFailsWith<CredentialRejectedException> {
                holder(wallet, { credential(issuer = "did:web:evil.example.com") }).acceptCredentialOffer(offerUrl)
            }
            assertTrue(wallet.list().isEmpty())
        }

    @Test
    fun `a credential bound to someone else, or to no one, is not stored`() =
        runBlocking<Unit> {
            val wallet = BasicWallet()
            assertFailsWith<CredentialRejectedException> {
                holder(wallet, { credential(subject = "did:key:z6MkSomeoneElse") }).acceptCredentialOffer(offerUrl)
            }
            assertFailsWith<CredentialRejectedException> {
                holder(wallet, { credential(subject = null) }).acceptCredentialOffer(offerUrl)
            }
            assertTrue(wallet.list().isEmpty())
        }

    @Test
    fun `a protocol that echoes the request envelope is rejected`() =
        runBlocking<Unit> {
            val wallet = BasicWallet()
            assertFailsWith<CredentialRejectedException> {
                holder(wallet, { it.credential }).acceptCredentialOffer(offerUrl)
            }
            assertTrue(wallet.list().isEmpty())
        }

    @Test
    fun `accepting offers without a verifier fails before contacting the issuer`() =
        runBlocking<Unit> {
            val wallet = BasicWallet()
            var contacted = false
            assertFailsWith<IllegalStateException> {
                holder(wallet, {
                    contacted = true
                    credential()
                }, verifier = null).acceptCredentialOffer(offerUrl)
            }
            assertTrue(!contacted && wallet.list().isEmpty())
        }

    @Test
    fun `issuer URLs map to did web per the did web spec`() {
        assertEquals("did:web:issuer.example.com", issuerDidFor("https://issuer.example.com/"))
        assertEquals("did:web:issuer.example.com%3A8443:tenants:a", issuerDidFor("https://issuer.example.com:8443/tenants/a"))
        assertEquals("did:key:z6Mk", issuerDidFor("did:key:z6Mk"))
        assertFailsWith<IllegalArgumentException> { issuerDidFor("http://issuer.example.com") }
    }
}
