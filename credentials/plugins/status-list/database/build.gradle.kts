plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.credentials"
dependencies {
    api(project(":credentials:credential-api"))
    implementation(project(":common")) // Needed for Iri access

    // Kotlin Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // JDBC drivers
    implementation(libs.postgresql)
    implementation(libs.mysql.connector)
    implementation(libs.h2)

    // Connection pooling
    implementation(libs.hikaricp)

    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation(libs.testcontainers.postgresql)
}
