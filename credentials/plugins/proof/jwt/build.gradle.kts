plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.credentials"
dependencies {
    implementation(project(":common"))
    implementation(project(":credentials:credential-core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // JWT library
    implementation("com.nimbusds:nimbus-jose-jwt:9.37.3")

    // Test dependencies
    testImplementation(project(":testkit"))
}

