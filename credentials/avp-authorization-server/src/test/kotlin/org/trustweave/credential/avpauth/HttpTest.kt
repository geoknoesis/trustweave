package org.trustweave.credential.avpauth

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.trustweave.credential.avpauth.engine.AuthorizationEngine
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HttpTest {
    private val vector = this::class.java.getResource("/vectors/02-payment-authorization.json")!!.readText()
    private val now = Instant.parse("2026-03-25T21:30:30Z")

    @Test fun `valid authorization returns 200 allow`() = testApplication {
        application { configureAuthorization(AuthorizationEngine(clock = { now })) }
        val res = client.post("/v1/authorizations/verify") {
            contentType(ContentType.Application.Json); setBody(vector)
        }
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue(res.bodyAsText().contains("\"decision\":\"allow\""), res.bodyAsText())
    }

    @Test fun `replayed authorization returns 200 reject NONCE_REUSE`() = testApplication {
        application { configureAuthorization(AuthorizationEngine(clock = { now })) }
        client.post("/v1/authorizations/verify") { contentType(ContentType.Application.Json); setBody(vector) }
        val res = client.post("/v1/authorizations/verify") { contentType(ContentType.Application.Json); setBody(vector) }
        assertEquals(HttpStatusCode.OK, res.status)
        val body = res.bodyAsText()
        assertTrue(body.contains("\"decision\":\"reject\"") && body.contains("NONCE_REUSE"), body)
    }

    @Test fun `malformed body returns 400`() = testApplication {
        application { configureAuthorization(AuthorizationEngine(clock = { now })) }
        val res = client.post("/v1/authorizations/verify") {
            contentType(ContentType.Application.Json); setBody("{ not json ")
        }
        assertEquals(HttpStatusCode.BadRequest, res.status)
    }
}
