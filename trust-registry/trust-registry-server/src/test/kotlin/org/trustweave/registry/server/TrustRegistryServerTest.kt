package org.trustweave.registry.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.trustweave.registry.InMemoryTrustRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrustRegistryServerTest {
    private val testToken = "test-api-token".padEnd(40, 'x')

    private fun testServer(
        apiToken: String? = testToken,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application {
            val server = TrustRegistryServer(InMemoryTrustRegistry(), apiToken = apiToken)
            with(server) { configureApplication() }
        }
        block()
    }

    private fun HttpRequestBuilder.authorize(token: String = testToken) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    @Test
    fun `POST registry-issuers registers and returns 201`() =
        testServer {
            val response =
                client.post("/registry/issuers") {
                    authorize()
                    contentType(ContentType.Application.Json)
                    setBody("""{"did":"did:key:issuer1","name":"Acme University","credentialTypes":["DegreeCredential"]}""")
                }
            assertEquals(HttpStatusCode.Created, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("did:key:issuer1", body["did"]?.jsonPrimitive?.content)
            assertEquals("ACTIVE", body["status"]?.jsonPrimitive?.content)
        }

    @Test
    fun `GET registry-issuers returns list`() =
        testServer {
            client.post("/registry/issuers") {
                authorize()
                contentType(ContentType.Application.Json)
                setBody("""{"did":"did:key:i1","name":"Issuer One"}""")
            }
            val response = client.get("/registry/issuers")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            assertTrue(body.any { it.jsonObject["did"]?.jsonPrimitive?.content == "did:key:i1" })
        }

    @Test
    fun `GET registry-issuers-did returns 404 for unknown`() =
        testServer {
            val response = client.get("/registry/issuers/did:key:nobody")
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `POST registry-issuers-did-revoke revokes issuer`() =
        testServer {
            client.post("/registry/issuers") {
                authorize()
                contentType(ContentType.Application.Json)
                setBody("""{"did":"did:key:toRevoke","name":"To Revoke"}""")
            }
            val revokeResp = client.post("/registry/issuers/did:key:toRevoke/revoke") { authorize() }
            assertEquals(HttpStatusCode.OK, revokeResp.status)
            val getResp = client.get("/registry/issuers/did:key:toRevoke")
            val body = Json.parseToJsonElement(getResp.bodyAsText()).jsonObject
            assertEquals("REVOKED", body["status"]?.jsonPrimitive?.content)
        }

    @Test
    fun `POST registry-verifiers registers verifier`() =
        testServer {
            val response =
                client.post("/registry/verifiers") {
                    authorize()
                    contentType(ContentType.Application.Json)
                    setBody("""{"did":"did:key:verifier1","name":"Big Verifier"}""")
                }
            assertEquals(HttpStatusCode.Created, response.status)
        }

    @Test
    fun `GET registry-status-did returns UNKNOWN for unregistered DID`() =
        testServer {
            val response = client.get("/registry/status/did:key:nobody")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("UNKNOWN", body["status"]?.jsonPrimitive?.content)
        }

    // --- Authentication on mutating routes ---

    @Test
    fun `mutating route without token returns 401`() =
        testServer {
            val response =
                client.post("/registry/issuers") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"did":"did:key:unauth","name":"No Token"}""")
                }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `mutating route with wrong token returns 401`() =
        testServer {
            val response =
                client.post("/registry/issuers") {
                    authorize("wrong-token")
                    contentType(ContentType.Application.Json)
                    setBody("""{"did":"did:key:unauth","name":"Wrong Token"}""")
                }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `revoke without token returns 401 and does not revoke`() =
        testServer {
            client.post("/registry/issuers") {
                authorize()
                contentType(ContentType.Application.Json)
                setBody("""{"did":"did:key:safe","name":"Safe Issuer"}""")
            }
            val revokeResp = client.post("/registry/issuers/did:key:safe/revoke")
            assertEquals(HttpStatusCode.Unauthorized, revokeResp.status)
            val getResp = client.get("/registry/issuers/did:key:safe")
            val body = Json.parseToJsonElement(getResp.bodyAsText()).jsonObject
            assertEquals("ACTIVE", body["status"]?.jsonPrimitive?.content)
        }

    @Test
    fun `verifier update with wrong token returns 401`() =
        testServer {
            client.post("/registry/verifiers") {
                authorize()
                contentType(ContentType.Application.Json)
                setBody("""{"did":"did:key:v-auth","name":"Verifier"}""")
            }
            val response =
                client.put("/registry/verifiers/did:key:v-auth") {
                    authorize("wrong-token")
                    contentType(ContentType.Application.Json)
                    setBody("""{"name":"Renamed"}""")
                }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `mutating routes return 503 when no token configured`() =
        testServer(apiToken = null) {
            val postResp =
                client.post("/registry/issuers") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"did":"did:key:closed","name":"Fail Closed"}""")
                }
            assertEquals(HttpStatusCode.ServiceUnavailable, postResp.status)

            // Even presenting a token must not open mutations when none is configured
            val withTokenResp =
                client.post("/registry/issuers") {
                    authorize()
                    contentType(ContentType.Application.Json)
                    setBody("""{"did":"did:key:closed","name":"Fail Closed"}""")
                }
            assertEquals(HttpStatusCode.ServiceUnavailable, withTokenResp.status)

            val revokeResp = client.post("/registry/issuers/did:key:x/revoke")
            assertEquals(HttpStatusCode.ServiceUnavailable, revokeResp.status)
        }

    @Test
    fun `read routes stay open when no token configured`() =
        testServer(apiToken = null) {
            val listResp = client.get("/registry/issuers")
            assertEquals(HttpStatusCode.OK, listResp.status)
            val statusResp = client.get("/registry/status/did:key:nobody")
            assertEquals(HttpStatusCode.OK, statusResp.status)
            val body = Json.parseToJsonElement(statusResp.bodyAsText()).jsonObject
            assertEquals("UNKNOWN", body["status"]?.jsonPrimitive?.content)
        }
}

class TrustRegistryPaginationTest {
    private val token = "test-api-token".padEnd(40, 'x')

    private fun server(block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application {
                with(TrustRegistryServer(InMemoryTrustRegistry(), apiToken = token)) { configureApplication() }
            }
            block()
        }

    private suspend fun ApplicationTestBuilder.seed(
        kind: String,
        count: Int,
    ) {
        repeat(count) { i ->
            client.post("/registry/$kind") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"did":"did:key:p${i.toString().padStart(3, '0')}","name":"Name $i"}""")
            }
        }
    }

    @Test
    fun `issuers are paged with limit and offset in did order`() =
        server {
            seed("issuers", 5)
            val page = client.get("/registry/issuers?limit=2&offset=1")
            assertEquals(HttpStatusCode.OK, page.status)
            assertEquals("5", page.headers["X-Total-Count"])
            val dids = Json.parseToJsonElement(page.bodyAsText()).jsonArray.map { it.jsonObject["did"]!!.jsonPrimitive.content }
            assertEquals(listOf("did:key:p001", "did:key:p002"), dids)
        }

    @Test
    fun `verifiers are paged and default limit keeps the array shape`() =
        server {
            seed("verifiers", 3)
            val all = Json.parseToJsonElement(client.get("/registry/verifiers").bodyAsText()).jsonArray
            assertEquals(3, all.size)
            val tail = Json.parseToJsonElement(client.get("/registry/verifiers?limit=10&offset=2").bodyAsText()).jsonArray
            assertEquals(1, tail.size)
        }

    @Test
    fun `invalid limit offset and oversized nameContains are 400`() =
        server {
            for (kind in listOf("issuers", "verifiers")) {
                for (q in listOf("limit=0", "limit=1001", "limit=abc", "offset=-1", "offset=x")) {
                    assertEquals(HttpStatusCode.BadRequest, client.get("/registry/$kind?$q").status, "$kind $q")
                }
                assertEquals(HttpStatusCode.BadRequest, client.get("/registry/$kind?nameContains=${"a".repeat(257)}").status)
                assertEquals(HttpStatusCode.OK, client.get("/registry/$kind?nameContains=${"a".repeat(256)}&limit=1000").status)
            }
        }
}
