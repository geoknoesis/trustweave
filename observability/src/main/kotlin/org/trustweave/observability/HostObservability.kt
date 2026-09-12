package org.trustweave.observability

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.AttributeKey
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import kotlinx.coroutines.withContext
import org.trustweave.core.telemetry.TelemetryContext
import java.security.MessageDigest
import java.util.UUID
import javax.sql.DataSource

public val HostRequestId: AttributeKey<String> = AttributeKey("TrustWeaveRequestId")

/**
 * Explicit host configuration: metrics are absent unless a secret is provided. Remote trace
 * parents are ignored unless the host explicitly trusts its ingress. Baggage is never extracted.
 * Use TLS or a protected loopback proxy; bearer authentication does not encrypt the connection.
 */
public class HostObservability
    @JvmOverloads
    constructor(
        public val telemetry: HostTelemetry = HostTelemetry(),
        metricsBearerToken: String? = null,
        private val trustRemoteParent: Boolean = false,
        private val pool: DataSource? = null,
        /**
         * Bridges SDK operations onto this host's traces and metrics.
         *
         * Instrumentation used to stop at the HTTP boundary: a host saw that a request was slow
         * without seeing that the time went to a DID resolution or an HSM round trip. Pass a
         * [LibraryTelemetry] and the SDK's own operations join the same scrape and the same
         * request id. Null keeps the previous behaviour.
         */
        private val library: LibraryTelemetry? = null,
    ) {
        private val expectedAuthorization =
            metricsBearerToken?.let {
                require(
                    it.length in 32..256 &&
                        it.all { character ->
                            character.code in 33..126
                        },
                ) { "Metrics token must contain 32-256 printable non-space ASCII characters" }
                "Bearer $it".toByteArray(Charsets.UTF_8)
            }

        public fun install(
            application: Application,
            kind: HostKind,
        ) {
            application.intercept(ApplicationCallPipeline.Monitoring) {
                // Management traffic must remain available when application admission is saturated.
                if (call.request.path() == METRICS_PATH) {
                    proceed()
                    return@intercept
                }
                val requestId = UUID.randomUUID().toString()
                call.attributes.put(HostRequestId, requestId)
                call.response.headers.append("X-Request-ID", requestId)
                val header = if (trustRemoteParent) call.request.headers["traceparent"]?.takeIf { it.length <= 512 } else null
                val parent = W3CTraceContextPropagator.getInstance().extract(Context.root(), header, TRACE_GETTER)
                telemetry.request(kind, parent, { call.response.status()?.value ?: 200 }, { span ->
                    span.setAttribute("trustweave.request_id", requestId)
                    val method =
                        call.request.httpMethod.value
                            .takeIf { it in METHODS } ?: "_OTHER"
                    span.setAttribute("http.request.method", method)
                    if (span.spanContext.isValid) call.response.headers.append("X-Trace-ID", span.spanContext.traceId)
                }, {
                    call.response.headers.append("Retry-After", "1")
                    call.respond(HttpStatusCode.ServiceUnavailable)
                }) {
                    // Carry the request id into the SDK so a library operation can be joined to
                    // the request that caused it, rather than floating free in the trace.
                    withContext(TelemetryContext(requestId)) { proceed() }
                }
            }
            if (expectedAuthorization != null) {
                application.routing {
                    get(METRICS_PATH) {
                        val actual =
                            call.request.headers["Authorization"]
                                ?.takeIf { it.length <= 263 }
                                ?.toByteArray(Charsets.UTF_8) ?: byteArrayOf()
                        if (!MessageDigest.isEqual(expectedAuthorization, actual)) {
                            call.response.headers.append("WWW-Authenticate", "Bearer")
                            call.respond(HttpStatusCode.Unauthorized)
                        } else {
                            call.response.headers.append("Cache-Control", "no-store")
                            val scrape = telemetry.prometheus(pool) + (library?.prometheus() ?: "")
                            call.respondText(scrape, ContentType.parse("text/plain; version=0.0.4"))
                        }
                    }
                }
            }
        }

        private companion object {
            const val METRICS_PATH = "/internal/metrics"
            val METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "CONNECT", "TRACE")
            val TRACE_GETTER =
                object : TextMapGetter<String> {
                    override fun keys(carrier: String): Iterable<String> = listOf("traceparent")

                    override fun get(
                        carrier: String?,
                        key: String,
                    ): String? = if (key == "traceparent") carrier else null
                }
        }
    }
