plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "io.geoknoesis.vericore"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(project(":vericore-core"))
    implementation(project(":vericore-anchor"))
    implementation(project(":vericore-json"))

    // Ktor for HTTP-based Indy pool communication
    // Using HTTP approach initially (can be upgraded to indy-vdr later)
    implementation(Libs.ktorClientCore)
    implementation(Libs.ktorClientCio)
    implementation(Libs.ktorClientContentNegotiation)
    implementation(Libs.ktorSerializationKotlinxJson)

    // Test dependencies
    testImplementation(project(":vericore-testkit"))
    testImplementation(project(":vericore-did"))
    testImplementation(project(":vericore-kms"))
}

