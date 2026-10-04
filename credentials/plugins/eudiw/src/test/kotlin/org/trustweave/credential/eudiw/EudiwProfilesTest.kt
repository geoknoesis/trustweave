package org.trustweave.credential.eudiw

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.eudiw.exchange.EudiwExchangeProtocol
import org.trustweave.credential.eudiw.exchange.spi.EudiwExchangeProtocolProvider
import org.trustweave.credential.exchange.ExchangeOperation
import org.trustweave.credential.exchange.options.ExchangeOptions
import org.trustweave.credential.exchange.request.AttributeRequest
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.request.ProofExchangeRequest
import org.trustweave.credential.exchange.request.ProofRequest
import org.trustweave.credential.identifiers.ExchangeProtocolName
import org.trustweave.credential.identifiers.OfferId
import org.trustweave.credential.identifiers.RequestId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.did.identifiers.Did
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class EudiwProfilesTest {
    // ------------------------------------------------------------------ OID4VCI profile

    @Test
    fun `only the openid-credential-offer scheme is accepted for offers`() {
        assertTrue(EudiwOid4VciProfile.validateCredentialOffer("openid-credential-offer://?credential_offer_uri=x").valid)
        for (bad in listOf("https://issuer.example/offer", "openid-credential-offer:/x", "", "OPENID-CREDENTIAL-OFFER://x")) {
            val r = EudiwOid4VciProfile.validateCredentialOffer(bad)
            assertFalse(r.valid, bad)
            assertEquals(1, r.violations.size)
        }
    }

    @Test
    fun `a rejected offer never echoes more than its scheme`() {
        val secret = "pre-authorized_code=SECRET123"
        for (bad in listOf("https://issuer.example/offer?$secret", "openid-credential-offer:/x?$secret", secret, "?$secret")) {
            val message = EudiwOid4VciProfile.validateCredentialOffer(bad).violations.single()
            assertFalse(message.contains("SECRET123"), message)
        }
        assertTrue(
            EudiwOid4VciProfile
                .validateCredentialOffer("https://issuer.example/offer")
                .violations
                .single()
                .contains("https://..."),
        )
    }

    @Test
    fun `credential formats are limited to the profile set`() {
        for (ok in listOf("vc+sd-jwt", "dc+sd-jwt", "mso_mdoc")) assertTrue(EudiwOid4VciProfile.validateFormat(ok).valid, ok)
        for (bad in listOf("jwt_vc_json", "ldp_vc", "", "VC+SD-JWT")) assertFalse(EudiwOid4VciProfile.validateFormat(bad).valid, bad)
    }

    @Test
    fun `signing algorithms exclude RSA and none`() {
        for (ok in listOf("ES256", "ES384", "ES512", "EdDSA")) assertTrue(EudiwOid4VciProfile.validateAlgorithm(ok).valid, ok)
        for (bad in listOf("RS256", "RS512", "PS256", "HS256", "none", "")) {
            assertFalse(EudiwOid4VciProfile.validateAlgorithm(bad).valid, bad)
        }
    }

    // ------------------------------------------------------------------ OID4VP profile

    @Test
    fun `response mode must be a direct_post variant`() {
        assertTrue(EudiwOid4VpProfile.validateResponseMode("direct_post").valid)
        assertTrue(EudiwOid4VpProfile.validateResponseMode("direct_post.jwt").valid)
        for (bad in listOf(null, "fragment", "query", "form_post", "")) {
            assertFalse(EudiwOid4VpProfile.validateResponseMode(bad).valid, "$bad")
        }
    }

    @Test
    fun `client id scheme must be a recognised scheme and is never defaulted`() {
        for (ok in EudiwOid4VpProfile.SUPPORTED_CLIENT_ID_SCHEMES) assertTrue(EudiwOid4VpProfile.validateClientIdScheme(ok).valid, ok)
        for (bad in listOf(null, "redirect_uri", "pre-registered", "")) {
            assertFalse(EudiwOid4VpProfile.validateClientIdScheme(bad).valid, "$bad")
        }
    }

    // ------------------------------------------------------------------ PID issuance profile

    private fun fullClaims(): MutableMap<String, Any> = EuPidIssuanceProfile.REQUIRED_CLAIMS.associateWith { "value" as Any }.toMutableMap()

    @Test
    fun `a complete claim set is valid`() {
        val r = EuPidIssuanceProfile.validateClaims(fullClaims())
        assertTrue(r.valid)
        assertTrue(r.missingClaims.isEmpty())
    }

    @Test
    fun `each missing mandatory claim is reported`() {
        for (name in EuPidIssuanceProfile.REQUIRED_CLAIMS) {
            val claims = fullClaims().also { it.remove(name) }
            val r = EuPidIssuanceProfile.validateClaims(claims)
            assertFalse(r.valid, name)
            assertEquals(listOf(name), r.missingClaims)
            assertTrue(r.violations.single().contains(name))
        }
    }

    @Test
    fun `a blank mandatory claim counts as missing`() {
        val claims = fullClaims().also { it[EudiwConstants.CLAIM_FAMILY_NAME] = "   " }
        val r = EuPidIssuanceProfile.validateClaims(claims)
        assertFalse(r.valid)
        assertEquals(listOf(EudiwConstants.CLAIM_FAMILY_NAME), r.missingClaims)
    }

    @Test
    fun `an empty claim set reports all mandatory claims`() {
        assertEquals(EuPidIssuanceProfile.REQUIRED_CLAIMS, EuPidIssuanceProfile.validateClaims(emptyMap()).missingClaims.toSet())
    }

    @Test
    fun `the PID type and context arrays start with the W3C base`() {
        assertEquals("VerifiableCredential", EuPidIssuanceProfile.PID_VC_TYPES.first())
        assertTrue(EudiwConstants.PID_VC_TYPE in EuPidIssuanceProfile.PID_VC_TYPES)
        assertEquals("https://www.w3.org/ns/credentials/v2", EuPidIssuanceProfile.PID_VC_CONTEXTS.first())
    }

    // ------------------------------------------------------------------ wallet trust evidence

    @Test
    fun `wallet trust evidence round-trips with its wire names`() {
        val wte =
            WalletTrustEvidence(
                walletInstanceId = "w-1",
                walletProviderId = "did:example:provider",
                walletAttestationKey = """{"kty":"EC"}""",
                securityLevel = WalletSecurityLevel.HIGH,
                certificationAuthority = "did:example:cab",
                certificationScheme = "EUDI-CC",
                walletCapabilities =
                    WalletCapabilities(
                        formats = listOf(EudiwConstants.CREDENTIAL_FORMAT_SD_JWT_VC),
                        cryptographicSuites = listOf("ES256"),
                        keyStorage = listOf("hardware_key_storage"),
                        proofTypes = listOf("jwt"),
                        userAuthentication = listOf("biometric"),
                    ),
                issuedAt = 1_700_000_000,
                expiresAt = 1_700_086_400,
            )
        val text = Json.encodeToString(WalletTrustEvidence.serializer(), wte)
        for (name in listOf("wallet_instance_id", "wallet_provider_id", "wallet_attestation_key", "security_level", "iat", "exp")) {
            assertTrue(text.contains("\"$name\""), name)
        }
        assertEquals(
            "high",
            Json.parseToJsonElement(text).let {
                (it as kotlinx.serialization.json.JsonObject)["security_level"]!!.jsonPrimitive.content
            },
        )
        assertEquals(wte, Json.decodeFromString(WalletTrustEvidence.serializer(), text))
    }

    @Test
    fun `an unknown security level is refused when decoding`() {
        val bad =
            """{"wallet_instance_id":"w","wallet_provider_id":"p","wallet_attestation_key":"{}","security_level":"ultra",""" +
                """"certification_authority":"c","certification_scheme":"s",""" +
                """"wallet_capabilities":{"formats":[],"cryptographic_suites":[],""" +
                """"key_storage":[],"proof_types":[],"user_authentication":[]},"iat":1,"exp":2}"""
        assertFailsWith<kotlinx.serialization.SerializationException> { Json.decodeFromString(WalletTrustEvidence.serializer(), bad) }
    }

    // ------------------------------------------------------------------ exchange protocol

    private val protocol = EudiwExchangeProtocol()
    private val name = ExchangeProtocolName("eudiw")

    private fun proofRequest(metadata: Map<String, String>) =
        ProofExchangeRequest.Request(
            protocolName = name,
            verifierDid = Did("did:example:verifier"),
            proverDid = Did("did:example:prover"),
            proofRequest = ProofRequest("pid", requestedAttributes = mapOf("a" to AttributeRequest("family_name"))),
            options = ExchangeOptions(metadata = metadata.mapValues { JsonPrimitive(it.value) }),
        )

    private fun credential() =
        VerifiableCredential(
            type = listOf(CredentialType.VerifiableCredential),
            issuer = Issuer.IriIssuer(Iri("did:example:issuer")),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject(id = Iri("did:example:holder"), claims = emptyMap()),
        )

    private fun presentation(
        credentials: List<VerifiableCredential> = listOf(credential()),
        proof: CredentialProof? = CredentialProof.SdJwtVcProof(sdJwtVc = "a.b.c~", disclosures = emptyList()),
    ) = VerifiablePresentation(
        type = listOf(CredentialType.fromString("VerifiablePresentation")),
        holder = Iri("did:example:holder"),
        verifiableCredential = credentials,
        proof = proof,
    )

    private fun presentRequest(
        vp: VerifiablePresentation,
        metadata: Map<String, String> = emptyMap(),
    ) = ProofExchangeRequest.Presentation(
        protocolName = name,
        proverDid = Did("did:example:prover"),
        verifierDid = Did("did:example:verifier"),
        presentation = vp,
        requestId = RequestId("r-1"),
        options = ExchangeOptions(metadata = metadata.mapValues { JsonPrimitive(it.value) }),
    )

    private fun violation(block: suspend () -> Unit): TrustWeaveException.InvalidOperation = assertFailsWith { runBlocking { block() } }

    @Test
    fun `protocol advertises only proof operations`() {
        assertEquals("eudiw", protocol.protocolName.value)
        assertEquals(setOf(ExchangeOperation.REQUEST_PROOF, ExchangeOperation.PRESENT_PROOF), protocol.capabilities.supportedOperations)
    }

    @Test
    fun `credential operations are refused with a typed error`() {
        val issuance = ExchangeRequest.Request(name, Did("did:example:h"), Did("did:example:i"), OfferId("o"))
        val e = violation { protocol.request(issuance) }
        assertEquals("OPERATION_NOT_SUPPORTED", e.code)
    }

    @Test
    fun `a conformant proof request yields an envelope with the chosen values`() =
        runBlocking<Unit> {
            val env = protocol.requestProof(proofRequest(mapOf("response_mode" to "direct_post.jwt", "client_id_scheme" to "x509_san_dns")))
            val data = env.messageData as kotlinx.serialization.json.JsonObject
            assertEquals("direct_post.jwt", data["response_mode"]!!.jsonPrimitive.content)
            assertEquals("x509_san_dns", data["client_id_scheme"]!!.jsonPrimitive.content)
            assertEquals("did:example:verifier", data["verifier_did"]!!.jsonPrimitive.content)
        }

    @Test
    fun `a proof request without explicit response mode and scheme is refused rather than defaulted`() {
        val e = violation { protocol.requestProof(proofRequest(emptyMap())) }
        assertEquals("EUDIW_PROFILE_VIOLATION", e.code)
    }

    @Test
    fun `a proof request with a non-conformant response mode is refused`() {
        val e = violation { protocol.requestProof(proofRequest(mapOf("response_mode" to "fragment", "client_id_scheme" to "did"))) }
        assertEquals("EUDIW_PROFILE_VIOLATION", e.code)
    }

    @Test
    fun `a conformant presentation is profile-checked, not claimed verified`() =
        runBlocking<Unit> {
            val (vp, env) = protocol.presentProof(presentRequest(presentation()))
            assertEquals(1, vp.verifiableCredential.size)
            val data = env.messageData as kotlinx.serialization.json.JsonObject
            assertEquals("profile_checked", data["status"]!!.jsonPrimitive.content)
        }

    @Test
    fun `a presentation without credentials or proof is refused`() {
        assertEquals(
            "EUDIW_PROFILE_VIOLATION",
            violation { protocol.presentProof(presentRequest(presentation(credentials = emptyList()))) }.code,
        )
        assertEquals("EUDIW_PROFILE_VIOLATION", violation { protocol.presentProof(presentRequest(presentation(proof = null))) }.code)
    }

    @Test
    fun `a stale or future nonce and bad request parameters are refused on presentation`() {
        val now = Clock.System.now().epochSeconds
        runBlocking { protocol.presentProof(presentRequest(presentation(), mapOf("nonce_issued_at" to (now - 10).toString()))) }
        for (bad in listOf((now - 301).toString(), (now + 600).toString(), "yesterday")) {
            assertEquals(
                "EUDIW_PROFILE_VIOLATION",
                violation { protocol.presentProof(presentRequest(presentation(), mapOf("nonce_issued_at" to bad))) }.code,
                bad,
            )
        }
        assertEquals(
            "EUDIW_PROFILE_VIOLATION",
            violation { protocol.presentProof(presentRequest(presentation(), mapOf("response_mode" to "query"))) }.code,
        )
    }

    @Test
    fun `the provider creates the protocol only for its own name`() {
        val provider = EudiwExchangeProtocolProvider()
        assertNotNull(provider.create("eudiw", emptyMap()))
        assertNull(provider.create("didcomm", emptyMap()))
        assertEquals(listOf("eudiw"), provider.supportedProtocols)
    }
}
