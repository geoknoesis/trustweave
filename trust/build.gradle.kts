plugins {
    kotlin("jvm")
    // Note: trustweave.shared plugin removed - configuration now centralized in root build.gradle.kts
}

dependencies {
    implementation(kotlin("reflect"))
    implementation(project(":common"))         // Root-level common (exceptions, common utilities, JSON)
    implementation(project(":credentials:core"))  // Credential DSLs and services
    implementation(project(":wallet:core"))      // Wallet interfaces and DSLs
    implementation(project(":anchors:core"))     // BlockchainAnchorClientFactory
    implementation(project(":did:core"))         // DID DSLs and services
    implementation(project(":kms:core"))         // KMS service interfaces
    compileOnly(project(":credentials:plugins:status-list:database"))  // StatusListManagerFactory (compileOnly to avoid circular dependency)

    testImplementation(project(":testkit"))
    testImplementation(project(":did:core"))
    testImplementation(project(":kms:core"))
}


