package org.trustweave.registry.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.trustweave.registry.InMemoryTrustRegistry
import org.trustweave.registry.IssuerRegistration
import org.trustweave.registry.VerifierRegistration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrustRegistryServerTest {

    private fun testServer(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            val server = TrustRegistryServer(InMemoryTrustRegistry())
            with(server) { configureApplication() }
        }
        block()
    }

    @Test
    fun `POST registry-issuers registers and returns 201`() = testServer {
        val response = client.post("/registry/issuers") {
            contentType(ContentType.Application.Json)
            setBody("""{"did":"did:key:issuer1","name":"Acme University","credentialTypes":["DegreeCredential"]}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("did:key:issuer1", body["did"]?.jsonPrimitive?.content)
        assertEquals("ACTIVE", body["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun `GET registry-issuers returns list`() = testServer {
        client.post("/registry/issuers") {
            contentType(ContentType.Application.Json)
            setBody("""{"did":"did:key:i1","name":"Issuer One"}""")
        }
        val response = client.get("/registry/issuers")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
        assertTrue(body.any { it.jsonObject["did"]?.jsonPrimitive?.content == "did:key:i1" })
    }

    @Test
    fun `GET registry-issuers-did returns 404 for unknown`() = testServer {
        val response = client.get("/registry/issuers/did:key:nobody")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST registry-issuers-did-revoke revokes issuer`() = testServer {
        client.post("/registry/issuers") {
            contentType(ContentType.Application.Json)
            setBody("""{"did":"did:key:toRevoke","name":"To Revoke"}""")
        }
        val revokeResp = client.post("/registry/issuers/did:key:toRevoke/revoke")
        assertEquals(HttpStatusCode.OK, revokeResp.status)
        val getResp = client.get("/registry/issuers/did:key:toRevoke")
        val body = Json.parseToJsonElement(getResp.bodyAsText()).jsonObject
        assertEquals("REVOKED", body["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun `POST registry-verifiers registers verifier`() = testServer {
        val response = client.post("/registry/verifiers") {
            contentType(ContentType.Application.Json)
            setBody("""{"did":"did:key:verifier1","name":"Big Verifier"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `GET registry-status-did returns PENDING for unknown`() = testServer {
        val response = client.get("/registry/status/did:key:nobody")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("PENDING", body["status"]?.jsonPrimitive?.content)
    }
}
