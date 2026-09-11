plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.credentials"

dependencies {
    api(project(":observability"))
    implementation(project(":credentials:credential-api"))
    implementation(project(":credentials:plugins:status-list:bitstring"))
    implementation(project(":credentials:plugins:status-list:token"))
    implementation(project(":common"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.bundles.ktor.server)

    testImplementation(project(":testkit"))
    testImplementation(libs.h2)
    testImplementation(project(":kms:kms-core"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.opentelemetry.sdk.testing)
    testImplementation(libs.opentelemetry.sdk)
    // Exercise actual SLF4J emission and HTTP/log correlation; applications still select their own backend.
    testRuntimeOnly(libs.slf4j.simple)
}
