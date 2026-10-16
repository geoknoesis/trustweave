package org.trustweave.did.registrar.server.spring

import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.http.server.reactive.ServerHttpRequestDecorator
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Bounds the size of request bodies sent to the registrar API (`/1.0/...`), mirroring the Ktor
 * server's `RequestBodyLimit`: a body larger than [maxBytes] is answered with 413 before a handler
 * parses it.
 *
 * A request that declares a `Content-Length` over the limit is refused immediately. A body without
 * one (chunked) is counted as it streams, and the read fails with 413 once it passes the limit.
 * Other paths are left alone. WebFlux's own codec buffer limit (`spring.codec.max-in-memory-size`,
 * 256 KiB by default) still applies on top of this one.
 *
 * @param maxBytes largest accepted body, in bytes
 */
class RegistrarRequestBodyLimit(
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) : WebFilter {
    init {
        require(maxBytes > 0) { "maxBytes must be positive" }
    }

    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> {
        if (!exchange.request.path
                .value()
                .startsWith(API_PREFIX)
        ) {
            return chain.filter(exchange)
        }

        val declared = exchange.request.headers.contentLength
        if (declared > maxBytes) {
            exchange.response.statusCode = HttpStatus.PAYLOAD_TOO_LARGE
            return exchange.response.setComplete()
        }

        val limited =
            object : ServerHttpRequestDecorator(exchange.request) {
                override fun getBody(): Flux<DataBuffer> {
                    var seen = 0L
                    return super.getBody().map { buffer ->
                        seen += buffer.readableByteCount()
                        if (seen > maxBytes) {
                            DataBufferUtils.release(buffer)
                            throw ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Request body too large")
                        }
                        buffer
                    }
                }
            }
        return chain.filter(exchange.mutate().request(limited as ServerHttpRequest).build())
    }

    companion object {
        /** Same default as the Ktor server: 1 MiB. */
        const val DEFAULT_MAX_BYTES: Long = 1024L * 1024L
        private const val API_PREFIX = "/1.0/"
    }
}
