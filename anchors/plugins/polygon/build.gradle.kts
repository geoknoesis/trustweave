plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.chains"
dependencies {
    implementation(project(":credentials:core"))
    implementation(project(":anchors:core"))
    implementation("org.web3j:core:5.0.1")

    // Test dependencies
    testImplementation(project(":testkit"))
}

