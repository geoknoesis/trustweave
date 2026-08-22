package org.trustweave.credential.vcapi

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.trustweave.core.serialization.SerializationModule
import org.trustweave.credential.CredentialServices
import org.trustweave.credential.format.ProofSuiteId
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

/**
 * Route behaviour for the W3C VC API server.
 *
 * The module shipped with no tests at all, which matters more here than in most places: these four
 * endpoints are the only thing between an HTTP request and a signing key.
 */
class VcApiRoutesTest {
    private val kms = InMemoryKeyManagementService()
    private val didMethod = DidKeyMockMethod(kms)
    private val issuerDocument: DidDocument = runBlocking { didMethod.createDid() }
    private val holderDocument: DidDocument = runBlocking { didMethod.createDid() }

    private val didResolver =
        object : DidResolver {
            override suspend fun resolve(did: Did): DidResolutionResult = didMethod.resolveDid(did)
        }

    private val service =
        CredentialServices.createCredentialService(
            kms = kms,
            didResolver = didResolver,
            formats = listOf(ProofSuiteId.VC_LD),
        )

    /** Mirrors VcApiServer.configureApplication: the routes need ContentNegotiation installed. */
    private fun Application.vcApiTestApp() {
        install(ContentNegotiation) {
            json(
                Json {
                    serializersModule = SerializationModule.default
                    ignoreUnknownKeys = true
                },
            )
        }
        routing { configureVcApiRoutes(service) }
    }

    private fun credentialJson(): JsonObject =
        buildJsonObject {
            putJsonArray("@context") { add("https://www.w3.org/ns/credentials/v2") }
            putJsonArray("type") { add("VerifiableCredential") }
            put("issuer", issuerDocument.id.value)
            putJsonObject("credentialSubject") { put("id", holderDocument.id.value) }
        }

    private val issuerKeyId: String =
        issuerDocument.verificationMethod
            .first()
            .id.value

    private fun issueRequestBody(): String =
        buildJsonObject {
            put("credential", credentialJson())
            putJsonObject("options") { put("verificationMethod", issuerKeyId) }
        }.toString()

    @Test
    fun `verify refuses a credential larger than the configured maximum`() =
        testApplication {
            application { vcApiTestApp() }

            // SecurityConstants.MAX_CREDENTIAL_SIZE_BYTES is 1MB. These routes decode straight
            // through the serializer, so the cap applied at JsonObject.toCredential() never ran
            // here - the HTTP boundary is where untrusted documents actually arrive.
            val oversized =
                buildJsonObject {
                    putJsonArray("@context") { add("https://www.w3.org/ns/credentials/v2") }
                    putJsonArray("type") { add("VerifiableCredential") }
                    put("issuer", issuerDocument.id.value)
                    putJsonObject("credentialSubject") {
                        put("id", holderDocument.id.value)
                        put("padding", "x".repeat(1_100_000))
                    }
                }

            val response =
                client.post("/credentials/verify") {
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject { put("verifiableCredential", oversized) }.toString())
                }

            assertEquals(HttpStatusCode.BadRequest, response.status, response.bodyAsText())
        }

    @Test
    fun `prove refuses a presentation larger than the configured maximum`() =
        testApplication {
            application { vcApiTestApp() }

            val oversized =
                buildJsonObject {
                    putJsonArray("type") { add("VerifiablePresentation") }
                    put("padding", "x".repeat(5_300_000))
                }

            val response =
                client.post("/presentations/prove") {
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject { put("presentation", oversized) }.toString())
                }

            assertEquals(HttpStatusCode.BadRequest, response.status, response.bodyAsText())
            assertTrue(
                "exceeds the maximum" in response.bodyAsText(),
                "Must be refused for its size, not incidentally: ${response.bodyAsText()}",
            )
        }

    @Test
    fun `issue returns a signed credential`() =
        testApplication {
            application { vcApiTestApp() }

            val response =
                client.post("/credentials/issue") {
                    contentType(ContentType.Application.Json)
                    setBody(issueRequestBody())
                }

            assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
            val vc =
                Json
                    .parseToJsonElement(response.bodyAsText())
                    .jsonObject["verifiableCredential"]!!
                    .jsonObject
            assertTrue("proof" in vc, "The issued credential must carry a proof: $vc")
        }

    @Test
    fun `prove refuses a presentation whose credentials cannot all be parsed`() =
        testApplication {
            application { vcApiTestApp() }

            // One well-formed credential and one that is not a credential at all. Dropping the bad
            // one and signing over the remainder would hand back a presentation that proves less
            // than the caller asked for, with nothing in the response saying so.
            val body =
                buildJsonObject {
                    putJsonObject("presentation") {
                        put(
                            "verifiableCredential",
                            buildJsonArray {
                                add(credentialJson())
                                add(buildJsonObject { put("not", "a credential") })
                            },
                        )
                    }
                }.toString()

            val response =
                client.post("/presentations/prove") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }

            assertEquals(
                HttpStatusCode.BadRequest,
                response.status,
                "A presentation with an unparseable credential must be refused, not silently " +
                    "proved over the rest. Got: ${response.bodyAsText()}",
            )
        }

    @Test
    fun `an error response does not echo internal exception text`() =
        testApplication {
            application { vcApiTestApp() }

            // Missing credentialSubject: the mapping helper raises with a message that describes
            // internals. Error responses go to whoever called the endpoint.
            val body =
                buildJsonObject {
                    putJsonObject("credential") { put("issuer", issuerDocument.id.value) }
                }.toString()

            val response =
                client.post("/credentials/issue") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val text = response.bodyAsText()
            assertFalse(
                "kotlinx.serialization" in text || "org.trustweave" in text || "Exception" in text,
                "The error body must not carry internal type or stack detail: $text",
            )
        }

    @Test
    fun `a credential issued by this server can be verified by it`() =
        testApplication {
            application { vcApiTestApp() }

            val issued =
                client.post("/credentials/issue") {
                    contentType(ContentType.Application.Json)
                    setBody(issueRequestBody())
                }
            assertEquals(HttpStatusCode.Created, issued.status, issued.bodyAsText())
            val vc =
                Json
                    .parseToJsonElement(issued.bodyAsText())
                    .jsonObject["verifiableCredential"]!!
                    .jsonObject

            val verify =
                client.post("/credentials/verify") {
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject { put("verifiableCredential", vc) }.toString())
                }

            assertEquals(HttpStatusCode.OK, verify.status, "issue -> verify must round-trip: ${verify.bodyAsText()}")
        }

    @Test
    fun `verify accepts the standard string form of issuer`() =
        testApplication {
            application { vcApiTestApp() }

            // "issuer": "did:..." is the ordinary W3C shape and the one /credentials/issue itself
            // accepts. A VC API server that cannot read it cannot verify most real credentials.
            val body = buildJsonObject { put("verifiableCredential", credentialJson()) }.toString()

            val response =
                client.post("/credentials/verify") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }

            assertEquals(
                HttpStatusCode.OK,
                response.status,
                "A string issuer must be readable: ${response.bodyAsText()}",
            )
        }
}
