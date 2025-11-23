plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.did"
dependencies {
    implementation(project(":common"))   // Root-level common (exceptions)
    implementation(project(":did:core"))
    implementation(project(":kms:core"))
    implementation(project(":anchors:core"))
    implementation(project(":credentials:core"))
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation(kotlin("test"))
}

