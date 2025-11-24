plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave"
dependencies {
    implementation(project(":common"))    // Root-level common (exceptions, common utilities, JSON)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    // Note: credentials:credential-core dependency removed - anchors:anchor-core should not depend on credentials
    // BlockchainAnchorClientFactory is in this module
}

