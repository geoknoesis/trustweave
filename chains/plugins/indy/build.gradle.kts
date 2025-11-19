plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.geoknoesis.vericore.chains"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(project(":core:vericore-core"))
    implementation(project(":chains:vericore-anchor"))
    implementation(project(":core:vericore-json"))

    // Ktor for HTTP-based Indy pool communication
    // Using HTTP approach initially (can be upgraded to indy-vdr later)
    implementation(Libs.ktorClientCore)
    implementation(Libs.ktorClientCio)
    implementation(Libs.ktorClientContentNegotiation)
    implementation(Libs.ktorSerializationKotlinxJson)

    // Test dependencies
    testImplementation(project(":core:vericore-testkit"))
    testImplementation(project(":did:vericore-did"))
    testImplementation(project(":kms:vericore-kms"))
    testImplementation(project(":distribution:vericore-all")) // For VeriCore facade
}

