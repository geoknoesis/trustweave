package org.trustweave.did.registrar.server

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.trustweave.did.registrar.client.KmsBasedRegistrar
import org.trustweave.did.registrar.storage.InMemoryJobStorage
import org.trustweave.observability.HostAuthentication
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * HTTP-level behaviour of the registrar routes under the host authentication gate.
 */
class DidRegistrarRoutesTest {
    private val token = "r".repeat(48)
    private val createBody = """{"method":"key","options":{}}"""

    private fun ApplicationTestBuilder.registrarApp(
        auth: HostAuthentication?,
        jobs: InMemoryJobStorage = InMemoryJobStorage(),
    ) {
        application {
            auth?.forRegistrar()?.install(this) ?: HostAuthentication.Unconfigured("The DID registrar").install(this)
            install(ContentNegotiation) { json(registrarJson()) }
            val registrar = KmsBasedRegistrar(InMemoryKeyManagementService(), jobs) { _, kms -> DidKeyMockMethod(kms) }
            routing { configureDidRegistrarRoutes(registrar, jobs) }
        }
    }

    @Test
    fun `an unconfigured server refuses to create a DID and explains why`() =
        testApplication {
            registrarApp(null)
            val response =
                client.post("/1.0/dids") {
                    contentType(ContentType.Application.Json)
                    setBody(createBody)
                }
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        }

    @Test
    fun `a missing or wrong bearer token is refused`() =
        testApplication {
            registrarApp(HostAuthentication.bearerToken(token))
            val none =
                client.post("/1.0/dids") {
                    contentType(ContentType.Application.Json)
                    setBody(createBody)
                }
            assertEquals(HttpStatusCode.Unauthorized, none.status)
            val wrong =
                client.post("/1.0/dids") {
                    header("Authorization", "Bearer ${"x".repeat(48)}")
                    contentType(ContentType.Application.Json)
                    setBody(createBody)
                }
            assertEquals(HttpStatusCode.Unauthorized, wrong.status)
        }

    @Test
    fun `the right token creates a DID`() =
        testApplication {
            registrarApp(HostAuthentication.bearerToken(token))
            val response =
                client.post("/1.0/dids") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(createBody)
                }
            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            assertTrue("did:key" in response.bodyAsText(), response.bodyAsText())
        }

    @Test
    fun `an unknown job is a 404 and internals are not leaked`() =
        testApplication {
            registrarApp(HostAuthentication.bearerToken(token))
            val response = client.get("/1.0/jobs/does-not-exist") { header("Authorization", "Bearer $token") }
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertTrue("JOB_NOT_FOUND" in response.bodyAsText(), response.bodyAsText())
        }

    @Test
    fun `job status can be gated with the same credential`() =
        testApplication {
            registrarApp(HostAuthentication.bearerToken(token, protect = HostAuthentication.ALL))
            assertEquals(HttpStatusCode.Unauthorized, client.get("/1.0/jobs/job-1").status)
            val authed = client.get("/1.0/jobs/job-1") { header("Authorization", "Bearer $token") }
            assertEquals(HttpStatusCode.NotFound, authed.status)
        }

    @Test
    fun `job status needs the credential even when only mutations were configured as protected`() =
        testApplication {
            registrarApp(HostAuthentication.bearerToken(token))
            assertEquals(HttpStatusCode.Unauthorized, client.get("/1.0/jobs/job-1").status)
            assertEquals(HttpStatusCode.Unauthorized, client.get("/1.0/jobs/job-1") { header("Authorization", "Bearer nope") }.status)
            val authed = client.get("/1.0/jobs/job-1") { header("Authorization", "Bearer $token") }
            assertEquals(HttpStatusCode.NotFound, authed.status)
        }

    @Test
    fun `a malformed body is a client error not a server crash`() =
        testApplication {
            registrarApp(HostAuthentication.frontedByProxy("test gateway"))
            val response =
                client.post("/1.0/dids") {
                    contentType(ContentType.Application.Json)
                    setBody("{not json")
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `update and deactivate need the credential`() =
        testApplication {
            registrarApp(HostAuthentication.bearerToken(token))
            val put =
                client.put("/1.0/dids/did:key:abc") {
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }
            assertEquals(HttpStatusCode.Unauthorized, put.status)
            assertEquals(HttpStatusCode.Unauthorized, client.delete("/1.0/dids/did:key:abc").status)
            val wrong = client.delete("/1.0/dids/did:key:abc") { header("Authorization", "Bearer ${"x".repeat(48)}") }
            assertEquals(HttpStatusCode.Unauthorized, wrong.status)
        }

    @Test
    fun `an authorised update with a malformed body is a client error`() =
        testApplication {
            registrarApp(HostAuthentication.bearerToken(token))
            val response =
                client.put("/1.0/dids/did:key:abc") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody("{not json")
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `expired jobs are not served by the status route`() =
        testApplication {
            var now = 0L
            val jobs = InMemoryJobStorage(finishedJobTtl = kotlin.time.Duration.parse("PT1M"), nowMillis = { now })
            registrarApp(HostAuthentication.bearerToken(token), jobs)
            val finished =
                org.trustweave.did.registrar.model.DidRegistrationResponse(
                    jobId = "job-1",
                    didState =
                        org.trustweave.did.registrar.model.DidState(
                            state = org.trustweave.did.registrar.model.OperationState.FINISHED,
                            did = "did:key:abc",
                        ),
                )
            jobs.store("job-1", finished)

            assertEquals(HttpStatusCode.OK, client.get("/1.0/jobs/job-1") { header("Authorization", "Bearer $token") }.status)
            now = 61_000L
            assertEquals(HttpStatusCode.NotFound, client.get("/1.0/jobs/job-1") { header("Authorization", "Bearer $token") }.status)
        }
}
