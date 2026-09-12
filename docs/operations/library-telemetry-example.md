# Executable library telemetry example

This entire Kotlin file is compiled and run by `:observability:test`. It installs the bridge,
records a DID resolution under a request id, reports a rejected verification, and asserts that
both reach the trace and the scrape.

Run `./gradlew :observability:test --tests '*LibraryTelemetryDocumentationExampleTest'` from the
SDK root with JDK 21. See the [library telemetry runbook](library-telemetry.md) for what each
metric means and why `rejected` is not `failure`.

<!-- example-source: observability/src/test/kotlin/org/trustweave/observability/LibraryTelemetryDocumentationExampleTest.kt -->
```kotlin
package org.trustweave.observability

import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import org.trustweave.core.telemetry.Operation
import org.trustweave.core.telemetry.Telemetry
import org.trustweave.core.telemetry.TelemetryContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryTelemetryDocumentationExampleTest {
    @Test
    fun `a library operation reaches the host trace and scrape under the request that caused it`() {
        val spans = InMemorySpanExporter.create()
        val openTelemetry =
            OpenTelemetrySdk
                .builder()
                .setTracerProvider(
                    SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(spans)).build(),
                ).build()

        // One process-wide install, usually at startup. Until this runs the SDK records nothing.
        val library = LibraryTelemetry(openTelemetry)
        Telemetry.install(library)
        try {
            // The embedded servers do this for you: HostObservability puts each request's
            // X-Request-ID into the coroutine context so library work inherits it.
            runBlocking {
                withContext(TelemetryContext("request-1")) {
                    Telemetry.measure(Operation.DID_RESOLVE, mapOf("did.method" to "key")) {
                        "did:key:z6MkResolved"
                    }
                }

                // A credential that fails verification is the library working, not an error:
                // it is reported as REJECTED so alerting on failures does not page on it.
                Telemetry.rejected(Operation.CREDENTIAL_VERIFY, "SignatureInvalid")
            }

            val resolution = spans.finishedSpanItems.first { it.name == "did_resolve" }
            val attributes = resolution.attributes.asMap().mapKeys { it.key.key }
            assertEquals("success", attributes["trustweave.outcome"])
            assertEquals("request-1", attributes["trustweave.request_id"])
            assertEquals("key", attributes["did.method"])

            val scrape = library.prometheus()
            assertTrue(
                "trustweave_library_operations_total{operation=\"did_resolve\",outcome=\"success\"} 1" in scrape,
                scrape,
            )
            assertTrue(
                "trustweave_library_operations_total{operation=\"credential_verify\",outcome=\"rejected\"} 1" in scrape,
                scrape,
            )
        } finally {
            Telemetry.uninstall()
            openTelemetry.close()
        }
    }
}
```
