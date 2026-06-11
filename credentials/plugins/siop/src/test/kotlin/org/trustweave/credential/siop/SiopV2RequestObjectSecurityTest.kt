package org.trustweave.credential.siop

import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import org.trustweave.testkit.kms.InMemoryKeyManagementService
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
 * Security hardening of [SiopV2Service.parseAuthorizationRequest]:
 *
 * - `request_uri` is restricted to https-or-loopback (SSRF guard);
 * - fetched JWT request objects must be signed (`alg: none` rejected);
 * - when the `client_id` is a DID, the request-object JWS MUST verify against an
 *   authentication-authorized key from the CLIENT's independently resolved DID
 *   document (fail-closed without a [DidResolver]).
 */
class SiopV2RequestObjectSecurityTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var kms: InMemoryKeyManagementService

    private val clientDid = Did("did:web:verifier.example")
    private val vmId: VerificationMethodId = clientDid + "key-1"
    private val kid: String get() = vmId.value

    @BeforeTest
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        kms = InMemoryKeyManagementService()
    }

    @AfterTest
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // ========== SSRF: request_uri scheme restriction ==========

    @Test
    fun `http request_uri to a non-loopback host is rejected`() = runBlocking {
        val service = serviceWith(didResolver = null)

        val exception = assertFailsWith<SiopV2Exception> {
            service.parseAuthorizationRequest(
                "siopv2://authorize?client_id=${clientDid.value}" +
                    "&request_uri=http://attacker.example/request",
            )
        }
        assertEquals("INSECURE_REQUEST_URI", exception.code)
    }

    @Test
    fun `non-http scheme request_uri is rejected`() = runBlocking {
        val service = serviceWith(didResolver = null)

        val exception = assertFailsWith<SiopV2Exception> {
            service.parseAuthorizationRequest(
                "siopv2://authorize?client_id=${clientDid.value}" +
                    "&request_uri=file:///etc/passwd",
            )
        }
        assertEquals("INSECURE_REQUEST_URI", exception.code)
    }

    @Test
    fun `http request_uri to loopback is allowed`() = runBlocking {
        // MockWebServer serves plain http on 127.0.0.1 — the loopback carve-out
        // must keep local development and tests working.
        val service = serviceWith(didResolver = null)
        enqueueBody(plainJsonRequestDocument())

        val session = service.parseAuthorizationRequest(authorizationUrl(clientId = "https://verifier.example"))

        assertEquals("https://verifier.example", session.request.clientId)
        assertEquals("json-nonce", session.request.nonce)
    }

    // ========== Unsigned / malformed request objects ==========

    @Test
    fun `unsigned request object with alg none is rejected`() = runBlocking {
        val service = serviceWith(didResolver = null)
        val unsigned = PlainJWT(
            JWTClaimsSet.Builder()
                .claim("response_type", "vp_token")
                .claim("client_id", clientDid.value)
                .claim("nonce", "jwt-nonce")
                .build(),
        ).serialize()
        enqueueBody(unsigned)

        val exception = assertFailsWith<SiopV2Exception> {
            service.parseAuthorizationRequest(authorizationUrl())
        }
        assertEquals("INVALID_REQUEST_OBJECT", exception.code)
        assertTrue(
            exception.message.contains("alg=none"),
            "Rejection must name alg=none, was: ${exception.message}",
        )
    }

    // ========== DID client_id pinning ==========

    @Test
    fun `request object signed by the DID's actual key is accepted`() = runBlocking {
        val keyPair = generateEd25519KeyPair()
        val service = serviceWith(resolverFor(didDocument(ed25519VerificationMethod(keyPair))))

        enqueueBody(signEdDsaRequestObject(keyPair, kid = kid))

        val session = service.parseAuthorizationRequest(authorizationUrl())

        assertEquals(clientDid.value, session.request.clientId)
        assertEquals("jwt-nonce", session.request.nonce)
        assertEquals("jwt-state", session.request.state)
    }

    @Test
    fun `request object signed by a different key than the DID document's is rejected`() = runBlocking {
        val verifierKeyPair = generateEd25519KeyPair()
        val attackerKeyPair = generateEd25519KeyPair()
        val service = serviceWith(resolverFor(didDocument(ed25519VerificationMethod(verifierKeyPair))))

        enqueueBody(signEdDsaRequestObject(attackerKeyPair, kid = kid))

        val exception = assertFailsWith<SiopV2Exception> {
            service.parseAuthorizationRequest(authorizationUrl())
        }
        assertEquals("REQUEST_OBJECT_VERIFICATION_FAILED", exception.code)
        assertTrue(
            exception.message.contains("signature verification failed"),
            "Expected signature failure, got: ${exception.message}",
        )
    }

    @Test
    fun `signed request object with DID client_id but no resolver is rejected fail-closed`() = runBlocking {
        val keyPair = generateEd25519KeyPair()
        // Even a genuinely signed request must be rejected without a resolver,
        // because the verifier identity cannot be pinned.
        val service = serviceWith(didResolver = null)

        enqueueBody(signEdDsaRequestObject(keyPair, kid = kid))

        val exception = assertFailsWith<SiopV2Exception> {
            service.parseAuthorizationRequest(authorizationUrl())
        }
        assertEquals("REQUEST_OBJECT_VERIFICATION_FAILED", exception.code)
        assertTrue(
            exception.message.contains("no DidResolver is configured"),
            "Expected fail-closed no-resolver rejection, got: ${exception.message}",
        )
    }

    @Test
    fun `key not authorized for authentication is rejected`() = runBlocking<Unit> {
        // The key IS in the DID document and the kid matches, but it is only listed
        // under assertionMethod — request-object signing is an authentication act.
        val keyPair = generateEd25519KeyPair()
        val vm = ed25519VerificationMethod(keyPair)
        val document = DidDocument(
            id = clientDid,
            verificationMethod = listOf(vm),
            authentication = emptyList(),
            assertionMethod = listOf(vm.id),
        )
        val service = serviceWith(resolverFor(document))

        enqueueBody(signEdDsaRequestObject(keyPair, kid = kid))

        val exception = assertFailsWith<SiopV2Exception> {
            service.parseAuthorizationRequest(authorizationUrl())
        }
        assertEquals("REQUEST_OBJECT_VERIFICATION_FAILED", exception.code)
        assertTrue(
            exception.message.contains("authentication-authorized"),
            "Rejection must name the missing authentication authorization, was: ${exception.message}",
        )
    }

    @Test
    fun `DID client_id whose DID cannot be resolved is rejected`() = runBlocking {
        val keyPair = generateEd25519KeyPair()
        val service = serviceWith(DidResolver { did -> DidResolutionResult.Failure.NotFound(did) })

        enqueueBody(signEdDsaRequestObject(keyPair, kid = kid))

        val exception = assertFailsWith<SiopV2Exception> {
            service.parseAuthorizationRequest(authorizationUrl())
        }
        assertEquals("REQUEST_OBJECT_VERIFICATION_FAILED", exception.code)
        assertTrue(
            exception.message.contains("DID resolution"),
            "Expected resolution-failure rejection, got: ${exception.message}",
        )
    }

    // ========== Helpers ==========

    private fun serviceWith(didResolver: DidResolver?) = SiopV2Service(
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

    private fun ed25519VerificationMethod(keyPair: KeyPair) = VerificationMethod(
        id = vmId,
        type = "JsonWebKey2020",
        controller = clientDid,
        publicKeyJwk = mapOf(
            "kty" to "OKP",
            "crv" to "Ed25519",
            "x" to Base64.getUrlEncoder().withoutPadding().encodeToString(rawEd25519PublicKey(keyPair)),
        ),
    )

    private fun generateEd25519KeyPair(): KeyPair =
        KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

    /** Raw 32-byte Ed25519 public key = last 32 bytes of the X.509 SubjectPublicKeyInfo. */
    private fun rawEd25519PublicKey(keyPair: KeyPair): ByteArray =
        keyPair.public.encoded.let { it.copyOfRange(it.size - 32, it.size) }

    private fun authorizationUrl(clientId: String = clientDid.value): String {
        val requestUri = "${mockWebServer.url("/request")}"
        return "siopv2://authorize?client_id=$clientId&request_uri=$requestUri"
    }

    private fun enqueueBody(body: String) {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(body))
    }

    private fun plainJsonRequestDocument(): String = buildJsonObject {
        put("response_type", "vp_token")
        put("client_id", "https://verifier.example")
        put("client_id_scheme", "pre-registered")
        put("response_uri", "${mockWebServer.url("/response")}")
        put("nonce", "json-nonce")
    }.toString()

    private fun signEdDsaRequestObject(keyPair: KeyPair, kid: String?): String {
        val header = buildJsonObject {
            put("alg", "EdDSA")
            put("typ", "oauth-authz-req+jwt")
            kid?.let { put("kid", it) }
        }
        val payload = buildJsonObject {
            put("response_type", "vp_token")
            put("client_id", clientDid.value)
            put("client_id_scheme", "did")
            put("nonce", "jwt-nonce")
            put("state", "jwt-state")
            put("response_uri", "${mockWebServer.url("/response")}")
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
