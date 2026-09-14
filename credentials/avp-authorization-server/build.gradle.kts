plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.credentials"

dependencies {
    api(project(":observability"))
    implementation(project(":credentials:plugins:avp-micro"))
    implementation(project(":common"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.bundles.ktor.server)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
    // The durable store's guarantees are only meaningful against a real database: a fake would
    // prove the code paths, not that two instances sharing one server enforce one set of limits.
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.postgresql)
}
