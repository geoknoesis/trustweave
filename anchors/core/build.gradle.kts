plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave"
dependencies {
    implementation(project(":common"))    // Root-level common (exceptions, common utilities, JSON)
    // Note: credentials:core dependency removed - anchors:core should not depend on credentials
    // BlockchainAnchorClientFactory is in this module
}

