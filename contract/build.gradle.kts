plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave"
dependencies {
    implementation(kotlin("reflect"))
    implementation(project(":credentials:core"))
    implementation(project(":anchors:core"))
    implementation(project(":did:core"))
    
    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation(project(":did:core"))
    testImplementation(project(":kms:core"))
}


