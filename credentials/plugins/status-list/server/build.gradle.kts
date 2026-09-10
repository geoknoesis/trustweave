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
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing:1.65.0")
    testImplementation("io.opentelemetry:opentelemetry-sdk:1.65.0")
    // Exercise actual SLF4J emission and HTTP/log correlation; applications still select their own backend.
    testRuntimeOnly("org.slf4j:slf4j-simple:${libs.versions.slf4j.get()}")
}
