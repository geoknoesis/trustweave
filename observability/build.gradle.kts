plugins {
    kotlin("jvm")
}

dependencies {
    api("io.opentelemetry:opentelemetry-api:1.65.0")
    implementation("io.opentelemetry:opentelemetry-extension-kotlin:1.65.0")
    implementation(libs.kotlinx.coroutines.core)
    api(libs.ktor.server.core)
    implementation(libs.hikaricp)
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing:1.65.0")
    testImplementation("io.opentelemetry:opentelemetry-sdk:1.65.0")
    testImplementation("io.opentelemetry:opentelemetry-exporter-otlp:1.65.0")
    testImplementation("io.opentelemetry.proto:opentelemetry-proto:1.11.0-alpha")
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.server.netty)
    testImplementation(libs.h2)
    testImplementation(libs.kotlinx.coroutines.test)
}
