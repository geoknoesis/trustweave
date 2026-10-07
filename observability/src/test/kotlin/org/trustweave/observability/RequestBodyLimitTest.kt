package org.trustweave.observability

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteReadChannel
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RequestBodyLimitTest {
    private fun Application.limited(max: Long) {
        RequestBodyLimit.install(this, max)
        routing {
            // A handler with a blanket catch, like the real servers', must not be able to turn an
            // oversized body into some other status.
            post("/echo") {
                try {
                    call.respondText(call.receiveText())
                } catch (e: Exception) {
                    call.respondText("swallowed", status = HttpStatusCode.BadRequest)
                }
            }
            get("/read") { call.respondText("ok") }
        }
    }

    private class Chunked(
        private val bytes: ByteArray,
    ) : OutgoingContent.ReadChannelContent() {
        override val contentLength: Long? = null

        override fun readFrom(): ByteReadChannel = ByteReadChannel(bytes)
    }

    @Test
    fun `a body within the limit reaches the handler intact`() =
        testApplication {
            application { limited(16) }
            val response = client.post("/echo") { setBody("0123456789abcdef") }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("0123456789abcdef", response.bodyAsText())
        }

    @Test
    fun `a declared Content-Length over the limit is refused with 413`() =
        testApplication {
            application { limited(16) }
            val response = client.post("/echo") { setBody("x".repeat(17)) }
            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
            assertTrue("request_too_large" in response.bodyAsText())
        }

    @Test
    fun `a chunked body over the limit is refused with 413 although no length was declared`() =
        testApplication {
            application { limited(16) }
            val response =
                client.post("/echo") {
                    contentType(ContentType.Text.Plain)
                    setBody(Chunked("x".repeat(64 * 1024).toByteArray()))
                }
            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        }

    @Test
    fun `a chunked body within the limit is accepted`() =
        testApplication {
            application { limited(16) }
            val response = client.post("/echo") { setBody(Chunked("hello".toByteArray())) }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("hello", response.bodyAsText())
        }

    @Test
    fun `reads are not affected`() =
        testApplication {
            application { limited(1) }
            assertEquals(HttpStatusCode.OK, client.get("/read").status)
        }
}
