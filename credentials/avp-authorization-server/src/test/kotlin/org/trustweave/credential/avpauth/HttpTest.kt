package org.trustweave.credential.avpauth

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.trustweave.credential.avpauth.dto.VerifyResponse
import org.trustweave.credential.avpauth.engine.AuthorizationEngine
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpTest {
    private val vector = requireNotNull(
        this::class.java.getResource("/vectors/02-payment-authorization.json")
    ) { "Test vector /vectors/02-payment-authorization.json missing from test resources" }.readText()
    private val now = Instant.parse("2026-03-25T21:30:30Z")
    private val json = Json { ignoreUnknownKeys = true }

    private fun body(text: String): VerifyResponse =
        json.decodeFromString(VerifyResponse.serializer(), text)

    @Test fun `valid authorization returns 200 allow`() = testApplication {
        application { configureAuthorization(AuthorizationEngine(clock = { now })) }
        val res = client.post("/v1/authorizations/verify") {
            contentType(ContentType.Application.Json); setBody(vector)
        }
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals("allow", body(res.bodyAsText()).decision)
    }

    @Test fun `replayed authorization returns 200 reject NONCE_REUSE`() = testApplication {
        application { configureAuthorization(AuthorizationEngine(clock = { now })) }
        client.post("/v1/authorizations/verify") { contentType(ContentType.Application.Json); setBody(vector) }
        val res = client.post("/v1/authorizations/verify") { contentType(ContentType.Application.Json); setBody(vector) }
        assertEquals(HttpStatusCode.OK, res.status)
        val decoded = body(res.bodyAsText())
        assertEquals("reject", decoded.decision)
        assertEquals("NONCE_REUSE", decoded.reason)
    }

    @Test fun `malformed body returns 400`() = testApplication {
        application { configureAuthorization(AuthorizationEngine(clock = { now })) }
        val res = client.post("/v1/authorizations/verify") {
            contentType(ContentType.Application.Json); setBody("{ not json ")
        }
        assertEquals(HttpStatusCode.BadRequest, res.status)
    }

    @Test fun `structurally incomplete authorization returns 400`() = testApplication {
        application { configureAuthorization(AuthorizationEngine(clock = { now })) }
        val res = client.post("/v1/authorizations/verify") {
            contentType(ContentType.Application.Json); setBody("""{"type":"PaymentAuthorization"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, res.status)
    }
}
