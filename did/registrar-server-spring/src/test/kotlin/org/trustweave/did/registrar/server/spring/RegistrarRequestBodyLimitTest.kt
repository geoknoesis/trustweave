package org.trustweave.did.registrar.server.spring

import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Flux
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegistrarRequestBodyLimitTest {
    private val factory = DefaultDataBufferFactory.sharedInstance

    private fun exchange(
        body: String,
        declareLength: Boolean = true,
        path: String = "/1.0/dids",
    ): MockServerWebExchange {
        val bytes = body.toByteArray()
        val builder = MockServerHttpRequest.post(path).contentType(MediaType.APPLICATION_JSON)
        if (declareLength) builder.contentLength(bytes.size.toLong())
        return MockServerWebExchange.from(builder.body(Flux.just(factory.wrap(bytes))))
    }

    private fun chainReadingBody(reached: BooleanArray = BooleanArray(1)): WebFilterChain =
        WebFilterChain { ex ->
            reached[0] = true
            ex.request.body.then()
        }

    @Test
    fun `a declared content length over the limit is refused with 413 and not forwarded`() {
        val reached = BooleanArray(1)
        val ex = exchange("x".repeat(200))
        RegistrarRequestBodyLimit(64).filter(ex, chainReadingBody(reached)).block()
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, ex.response.statusCode)
        assertFalse(reached[0])
    }

    @Test
    fun `an undeclared length that streams past the limit fails the read`() {
        val ex = exchange("x".repeat(200), declareLength = false)
        assertFailsWith<Exception> { RegistrarRequestBodyLimit(64).filter(ex, chainReadingBody()).block() }
    }

    @Test
    fun `a body within the limit passes`() {
        val reached = BooleanArray(1)
        val ex = exchange("""{"method":"key"}""")
        RegistrarRequestBodyLimit(64).filter(ex, chainReadingBody(reached)).block()
        assertTrue(reached[0])
    }

    @Test
    fun `paths outside the registrar API are not limited`() {
        val reached = BooleanArray(1)
        val ex = exchange("x".repeat(200), path = "/other")
        RegistrarRequestBodyLimit(64).filter(ex, chainReadingBody(reached)).block()
        assertTrue(reached[0])
    }

    @Test
    fun `a non-positive limit is refused`() {
        assertFailsWith<IllegalArgumentException> { RegistrarRequestBodyLimit(0) }
    }
}
