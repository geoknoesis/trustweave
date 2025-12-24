plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.chains"
dependencies {
    implementation(project(":common"))
    implementation(project(":credentials:credential-api"))
    implementation(project(":anchors:anchor-core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation("org.web3j:core:5.0.1")

    // Test dependencies
    testImplementation(project(":testkit"))
    testRuntimeOnly(libs.junit.jupiter.engine)
}

