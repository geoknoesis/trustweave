package org.trustweave.credential.oidc4vp

import org.trustweave.core.util.encodeBase58
import org.trustweave.credential.oidc4vp.exception.Oidc4VpException
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import org.trustweave.kms.Algorithm
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * DID-scheme key pinning for signed request objects (OID4VP `client_id_scheme=did`).
 *
 * When the verifier's `client_id` is a DID, the request-object JWS MUST verify against a
 * key from the CLIENT's independently resolved DID document. The self-attested
 * `client_metadata.jwks` embedded in the request object itself must be ignored — a forger
 * can always embed their own keys there.
 */
class Oidc4VpRequestObjectDidPinningTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var kms: InMemoryKeyManagementService

    private val clientDid = Did("did:web:verifier.example")
    private val vmId: VerificationMethodId = clientDid + "key-1"
    private val kid: String get() = vmId.value

    @BeforeTest
    fun setUp() = runBlocking {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        kms = InMemoryKeyManagementService()
        kms.generateKey(Algorithm.Ed25519)
        Unit
    }

    @AfterTest
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // ========== Acceptance: signed by the DID's actual key ==========

    @Test
    fun `request object signed by the DID's actual EC key is accepted`() = runBlocking {
        val verifierKey = ECKeyGenerator(Curve.P_256).keyID(kid).generate()
        val document = didDocument(ecVerificationMethod(verifierKey))
        val service = serviceWith(resolverFor(document))

        enqueueRequestObject(signEcRequestObject(signingKey = verifierKey))

        val permissionRequest = service.parseAuthorizationUrl(didAuthorizationUrl())

        assertEquals(clientDid.value, permissionRequest.authorizationRequest.clientId)
        assertEquals("jwt-nonce", permissionRequest.authorizationRequest.nonce)
    }

    @Test
    fun `request object signed by the DID's Ed25519 key via publicKeyJwk is accepted`() = runBlocking {
        val keyPair = generateEd25519KeyPair()
        val document = didDocument(ed25519JwkVerificationMethod(keyPair))
        val service = serviceWith(resolverFor(document))

        enqueueRequestObject(signEdDsaRequestObject(keyPair, kid = kid))

        val permissionRequest = service.parseAuthorizationUrl(didAuthorizationUrl())

        assertEquals(clientDid.value, permissionRequest.authorizationRequest.clientId)
    }

    @Test
    fun `request object signed by the DID's Ed25519 key via publicKeyMultibase is accepted`() = runBlocking {
        val keyPair = generateEd25519KeyPair()
        val document = didDocument(ed25519MultibaseVerificationMethod(keyPair))
        val service = serviceWith(resolverFor(document))

        enqueueRequestObject(signEdDsaRequestObject(keyPair, kid = kid))

        val permissionRequest = service.parseAuthorizationUrl(didAuthorizationUrl())

        assertEquals(clientDid.value, permissionRequest.authorizationRequest.clientId)
    }

    @Test
    fun `request object without kid is verified against authentication-authorized keys`() = runBlocking {
        val keyPair = generateEd25519KeyPair()
        val document = didDocument(ed25519JwkVerificationMethod(keyPair))
        val service = serviceWith(resolverFor(document))

        enqueueRequestObject(signEdDsaRequestObject(keyPair, kid = null))

        val permissionRequest = service.parseAuthorizationUrl(didAuthorizationUrl())

        assertEquals(clientDid.value, permissionRequest.authorizationRequest.clientId)
    }

    // ========== Rejection: forged / unpinnable request objects ==========

    @Test
    fun `kid-matched key not authorized for authentication is rejected`() = runBlocking<Unit> {
        // The key IS in the DID document and the kid matches, but it is only listed
        // under assertionMethod — request-object signing is an authentication act,
        // so the key must be authentication-authorized.
        val verifierKey = ECKeyGenerator(Curve.P_256).keyID(kid).generate()
        val vm = ecVerificationMethod(verifierKey)
        val document = DidDocument(
            id = clientDid,
            verificationMethod = listOf(vm),
            authentication = emptyList(),
            assertionMethod = listOf(vm.id),
        )
        val service = serviceWith(resolverFor(document))

        enqueueRequestObject(signEcRequestObject(signingKey = verifierKey))

        val ex = assertFailsWith<Oidc4VpException.AuthorizationRequestFetchFailed> {
            service.parseAuthorizationUrl(didAuthorizationUrl())
        }
        assertTrue(
            ex.message!!.contains("authentication-authorized"),
            "Rejection must name the missing authentication authorization, was: ${ex.message}"
        )
    }

    @Test
    fun `request object signed by a different key than the DID document's is rejected`() = runBlocking {
        val verifierKey = ECKeyGenerator(Curve.P_256).keyID(kid).generate()
        val attackerKey = ECKeyGenerator(Curve.P_256).keyID(kid).generate()
        val document = didDocument(ecVerificationMethod(verifierKey))
        val service = serviceWith(resolverFor(document))

        enqueueRequestObject(signEcRequestObject(signingKey = attackerKey))

        val exception = assertFailsWith<Oidc4VpException.AuthorizationRequestFetchFailed> {
            service.parseAuthorizationUrl(didAuthorizationUrl())
        }
        assertTrue(
            exception.reason.contains("signature verification failed"),
            "Expected signature failure, got: ${exception.reason}"
        )
    }

    @Test
    fun `attacker-embedded jwks is ignored for DID client_id`() = runBlocking {
        // The attacker embeds their OWN key in client_metadata.jwks and signs with it —
        // internally consistent, but not the key in the client's DID document.
        val verifierKey = ECKeyGenerator(Curve.P_256).keyID(kid).generate()
        val attackerKey = ECKeyGenerator(Curve.P_256).keyID(kid).generate()
        val document = didDocument(ecVerificationMethod(verifierKey))
        val service = serviceWith(resolverFor(document))

        enqueueRequestObject(
            signEcRequestObject(signingKey = attackerKey, embeddedJwksKey = attackerKey)
        )

        val exception = assertFailsWith<Oidc4VpException.AuthorizationRequestFetchFailed> {
            service.parseAuthorizationUrl(didAuthorizationUrl())
        }
        assertTrue(
            exception.reason.contains("signature verification failed"),
            "Embedded jwks must not be consulted for DID client_ids, got: ${exception.reason}"
        )
    }

    @Test
    fun `DID client_id without a configured resolver is rejected`() = runBlocking {
        val verifierKey = ECKeyGenerator(Curve.P_256).keyID(kid).generate()
        // No didResolver on the service — even a genuinely signed request must be rejected
        // because the verifier identity cannot be pinned.
        val service = serviceWith(didResolver = null)

        enqueueRequestObject(signEcRequestObject(signingKey = verifierKey))

        val exception = assertFailsWith<Oidc4VpException.AuthorizationRequestFetchFailed> {
            service.parseAuthorizationUrl(didAuthorizationUrl())
        }
        assertTrue(
            exception.reason.contains("no DidResolver is configured"),
            "Expected fail-closed no-resolver rejection, got: ${exception.reason}"
        )
    }

    @Test
    fun `DID client_id whose DID cannot be resolved is rejected`() = runBlocking {
        val verifierKey = ECKeyGenerator(Curve.P_256).keyID(kid).generate()
        val service = serviceWith(
            DidResolver { did -> DidResolutionResult.Failure.NotFound(did) }
        )

        enqueueRequestObject(signEcRequestObject(signingKey = verifierKey))

        val exception = assertFailsWith<Oidc4VpException.AuthorizationRequestFetchFailed> {
            service.parseAuthorizationUrl(didAuthorizationUrl())
        }
        assertTrue(
            exception.reason.contains("DID resolution"),
            "Expected resolution-failure rejection, got: ${exception.reason}"
        )
    }

    @Test
    fun `kid not present in the DID document is rejected`() = runBlocking {
        val verifierKey = ECKeyGenerator(Curve.P_256).keyID("${clientDid.value}#other-key").generate()
        val document = didDocument(ecVerificationMethod(ECKeyGenerator(Curve.P_256).keyID(kid).generate()))
        val service = serviceWith(resolverFor(document))

        enqueueRequestObject(signEcRequestObject(signingKey = verifierKey))

        val exception = assertFailsWith<Oidc4VpException.AuthorizationRequestFetchFailed> {
            service.parseAuthorizationUrl(didAuthorizationUrl())
        }
        assertTrue(
            exception.reason.contains("verification method matching kid"),
            "Expected kid-mismatch rejection, got: ${exception.reason}"
        )
    }

    // ========== Helpers ==========

    private fun serviceWith(didResolver: DidResolver?) = Oidc4VpService(
        kms = kms,
        httpClient = okhttp3.OkHttpClient(),
        didResolver = didResolver,
    )

    private fun resolverFor(document: DidDocument) = DidResolver { did ->
        if (did.value == document.id.value) {
            DidResolutionResult.Success(document)
        } else {
            DidResolutionResult.Failure.NotFound(did)
        }
    }

    private fun didDocument(verificationMethod: VerificationMethod) = DidDocument(
        id = clientDid,
        verificationMethod = listOf(verificationMethod),
        authentication = listOf(verificationMethod.id),
        assertionMethod = listOf(verificationMethod.id),
    )

    private fun ecVerificationMethod(key: ECKey) = VerificationMethod(
        id = vmId,
        type = "JsonWebKey2020",
        controller = clientDid,
        publicKeyJwk = key.toPublicJWK().toJSONObject(),
    )

    private fun ed25519JwkVerificationMethod(keyPair: KeyPair) = VerificationMethod(
        id = vmId,
        type = "JsonWebKey2020",
        controller = clientDid,
        publicKeyJwk = mapOf(
            "kty" to "OKP",
            "crv" to "Ed25519",
            "x" to Base64.getUrlEncoder().withoutPadding().encodeToString(rawEd25519PublicKey(keyPair)),
        ),
    )

    private fun ed25519MultibaseVerificationMethod(keyPair: KeyPair) = VerificationMethod(
        id = vmId,
        type = "Ed25519VerificationKey2020",
        controller = clientDid,
        publicKeyMultibase = "z" + (
            byteArrayOf(0xED.toByte(), 0x01) + rawEd25519PublicKey(keyPair)
            ).encodeBase58(),
    )

    private fun generateEd25519KeyPair(): KeyPair =
        KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

    /** Raw 32-byte Ed25519 public key = last 32 bytes of the X.509 SubjectPublicKeyInfo. */
    private fun rawEd25519PublicKey(keyPair: KeyPair): ByteArray =
        keyPair.public.encoded.let { it.copyOfRange(it.size - 32, it.size) }

    private fun didAuthorizationUrl(): String {
        val requestUri = "${mockWebServer.url("/request")}"
        return "openid4vp://authorize?client_id=${clientDid.value}&client_id_scheme=did&request_uri=$requestUri"
    }

    private fun enqueueRequestObject(requestObject: String) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(requestObject)
                .setHeader("Content-Type", "application/oauth-authz-req+jwt")
        )
    }

    private fun requestObjectClaims(): JWTClaimsSet.Builder = JWTClaimsSet.Builder()
        .claim("client_id", clientDid.value)
        .claim("client_id_scheme", "did")
        .claim("nonce", "jwt-nonce")
        .claim("state", "jwt-state")
        .claim("response_uri", "${mockWebServer.url("/response")}")
        .claim("response_mode", "direct_post")

    private fun signEcRequestObject(
        signingKey: ECKey,
        embeddedJwksKey: ECKey? = null,
    ): String {
        val claims = requestObjectClaims()
            .apply {
                embeddedJwksKey?.let {
                    claim(
                        "client_metadata",
                        mapOf("jwks" to mapOf("keys" to listOf(it.toPublicJWK().toJSONObject())))
                    )
                }
            }
            .build()
        val jwt = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.ES256).keyID(signingKey.keyID).build(),
            claims
        )
        jwt.sign(ECDSASigner(signingKey))
        return jwt.serialize()
    }

    /**
     * Signs an EdDSA request object via JCA (Nimbus' Ed25519Signer needs the optional
     * Tink dependency, which this module does not declare).
     */
    private fun signEdDsaRequestObject(keyPair: KeyPair, kid: String?): String {
        val header = buildJsonObject {
            put("alg", "EdDSA")
            put("typ", "oauth-authz-req+jwt")
            kid?.let { put("kid", it) }
        }
        val payload = buildJsonObject {
            put("client_id", clientDid.value)
            put("client_id_scheme", "did")
            put("nonce", "jwt-nonce")
            put("state", "jwt-state")
            put("response_uri", "${mockWebServer.url("/response")}")
            put("response_mode", "direct_post")
        }
        return signCompactJwt(header, payload, keyPair)
    }

    private fun signCompactJwt(header: JsonObject, payload: JsonObject, keyPair: KeyPair): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val headerB64 = encoder.encodeToString(header.toString().toByteArray(Charsets.UTF_8))
        val payloadB64 = encoder.encodeToString(payload.toString().toByteArray(Charsets.UTF_8))
        val signature = Signature.getInstance("Ed25519").apply {
            initSign(keyPair.private)
            update("$headerB64.$payloadB64".toByteArray(Charsets.UTF_8))
        }.sign()
        return "$headerB64.$payloadB64.${encoder.encodeToString(signature)}"
    }
}
