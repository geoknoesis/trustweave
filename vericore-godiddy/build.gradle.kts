plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.geoknoesis.vericore"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(project(":vericore-core"))
    implementation(project(":vericore-did"))
    implementation(project(":vericore-kms"))
    implementation(project(":vericore-json"))

    // Ktor Client for HTTP requests
    implementation(Libs.ktorClientCore)
    implementation(Libs.ktorClientCio)
    implementation(Libs.ktorClientContentNegotiation)
    implementation(Libs.ktorSerializationKotlinxJson)

    // Test dependencies
    testImplementation(project(":vericore-testkit"))
    testImplementation(project(":vericore-anchor"))
}

