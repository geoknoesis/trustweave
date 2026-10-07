package org.trustweave.observability

import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.ApplicationReceivePipeline
import io.ktor.server.request.contentLength
import io.ktor.server.request.httpMethod
import io.ktor.server.response.respondText
import io.ktor.util.AttributeKey
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream

/**
 * Bounds the size of request bodies on the embedded servers.
 *
 * Without a bound, a caller (authenticated or not) can have the server buffer an arbitrarily large
 * body while `ContentNegotiation` parses it. The limit is enforced twice, because both signals are
 * attacker-controlled: the declared `Content-Length` is checked first as a cheap reject, and the
 * body is then read through a bounded buffer, which is what actually enforces it for chunked
 * requests that declare no length. Either failure answers `413 Payload Too Large`.
 *
 * Install it after the authentication gate (so unauthenticated callers are refused before any body
 * is read) and before the routes. The body is read before the handler runs, so a handler's own
 * error handling can never turn an oversized request into a different status.
 */
public object RequestBodyLimit {
    /** 1 MiB: far above any JSON request these servers take, far below what exhausts memory. */
    public const val DEFAULT_MAX_BYTES: Long = 1024L * 1024

    private val BufferedBody = AttributeKey<ByteArray>("trustweave.requestBodyLimit.body")

    private val BODY_METHODS = setOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch, HttpMethod.Delete)

    @JvmStatic
    @JvmOverloads
    public fun install(
        application: Application,
        maxBytes: Long = DEFAULT_MAX_BYTES,
    ) {
        require(maxBytes > 0) { "maxBytes must be positive" }
        application.intercept(ApplicationCallPipeline.Plugins) {
            val declared = call.request.contentLength()
            val mayHaveBody = call.request.httpMethod in BODY_METHODS && declared != 0L
            if (!mayHaveBody) {
                proceed()
                return@intercept
            }
            if (declared != null && declared > maxBytes) {
                call.tooLarge(maxBytes)
                finish()
                return@intercept
            }
            val bytes = readBounded(call.request.receiveChannel(), maxBytes)
            if (bytes == null) {
                call.tooLarge(maxBytes)
                finish()
                return@intercept
            }
            call.attributes.put(BufferedBody, bytes)
            proceed()
        }
        // The channel was consumed above; hand the handler the buffered bytes instead.
        application.receivePipeline.intercept(ApplicationReceivePipeline.Before) { body ->
            val buffered = call.attributes.getOrNull(BufferedBody)
            if (buffered != null && body is ByteReadChannel) {
                proceedWith(ByteReadChannel(buffered))
            }
        }
    }

    /** Returns the body, or null when it is longer than [maxBytes]. Never holds more than one byte over. */
    private suspend fun readBounded(
        channel: ByteReadChannel,
        maxBytes: Long,
    ): ByteArray? {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        while (true) {
            val read = channel.readAvailable(chunk, 0, chunk.size)
            if (read < 0) break
            if (read == 0) continue
            if (buffer.size() + read > maxBytes) return null
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    private suspend fun ApplicationCall.tooLarge(maxBytes: Long) {
        response.headers.append("Cache-Control", "no-store")
        respondText(
            """{"error":"request_too_large","message":"Request body exceeds $maxBytes bytes"}""",
            ContentType.Application.Json,
            HttpStatusCode.PayloadTooLarge,
        )
    }
}
