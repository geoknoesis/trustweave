plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave"
dependencies {
    implementation(kotlin("reflect"))
    implementation(project(":credentials:credential-core"))
    implementation(project(":anchors:anchor-core"))
    implementation(project(":did:did-core"))
    
    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation(project(":did:did-core"))
    testImplementation(project(":kms:kms-core"))
}


