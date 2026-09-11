plugins {
    kotlin("jvm")
}

dependencies {
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
