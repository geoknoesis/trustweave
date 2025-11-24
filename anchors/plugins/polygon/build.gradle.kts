plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.chains"
dependencies {
    implementation(project(":credentials:credential-core"))
    implementation(project(":anchors:anchor-core"))
    implementation("org.web3j:core:5.0.1")

    // Test dependencies
    testImplementation(project(":testkit"))
}

