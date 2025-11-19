plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.geoknoesis.vericore.did"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(project(":core:vericore-core"))
    implementation(project(":did:vericore-did"))
    implementation(project(":kms:vericore-kms"))
    implementation(project(":core:vericore-json"))

    // Ktor Client for HTTP requests
    implementation(Libs.ktorClientCore)
    implementation(Libs.ktorClientCio)
    implementation(Libs.ktorClientContentNegotiation)
    implementation(Libs.ktorSerializationKotlinxJson)

    // Test dependencies
    testImplementation(project(":core:vericore-testkit"))
    testImplementation(project(":chains:vericore-anchor"))
}

