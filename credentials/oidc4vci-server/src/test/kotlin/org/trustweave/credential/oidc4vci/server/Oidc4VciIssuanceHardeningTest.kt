package org.trustweave.credential.oidc4vci.server

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.trustweave.credential.CredentialServices
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import org.trustweave.observability.HostAuthentication
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Issuance must be signed, format-checked, claim-carrying and proof-`typ`-strict. */
class Oidc4VciIssuanceHardeningTest {
    private val issuerUrl = "https://issuer.example.com"
    private val holder: KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

    private fun host(
        service: Oidc4VciIssuerService,
        configure: Oidc4VciServer.() -> Unit = {},
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application {
            val server = Oidc4VciServer(service).withAuthentication(HostAuthentication.frontedByProxy("test"))
            server.configure()
            with(server) { configureApplication() }
        }
        block()
    }

    private fun service(builder: Oidc4VciCredentialBuilder?) =
        Oidc4VciIssuerService(issuerUrl, "did:key:z6MkTestIssuer", TEST_CONFIGURATIONS, credentialBuilder = builder)

    private fun session(
        svc: Oidc4VciIssuerService,
        claims: JsonObject = JsonObject(emptyMap()),
    ): Pair<String, String> {
        val offer = svc.createOffer(listOf("DegreeCredential"), claims = claims)
        val token = svc.exchangePreAuthCode(offer.preAuthCode, null)
        return token.accessToken to token.cNonce
    }

    private suspend fun ApplicationTestBuilder.credential(
        accessToken: String,
        format: String,
        proof: String,
    ): HttpResponse =
        client.post("/credential") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("format", format)
                    put(
                        "proof",
                        buildJsonObject {
                            put("proof_type", "jwt")
                            put("jwt", proof)
                        },
                    )
                }.toString(),
            )
        }

    private fun proof(
        nonce: String,
        typ: String? = "openid4vci-proof+jwt",
    ): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        val raw = holder.public.encoded.let { it.copyOfRange(it.size - 32, it.size) }
        val header =
            buildJsonObject {
                put("alg", "EdDSA")
                typ?.let { put("typ", it) }
                put(
                    "jwk",
                    buildJsonObject {
                        put("kty", "OKP")
                        put("crv", "Ed25519")
                        put("x", enc.encodeToString(raw))
                    },
                )
            }
        val payload =
            buildJsonObject {
                put("aud", issuerUrl)
                put("nonce", nonce)
            }
        val h = enc.encodeToString(header.toString().toByteArray())
        val p = enc.encodeToString(payload.toString().toByteArray())
        val sig =
            Signature
                .getInstance("Ed25519")
                .apply {
                    initSign(holder.private)
                    update("$h.$p".toByteArray())
                }.sign()
        return "$h.$p.${enc.encodeToString(sig)}"
    }

    private fun error(response: String) =
        Json
            .parseToJsonElement(response)
            .jsonObject["error"]
            ?.jsonPrimitive
            ?.content

    @Test
    fun `an issuer with no signing hook refuses every format instead of emitting an unsigned credential`() {
        val svc = service(null)
        host(svc) {
            val (token, nonce) = session(svc)
            val r = credential(token, "jwt_vc_json", proof(nonce))
            assertEquals(HttpStatusCode.BadRequest, r.status)
            assertEquals("unsupported_credential_format", error(r.bodyAsText()))
        }
    }

    @Test
    fun `an unsupported format is refused and not echoed back`() {
        val svc = service(TestCredentialBuilder(setOf("jwt_vc_json")))
        host(svc) {
            val (token, nonce) = session(svc)
            val r = credential(token, "mso_mdoc", proof(nonce))
            assertEquals(HttpStatusCode.BadRequest, r.status)
            assertEquals("unsupported_credential_format", error(r.bodyAsText()))
            // The refusal happened before the proof, so the same nonce still works for a valid request.
            val ok = credential(token, "jwt_vc_json", proof(nonce))
            assertEquals(HttpStatusCode.OK, ok.status, ok.bodyAsText())
        }
    }

    @Test
    fun `the offer's claims are carried into the credential`() {
        val svc = service(TestCredentialBuilder())
        host(svc) {
            val (token, nonce) =
                session(
                    svc,
                    buildJsonObject {
                        put("degree", "BSc")
                        put("id", "did:evil:x")
                    },
                )
            val r = credential(token, "jwt_vc_json", proof(nonce))
            assertEquals(HttpStatusCode.OK, r.status)
            val cred =
                Json
                    .parseToJsonElement(
                        Json
                            .parseToJsonElement(r.bodyAsText())
                            .jsonObject["credential"]!!
                            .jsonPrimitive.content,
                    ).jsonObject
            val subject = cred["credentialSubject"]!!.jsonObject
            assertEquals("BSc", subject["degree"]?.jsonPrimitive?.content)
            assertTrue(subject["id"]!!.jsonPrimitive.content.startsWith("did:key:z"), "id stays bound to the proven key")
        }
    }

    @Test
    fun `claims may be supplied on the offer endpoint`() {
        val svc = service(TestCredentialBuilder())
        host(svc) {
            val r =
                client.post("/api/offer") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"credentialTypes":["DegreeCredential"],"claims":{"degree":"MSc"}}""")
                }
            assertEquals(HttpStatusCode.Created, r.status)
            val code =
                Json
                    .parseToJsonElement(r.bodyAsText())
                    .jsonObject["pre_authorized_code"]!!
                    .jsonPrimitive.content
            val token = svc.exchangePreAuthCode(code, null)
            val issued = runBlocking { svc.issueCredential(token.accessToken, "jwt_vc_json", emptyList(), proof(token.cNonce)) }
            assertTrue("MSc" in issued.credential!!)
        }
    }

    @Test
    fun `a proof without the openid4vci-proof+jwt typ is rejected`() {
        val svc = service(TestCredentialBuilder())
        host(svc) {
            val (token, nonce) = session(svc)
            for (typ in listOf(null, "JWT")) {
                val r = credential(token, "jwt_vc_json", proof(nonce, typ))
                assertEquals(HttpStatusCode.BadRequest, r.status, "typ=$typ")
                assertEquals("invalid_proof", error(r.bodyAsText()))
            }
        }
    }

    @Test
    fun `credentials are signed through the credential service`() {
        val kms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        val issuerDocument = runBlocking { didMethod.createDid() }
        val issuerDid = issuerDocument.id.value
        val credentialService =
            CredentialServices.createCredentialService(
                kms = kms,
                didResolver =
                    object : DidResolver {
                        override suspend fun resolve(did: Did): DidResolutionResult = didMethod.resolveDid(did)
                    },
                formats = listOf(ProofSuiteId.VC_LD),
            )
        val svc =
            Oidc4VciIssuerService(
                issuerUrl,
                issuerDid,
                TEST_CONFIGURATIONS,
                credentialBuilder = CredentialServiceCredentialBuilder(credentialService, issuerDocument.verificationMethod.first().id),
            )
        host(svc) {
            val (ldToken, ldNonce) = session(svc)
            val ld = credential(ldToken, "ldp_vc", proof(ldNonce))
            assertEquals(HttpStatusCode.OK, ld.status, ld.bodyAsText())
            val doc =
                Json
                    .parseToJsonElement(
                        Json
                            .parseToJsonElement(ld.bodyAsText())
                            .jsonObject["credential"]!!
                            .jsonPrimitive.content,
                    ).jsonObject
            assertNotNull(doc["proof"], "an ldp_vc credential carries its proof")

            // jwt_vc_json is not mapped by default, so it is refused rather than faked.
            val (jwtToken, jwtNonce) = session(svc)
            val jwt = credential(jwtToken, "jwt_vc_json", proof(jwtNonce))
            assertEquals("unsupported_credential_format", error(jwt.bodyAsText()))
        }
    }

    @Test
    fun `a deferred credential can only be collected with the access token it was registered for`() {
        val svc = service(TestCredentialBuilder())
        val (owner, _) = session(svc)
        val (other, _) = session(svc)
        svc.registerDeferredCredential("tx-1", "{\"c\":1}", ownerAccessToken = owner)

        assertNull(svc.getDeferredCredential("tx-1", other), "another holder's token must not collect it")
        assertNotNull(svc.getDeferredCredential("tx-1", owner), "the refused attempt must not have destroyed it")
        assertNull(svc.getDeferredCredential("tx-1", owner), "single use")
    }

    @Test
    fun `an unbound deferred credential stays collectable by any live token`() {
        val svc = service(TestCredentialBuilder())
        val (token, _) = session(svc)
        svc.registerDeferredCredential("tx-2", "{}")
        assertNotNull(svc.getDeferredCredential("tx-2", token))
    }

    @Test
    fun `an oversized request body is refused with 413 before it is parsed`() {
        host(service(TestCredentialBuilder()), configure = { withMaxRequestBytes(64) }) {
            val big = """{"credentialTypes":["${"x".repeat(200)}"]}"""
            val offer =
                client.post("/api/offer") {
                    contentType(ContentType.Application.Json)
                    setBody(big)
                }
            assertEquals(HttpStatusCode.PayloadTooLarge, offer.status)
            val token =
                client.post("/token") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("grant_type=x&pre-authorized_code=${"y".repeat(200)}")
                }
            assertEquals(HttpStatusCode.PayloadTooLarge, token.status)
        }
    }

    @Test
    fun `withMaxRequestBytes rejects a non-positive limit`() {
        assertFailsWith<IllegalArgumentException> { Oidc4VciServer(service(null)).withMaxRequestBytes(0) }
    }
}
