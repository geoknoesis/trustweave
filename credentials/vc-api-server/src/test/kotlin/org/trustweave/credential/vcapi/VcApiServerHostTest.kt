package org.trustweave.credential.vcapi

import io.ktor.client.request.get
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.trustweave.credential.CredentialServices
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import org.trustweave.observability.HostAuthentication
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The full [VcApiServer] pipeline (authentication gate, content negotiation, routes) through the
 * Ktor test host: authentication, error mapping and malformed request bodies.
 */
class VcApiServerHostTest {
    private val kms = InMemoryKeyManagementService()
    private val didMethod = DidKeyMockMethod(kms)
    private val issuerDid = runBlocking { didMethod.createDid() }.id.value

    private val service =
        CredentialServices.createCredentialService(
            kms = kms,
            didResolver =
                object : DidResolver {
                    override suspend fun resolve(did: Did): DidResolutionResult = didMethod.resolveDid(did)
                },
            formats = listOf(ProofSuiteId.VC_LD),
        )

    private val token = "t".repeat(40)

    private fun host(
        authentication: HostAuthentication?,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application {
            val server = VcApiServer(service).also { s -> authentication?.let { s.withAuthentication(it) } }
            with(server) { configureApplication() }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.postJson(
        path: String,
        body: String,
        bearer: String? = null,
        type: ContentType = ContentType.Application.Json,
    ): HttpResponse =
        client.post(path) {
            contentType(type)
            bearer?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            setBody(body)
        }

    private fun errorCode(response: String): String =
        Json
            .parseToJsonElement(response)
            .jsonObject["error"]
            ?.jsonPrimitive
            ?.content
            .orEmpty()

    @Test
    fun `an unconfigured server refuses every mutation with 503`() =
        host(authentication = null) {
            for (path in listOf("/credentials/issue", "/credentials/verify", "/presentations/prove", "/presentations/verify")) {
                val response = postJson(path, "{}")
                assertEquals(HttpStatusCode.ServiceUnavailable, response.status, path)
                assertTrue("authentication_not_configured" in response.bodyAsText(), path)
            }
        }

    @Test
    fun `a bearer-protected server rejects missing and wrong tokens before reaching the routes`() =
        host(HostAuthentication.bearerToken(token)) {
            val missing = postJson("/credentials/issue", "{}")
            assertEquals(HttpStatusCode.Unauthorized, missing.status)
            assertEquals("Bearer", missing.headers["WWW-Authenticate"])

            val wrong = postJson("/credentials/issue", "{}", bearer = "x".repeat(40))
            assertEquals(HttpStatusCode.Unauthorized, wrong.status)

            val wrongScheme =
                client.post("/credentials/verify") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Basic $token")
                    setBody("{}")
                }
            assertEquals(HttpStatusCode.Unauthorized, wrongScheme.status)
            assertFalse("INVALID_REQUEST" in wrong.bodyAsText(), "a refused caller must not reach request parsing")
        }

    @Test
    fun `a valid token reaches the routes and a bad body maps to 400 INVALID_REQUEST`() =
        host(HostAuthentication.bearerToken(token)) {
            val response = postJson("/credentials/issue", "{}", bearer = token)

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("INVALID_REQUEST", errorCode(response.bodyAsText()))
        }

    @Test
    fun `malformed json bodies are 400 on every endpoint`() =
        host(HostAuthentication.frontedByProxy("test")) {
            for (path in listOf("/credentials/issue", "/credentials/verify", "/presentations/prove", "/presentations/verify")) {
                for (body in listOf("", "{", "not json", "[]", "null", "{\"unexpected\":1}")) {
                    val response = postJson(path, body)
                    assertEquals(HttpStatusCode.BadRequest, response.status, "$path <- '$body': ${response.bodyAsText()}")
                    assertEquals("INVALID_REQUEST", errorCode(response.bodyAsText()), "$path <- '$body'")
                }
            }
        }

    @Test
    fun `a non-json content type is not accepted as a request body`() =
        host(HostAuthentication.frontedByProxy("test")) {
            val response = postJson("/credentials/verify", "verifiableCredential=x", type = ContentType.Application.FormUrlEncoded)

            assertTrue(response.status.value in 400..499, "got ${response.status}")
            assertEquals(false, response.status == HttpStatusCode.OK)
        }

    @Test
    fun `wrongly typed fields are rejected rather than coerced`() =
        host(HostAuthentication.frontedByProxy("test")) {
            val response = postJson("/credentials/verify", """{"verifiableCredential":"a string, not an object"}""")

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `issuing for an issuer the service holds no key for is a mapped failure, not a server error`() =
        host(HostAuthentication.frontedByProxy("test")) {
            val body =
                """
                {"credential":{"issuer":"$issuerDid","credentialSubject":{"id":"did:key:z6Mkholder","name":"x"}},
                 "options":{"verificationMethod":"$issuerDid#does-not-exist"}}
                """.trimIndent()

            val response = postJson("/credentials/issue", body)

            assertTrue(response.status.value in 400..499, "got ${response.status}: ${response.bodyAsText()}")
            assertFalse("Exception" in response.bodyAsText())
        }

    @Test
    fun `an unknown route is 404 and a GET on a POST route is not served`() =
        host(HostAuthentication.frontedByProxy("test")) {
            assertEquals(HttpStatusCode.NotFound, client.get("/credentials/unknown").status)
            assertTrue(client.get("/credentials/issue").status.value in 404..405)
        }
}
