package org.trustweave.registry.server

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.trustweave.registry.InMemoryTrustRegistry
import org.trustweave.registry.IssuerRecord
import org.trustweave.registry.IssuerUpdate
import org.trustweave.registry.TrustRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrustRegistryRoutesErrorMappingTest {
    private val token = "t".repeat(40)

    private fun server(
        registry: TrustRegistry = InMemoryTrustRegistry(),
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application {
            with(TrustRegistryServer(registry, apiToken = token)) { configureApplication() }
        }
        block()
    }

    private fun HttpRequestBuilder.auth() = header(HttpHeaders.Authorization, "Bearer $token")

    private suspend fun ApplicationTestBuilder.register(did: String) =
        client.post("/registry/issuers") {
            auth()
            contentType(ContentType.Application.Json)
            setBody("""{"did":"$did","name":"N"}""")
        }

    @Test
    fun `update of unknown issuer is 404`() =
        server {
            val r =
                client.put("/registry/issuers/did:key:none") {
                    auth()
                    contentType(ContentType.Application.Json)
                    setBody("""{"name":"x"}""")
                }
            assertEquals(HttpStatusCode.NotFound, r.status)
        }

    @Test
    fun `backend failure during update is 500, not 404`() {
        val broken =
            object : TrustRegistry by InMemoryTrustRegistry() {
                override suspend fun updateIssuer(
                    did: String,
                    update: IssuerUpdate,
                ): IssuerRecord = throw IllegalStateException("database unavailable")
            }
        server(broken) {
            val r =
                client.put("/registry/issuers/did:key:any") {
                    auth()
                    contentType(ContentType.Application.Json)
                    setBody("""{"name":"x"}""")
                }
            assertEquals(HttpStatusCode.InternalServerError, r.status)
        }
    }

    @Test
    fun `duplicate registration is 409 and does not reactivate a revoked issuer`() =
        server {
            assertEquals(HttpStatusCode.Created, register("did:key:i").status)
            client.post("/registry/issuers/did:key:i/revoke?reason=fraud") { auth() }
            assertEquals(HttpStatusCode.Conflict, register("did:key:i").status)

            val body = Json.parseToJsonElement(client.get("/registry/issuers/did:key:i").bodyAsText()).jsonObject
            assertEquals("REVOKED", body["status"]?.jsonPrimitive?.content)
            assertEquals("fraud", body["revocationReason"]?.jsonPrimitive?.content)
        }

    @Test
    fun `an invalid status filter is 400 rather than an unfiltered list`() =
        server {
            register("did:key:i")
            val issuers = client.get("/registry/issuers?status=ACTVE")
            assertEquals(HttpStatusCode.BadRequest, issuers.status)
            assertTrue("invalid_status" in issuers.bodyAsText())
            assertEquals(HttpStatusCode.BadRequest, client.get("/registry/verifiers?status=nope").status)
            assertEquals(HttpStatusCode.OK, client.get("/registry/issuers?status=ACTIVE").status)
        }

    @Test
    fun `revoke of an unknown DID is 404 and of a known DID is 200`() =
        server {
            assertEquals(HttpStatusCode.NotFound, client.post("/registry/issuers/did:key:none/revoke") { auth() }.status)
            assertEquals(HttpStatusCode.NotFound, client.post("/registry/verifiers/did:key:none/revoke") { auth() }.status)
            register("did:key:i")
            val ok = client.post("/registry/issuers/did:key:i/revoke") { auth() }
            assertEquals(HttpStatusCode.OK, ok.status)
            assertTrue("revoked" in ok.bodyAsText())
        }

    @Test
    fun `backend failure during revoke is a logged 500, not 404`() {
        val broken =
            object : TrustRegistry by InMemoryTrustRegistry() {
                override suspend fun revokeIssuer(
                    did: String,
                    reason: String?,
                ): Boolean = throw IllegalStateException("database unavailable")

                override suspend fun revokeVerifier(
                    did: String,
                    reason: String?,
                ): Boolean = throw IllegalStateException("database unavailable")
            }
        server(broken) {
            assertEquals(HttpStatusCode.InternalServerError, client.post("/registry/issuers/did:key:a/revoke") { auth() }.status)
            assertEquals(HttpStatusCode.InternalServerError, client.post("/registry/verifiers/did:key:a/revoke") { auth() }.status)
        }
    }
}
