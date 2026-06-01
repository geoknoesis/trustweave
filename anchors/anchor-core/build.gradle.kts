plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave"
dependencies {
    implementation(project(":common"))    // Root-level common (exceptions, common utilities, JSON)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.slf4j.api)
    // Note: credentials:credential-core dependency removed - anchors:anchor-core should not depend on credentials
    // BlockchainAnchorClientFactory is in this module
}

