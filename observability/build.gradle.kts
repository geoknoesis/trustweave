plugins {
    kotlin("jvm")
}

dependencies {
    // The library telemetry SPI lives in :common so the core modules stay free of
    // OpenTelemetry; this module is the adapter, so it needs both sides.
    api(project(":common"))
    api(libs.opentelemetry.api)
    implementation(libs.opentelemetry.extension.kotlin)
    implementation(libs.kotlinx.coroutines.core)
    api(libs.ktor.server.core)
    implementation(libs.hikaricp)
    testImplementation(libs.opentelemetry.sdk.testing)
    testImplementation(libs.opentelemetry.sdk)
    testImplementation(libs.opentelemetry.exporter.otlp)
    testImplementation(libs.opentelemetry.proto)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.server.netty)
    testImplementation(libs.h2)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Same contract as the verifiable-intent module: evidence read back by CI gates has to be a
// declared output, or a build-cache hit restores the test results without it.
tasks.test {
    val evidence = layout.buildDirectory.dir("reports")
    systemProperty("trustweave.reports.dir", evidence.get().asFile.absolutePath)
    outputs.dir(layout.buildDirectory.dir("reports/host-otlp")).withPropertyName("hostExportEvidence")
}
