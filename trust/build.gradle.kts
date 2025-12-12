plugins {
    kotlin("jvm")
    // Note: trustweave.shared plugin removed - configuration now centralized in root build.gradle.kts
}

dependencies {
    implementation(kotlin("reflect"))
    implementation(project(":common"))         // Root-level common (exceptions, common utilities, JSON)
    implementation(project(":credentials:credential-api"))  // Credential DSLs and services
    implementation(project(":wallet:wallet-core"))      // Wallet interfaces and DSLs
    implementation(project(":anchors:anchor-core"))     // BlockchainAnchorClientFactory
    implementation(project(":did:did-core"))         // DID DSLs and services
    implementation(project(":kms:kms-core"))         // KMS service interfaces
    implementation(project(":contract"))              // Smart contract services
    compileOnly(project(":credentials:plugins:status-list:database"))  // StatusListManagerFactory (compileOnly to avoid circular dependency)

    // Kotlin Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)
    
    // Kotlinx DateTime
    implementation(libs.kotlinx.datetime)
    
    // SLF4J for logging (compileOnly - plugins provide implementation)
    compileOnly("org.slf4j:slf4j-api:2.0.9")

    testImplementation(project(":testkit"))
    testImplementation(project(":did:did-core"))
    testImplementation(project(":kms:kms-core"))
    testImplementation(project(":credentials:credential-api"))
    testImplementation(project(":credentials:plugins:status-list:database"))  // StatusListRegistryFactory for tests
}


