package org.trustweave.observability

import com.sun.net.httpserver.HttpServer
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.Context
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class HostExportIntegrationTest {
    @Test
    fun `two real HTTP hosts export correlated traces through authenticated OTLP protobuf`(): Unit =
        runBlocking {
            val payloads = CopyOnWriteArrayList<ExportTraceServiceRequest>()
            val collector = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            collector.createContext("/v1/traces") { exchange ->
                exchange.use {
                    if (exchange.requestHeaders.getFirst("Authorization") != "Bearer fixture-collector-secret") {
                        exchange.sendResponseHeaders(401, -1)
                    } else {
                        assertEquals("application/x-protobuf", exchange.requestHeaders.getFirst("Content-Type"))
                        payloads.add(ExportTraceServiceRequest.parseFrom(exchange.requestBody))
                        exchange.responseHeaders.add("Content-Type", "application/x-protobuf")
                        exchange.sendResponseHeaders(200, -1)
                    }
                }
            }
            collector.start()
            val endpoint = "http://127.0.0.1:${collector.address.port}/v1/traces"
            val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
            val exporter =
                OtlpHttpSpanExporter
                    .builder()
                    .setEndpoint(endpoint)
                    .addHeader("Authorization", "Bearer fixture-collector-secret")
                    .setTimeout(Duration.ofSeconds(2))
                    .build()
            val processor =
                BatchSpanProcessor
                    .builder(exporter)
                    .setMaxQueueSize(128)
                    .setMaxExportBatchSize(16)
                    .setScheduleDelay(Duration.ofMillis(20))
                    .setExporterTimeout(Duration.ofSeconds(2))
                    .build()
            val provider = SdkTracerProvider.builder().addSpanProcessor(processor).build()
            try {
                OpenTelemetrySdk.builder().setTracerProvider(provider).build().use { sdk ->
                    val downstreamTelemetry = HostTelemetry(sdk)
                    val downstream =
                        embeddedServer(Netty, port = 0, host = "127.0.0.1") {
                            HostObservability(downstreamTelemetry, trustRemoteParent = true).install(this, HostKind.TRUST_REGISTRY)
                            routing { get("/lookup") { downstreamTelemetry.phase(HostPhase.VERIFY) { call.respondText("verified") } } }
                        }.start()
                    try {
                        val downstreamPort = downstream.resolvedConnectors().single().port
                        val upstreamTelemetry = HostTelemetry(sdk)
                        val upstream =
                            embeddedServer(Netty, port = 0, host = "127.0.0.1") {
                                HostObservability(upstreamTelemetry).install(this, HostKind.VC_API)
                                routing {
                                    get("/issue") {
                                        val body =
                                            upstreamTelemetry.phase(HostPhase.PROVIDER) {
                                                withContext(Dispatchers.IO) {
                                                    val request =
                                                        HttpRequest
                                                            .newBuilder(URI("http://127.0.0.1:$downstreamPort/lookup"))
                                                            .timeout(Duration.ofSeconds(3))
                                                    W3CTraceContextPropagator.getInstance().inject(
                                                        Context.current(),
                                                        request,
                                                    ) { carrier, key, value -> carrier?.header(key, value) }
                                                    http.send(request.build(), HttpResponse.BodyHandlers.ofString()).body()
                                                }
                                            }
                                        call.respondText(body)
                                    }
                                }
                            }.start()
                        try {
                            val upstreamPort = upstream.resolvedConnectors().single().port
                            val upstreamUri = URI("http://127.0.0.1:$upstreamPort/issue?credential=private-payload")
                            val responses =
                                (1..10)
                                    .map {
                                        async(Dispatchers.IO) {
                                            http.send(
                                                HttpRequest
                                                    .newBuilder(upstreamUri)
                                                    .timeout(Duration.ofSeconds(5))
                                                    .header("Authorization", "Bearer private-bearer")
                                                    .header("baggage", "private=private-baggage")
                                                    .build(),
                                                HttpResponse.BodyHandlers.ofString(),
                                            )
                                        }
                                    }.awaitAll()
                            assertTrue(responses.all { it.statusCode() == 200 && it.body() == "verified" })
                            assertTrue(provider.forceFlush().join(5, TimeUnit.SECONDS).isSuccess)
                            val spans =
                                payloads.flatMap { p ->
                                    p.resourceSpansList.flatMap { r -> r.scopeSpansList.flatMap { it.spansList } }
                                }
                            assertEquals(40, spans.size)
                            val traces = spans.groupBy { it.traceId }
                            assertEquals(10, traces.size)
                            for (trace in traces.values) {
                                val root = trace.single { it.name == "trustweave.vc_api" }
                                val outbound = trace.single { it.name == "trustweave.provider" }
                                val inbound = trace.single { it.name == "trustweave.trust_registry" }
                                val verify = trace.single { it.name == "trustweave.verify" }
                                assertEquals(root.spanId, outbound.parentSpanId)
                                assertEquals(outbound.spanId, inbound.parentSpanId)
                                assertEquals(inbound.spanId, verify.parentSpanId)
                            }
                            assertFalse(payloads.toString().contains("private-"))
                            assertFalse(payloads.toString().contains("fixture-collector-secret"))
                            assertEquals(
                                401,
                                http
                                    .send(
                                        HttpRequest.newBuilder(URI(endpoint)).POST(HttpRequest.BodyPublishers.noBody()).build(),
                                        HttpResponse.BodyHandlers.discarding(),
                                    ).statusCode(),
                            )
                            val output = evidenceDir("host-otlp")
                            Files.createDirectories(output)
                            payloads.forEachIndexed {
                                index,
                                payload,
                                ->
                                Files.write(output.resolve("batch-$index.pb"), payload.toByteArray())
                            }
                            Files.writeString(output.resolve("traces.txt"), payloads.joinToString("\n"))
                            Files.writeString(
                                output.resolve("summary.json"),
                                """{"hosts":2,"requests":10,"traces":10,"spans":40,
                                "transport":"OTLP HTTP/protobuf","authenticated":true,"redaction":"passed"}""",
                            )
                        } finally {
                            upstream.stop(0, 2000)
                        }
                    } finally {
                        downstream.stop(0, 2000)
                    }
                }
            } finally {
                collector.stop(0)
                http.close()
            }
        }

    @Test
    fun `blocked exporter uses a bounded queue and does not block application work`(): Unit =
        runBlocking {
            val entered = CountDownLatch(1)
            val complete = CompletableResultCode()
            val exporter =
                object : SpanExporter {
                    override fun export(spans: Collection<SpanData>): CompletableResultCode {
                        entered.countDown()
                        return complete
                    }

                    override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()

                    override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
                }
            val reader = InMemoryMetricReader.create()
            SdkMeterProvider.builder().registerMetricReader(reader).build().use { meters ->
                val processor =
                    BatchSpanProcessor
                        .builder(exporter)
                        .setMaxQueueSize(32)
                        .setMaxExportBatchSize(8)
                        .setMeterProvider(meters)
                        .setScheduleDelay(Duration.ofMillis(1))
                        .setExporterTimeout(Duration.ofSeconds(10))
                        .build()
                val provider = SdkTracerProvider.builder().addSpanProcessor(processor).build()
                OpenTelemetrySdk.builder().setTracerProvider(provider).build().use { sdk ->
                    try {
                        val telemetry = HostTelemetry(sdk)
                        telemetry.phase(HostPhase.VERIFY) {}
                        assertTrue(entered.await(2, TimeUnit.SECONDS))
                        val started = System.nanoTime()
                        repeat(10000) { telemetry.phase(HostPhase.VERIFY) {} }
                        assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(5))
                        val metrics = reader.collectAllMetrics()
                        val processed = metrics.single { it.name == "processedSpans" }.longSumData.points
                        assertTrue(
                            processed.any { point ->
                                point.attributes.asMap().any { it.key.key == "dropped" && it.value == true } &&
                                    point.value > 0
                            },
                        )
                        assertTrue(telemetry.prometheus().contains("trustweave_host_phase_seconds_count{phase=\"verify\"} 10001"))
                        val output = evidenceDir()
                        Files.createDirectories(output)
                        Files.writeString(output.resolve("host-export-backpressure.txt"), metrics.toString())
                    } finally {
                        complete.fail()
                    }
                }
            }
        }
}
