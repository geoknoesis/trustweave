package org.trustweave.credential.vcapi

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.trustweave.credential.CredentialServices
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.trust.TrustEvaluator
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What `verified: true` claims, what error bodies reveal, and what `prove` may silently drop. */
class VcApiTrustAndErrorsTest {
    private val kms = InMemoryKeyManagementService()
    private val didMethod = DidKeyMockMethod(kms)
    private val issuerDocument: DidDocument = runBlocking { didMethod.createDid() }
    private val holderDocument: DidDocument = runBlocking { didMethod.createDid() }
    private val service =
        CredentialServices.createCredentialService(
            kms = kms,
            didResolver =
                object : DidResolver {
                    override suspend fun resolve(did: Did): DidResolutionResult = didMethod.resolveDid(did)
                },
            formats = listOf(ProofSuiteId.VC_LD),
        )

    private fun host(
        trust: TrustEvaluator? = null,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application {
            val server = VcApiServer(service).also { s -> trust?.let { s.withTrustEvaluator(it) } }
            // The authentication gate has its own tests; this is about the routes behind it.
            server.withAuthentication(
                org.trustweave.observability.HostAuthentication
                    .frontedByProxy("test"),
            )
            with(server) { configureApplication() }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.post(
        path: String,
        body: String,
    ): HttpResponse =
        client.post(path) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.issue(): JsonObject {
        val r =
            post(
                "/credentials/issue",
                buildJsonObject {
                    putJsonObject("credential") {
                        put("issuer", issuerDocument.id.value)
                        putJsonObject("credentialSubject") { put("id", holderDocument.id.value) }
                    }
                    putJsonObject("options") {
                        put(
                            "verificationMethod",
                            issuerDocument.verificationMethod
                                .first()
                                .id.value,
                        )
                    }
                }.toString(),
            )
        assertEquals(HttpStatusCode.Created, r.status, r.bodyAsText())
        return Json.parseToJsonElement(r.bodyAsText()).jsonObject["verifiableCredential"]!!.jsonObject
    }

    private fun JsonObject.strings(key: String) = this[key]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

    @Test
    fun `verify without a trust evaluator says trust was not evaluated`() =
        host {
            val vc = issue()
            val r = post("/credentials/verify", buildJsonObject { put("verifiableCredential", vc) }.toString())
            assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
            val body = Json.parseToJsonElement(r.bodyAsText()).jsonObject
            assertEquals("true", body["verified"]!!.jsonPrimitive.content)
            assertTrue("trust:not-evaluated" in body.strings("checks"), body.toString())
            assertTrue(body.strings("warnings").any { "trust was not evaluated" in it }, body.toString())
        }

    @Test
    fun `verify with a trust evaluator judges the issuer`() {
        val trusting = TrustEvaluator.allowlist(setOf(Did(issuerDocument.id.value)))
        host(trusting) {
            val vc = issue()
            val ok = post("/credentials/verify", buildJsonObject { put("verifiableCredential", vc) }.toString())
            val body = Json.parseToJsonElement(ok.bodyAsText()).jsonObject
            assertEquals("true", body["verified"]!!.jsonPrimitive.content, body.toString())
            assertTrue("trust:evaluated" in body.strings("checks"))
            assertFalse(body.strings("warnings").any { "not evaluated" in it })
        }
        host(TrustEvaluator.allowlist(emptySet())) {
            val vc = issue()
            val rejected = post("/credentials/verify", buildJsonObject { put("verifiableCredential", vc) }.toString())
            val body = Json.parseToJsonElement(rejected.bodyAsText()).jsonObject
            assertEquals("false", body["verified"]!!.jsonPrimitive.content, "an untrusted issuer must not verify: $body")
        }
    }

    @Test
    fun `an invalid result also says trust was not evaluated`() =
        host {
            val vc = issue().toMutableMap().apply { put("issuer", kotlinx.serialization.json.JsonPrimitive("did:key:z6MkOther")) }
            val r = post("/credentials/verify", buildJsonObject { put("verifiableCredential", JsonObject(vc)) }.toString())
            val body = Json.parseToJsonElement(r.bodyAsText()).jsonObject
            assertEquals("false", body["verified"]!!.jsonPrimitive.content, body.toString())
            assertTrue("trust:not-evaluated" in body.strings("checks"))
        }

    @Test
    fun `a malformed body is answered with a fixed message, not the parser's`() =
        host {
            for (path in listOf("/credentials/issue", "/credentials/verify", "/presentations/prove", "/presentations/verify")) {
                val r =
                    post(path, """{"credential": 12, "verifiableCredential": "x", "presentation": [1], "verifiablePresentation": null""")
                assertEquals(HttpStatusCode.BadRequest, r.status, path)
                val message =
                    Json
                        .parseToJsonElement(r.bodyAsText())
                        .jsonObject["message"]
                        ?.jsonPrimitive
                        ?.content
                assertEquals("The request could not be processed", message, "$path leaked: ${r.bodyAsText()}")
            }
        }

    @Test
    fun `prove refuses a presentation when one of its credentials cannot be parsed`() =
        host {
            val good = issue()
            val r =
                post(
                    "/presentations/prove",
                    buildJsonObject {
                        putJsonObject("presentation") {
                            put(
                                "verifiableCredential",
                                buildJsonArray {
                                    add(good)
                                    add(buildJsonObject { put("not", "a credential") })
                                },
                            )
                        }
                        putJsonObject("options") {
                            put(
                                "verificationMethod",
                                holderDocument.verificationMethod
                                    .first()
                                    .id.value,
                            )
                        }
                    }.toString(),
                )
            assertEquals(HttpStatusCode.BadRequest, r.status, r.bodyAsText())
            assertTrue("verifiableCredential[1]" in r.bodyAsText(), "the caller must be told which one: ${r.bodyAsText()}")
        }

    @Test
    fun `an oversized body is refused with 413 before it is parsed`() {
        testApplication {
            application {
                val server =
                    VcApiServer(service)
                        .withAuthentication(
                            org.trustweave.observability.HostAuthentication
                                .frontedByProxy("test"),
                        ).withMaxRequestBytes(128)
                with(server) { configureApplication() }
            }
            val r = post("/credentials/verify", """{"verifiableCredential":{"pad":"${"x".repeat(500)}"}}""")
            assertEquals(HttpStatusCode.PayloadTooLarge, r.status)
        }
    }
}
