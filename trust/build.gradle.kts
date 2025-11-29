plugins {
    kotlin("jvm")
    // Note: trustweave.shared plugin removed - configuration now centralized in root build.gradle.kts
}

dependencies {
    implementation(kotlin("reflect"))
    implementation(project(":common"))         // Root-level common (exceptions, common utilities, JSON)
    implementation(project(":credentials:credential-core"))  // Credential DSLs and services
    implementation(project(":wallet:wallet-core"))      // Wallet interfaces and DSLs
    implementation(project(":anchors:anchor-core"))     // BlockchainAnchorClientFactory
    implementation(project(":did:did-core"))         // DID DSLs and services
    implementation(project(":kms:kms-core"))         // KMS service interfaces
    compileOnly(project(":credentials:plugins:status-list:database"))  // StatusListManagerFactory (compileOnly to avoid circular dependency)

    // Kotlin Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    testImplementation(project(":testkit"))
    testImplementation(project(":did:did-core"))
    testImplementation(project(":kms:kms-core"))
}


