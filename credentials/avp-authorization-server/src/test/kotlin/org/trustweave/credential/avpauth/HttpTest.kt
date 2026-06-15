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
    private fun res(name: String) = requireNotNull(this::class.java.getResource("/vectors/$name")) {
        "Test vector /vectors/$name missing from test resources"
    }.readText()

    private val authz = res("02-payment-authorization.json")
    private val quote = res("01-payment-quote.json")
    private val now = Instant.parse("2026-03-25T21:30:30Z")
    private val json = Json { ignoreUnknownKeys = true }

    private fun wrapper(a: String = authz, q: String = quote) = """{"authorization":$a,"quote":$q}"""
    private fun body(text: String): VerifyResponse = json.decodeFromString(VerifyResponse.serializer(), text)

    @Test fun `valid authorization with quote returns 200 allow`() = testApplication {
        application { configureAuthorization(AuthorizationEngine(clock = { now })) }
        val r = client.post("/v1/authorizations/verify") { contentType(ContentType.Application.Json); setBody(wrapper()) }
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals("allow", body(r.bodyAsText()).decision)
    }

    @Test fun `replayed authorization returns 200 reject NONCE_REUSE`() = testApplication {
        application { configureAuthorization(AuthorizationEngine(clock = { now })) }
        client.post("/v1/authorizations/verify") { contentType(ContentType.Application.Json); setBody(wrapper()) }
        val r = client.post("/v1/authorizations/verify") { contentType(ContentType.Application.Json); setBody(wrapper()) }
        assertEquals(HttpStatusCode.OK, r.status)
        val decoded = body(r.bodyAsText())
        assertEquals("reject", decoded.decision)
        assertEquals("NONCE_REUSE", decoded.reason)
    }

    @Test fun `missing quote returns 400`() = testApplication {
        application { configureAuthorization(AuthorizationEngine(clock = { now })) }
        val r = client.post("/v1/authorizations/verify") {
            contentType(ContentType.Application.Json); setBody("""{"authorization":$authz}""")
        }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }

    @Test fun `malformed body returns 400`() = testApplication {
        application { configureAuthorization(AuthorizationEngine(clock = { now })) }
        val r = client.post("/v1/authorizations/verify") {
            contentType(ContentType.Application.Json); setBody("{ not json ")
        }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }

    @Test fun `structurally incomplete authorization returns 400`() = testApplication {
        application { configureAuthorization(AuthorizationEngine(clock = { now })) }
        val r = client.post("/v1/authorizations/verify") {
            contentType(ContentType.Application.Json)
            setBody("""{"authorization":{"type":"PaymentAuthorization"},"quote":$quote}""")
        }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }
}
