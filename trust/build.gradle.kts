plugins {
    kotlin("jvm")
    // Note: trustweave.shared plugin removed - configuration now centralized in root build.gradle.kts
    // JMH plugin - uncomment when ready to run benchmarks
    // id("me.champeau.jmh") version "0.7.2"
}

dependencies {
    implementation(kotlin("reflect"))
    // The facade's public API exposes types from all five domain core modules plus common
    // (e.g. TrustWeave.verify returns credential-api's VerificationResult, getKms() returns
    // kms-core's KeyManagementService, blockchains is anchor-core's BlockchainService,
    // contracts is contract's SmartContractService, getWalletFactory() is wallet-core's
    // WalletFactory, and TrustWeaveException/ConfigException come from common). They must be
    // `api` so consumers get them on their compile classpath transitively.
    api(project(":common"))         // Root-level common (exceptions, common utilities, JSON)
    api(project(":credentials:credential-api"))  // Credential DSLs and services
    api(project(":wallet:wallet-core"))      // Wallet interfaces and DSLs
    api(project(":anchors:anchor-core"))     // BlockchainAnchorClientFactory
    api(project(":did:did-core"))         // DID DSLs and services
    api(project(":kms:kms-core"))         // KMS service interfaces
    api(project(":contract"))              // Smart contract services
    compileOnly(project(":credentials:plugins:status-list:database"))  // StatusListManagerFactory (compileOnly to avoid circular dependency)

    // JAdES DSL extensions — plugin-specific types referenced only by the optional
    // org.trustweave.trust.dsl.credential.jades extensions. compileOnly so the trust
    // module does not pull the signatures stack into every consumer's classpath;
    // apps that call withJadesProfile(...) must depend on these modules explicitly
    // (or pull in the JAdES proof-engine plugin which already does so).
    compileOnly(project(":signatures:jades"))
    compileOnly(project(":signatures:trust-lists"))
    compileOnly(project(":signatures:tsa-core"))

    // Kotlin Coroutines — CoroutineDispatcher appears in public signatures
    // (TrustWeave.inMemory, Builder.dispatcher), so consumers need it at compile time.
    api(libs.kotlinx.coroutines.core)

    // Kotlinx Serialization — JsonElement appears in public signatures (anchorThroughDomain).
    api(libs.kotlinx.serialization.json)

    // Kotlinx DateTime — Instant appears in public types (TrustAnchorMetadata, TrustPath.Verified).
    api(libs.kotlinx.datetime)
    
    // CredentialBuilder logs directly via org.slf4j.LoggerFactory, so slf4j-api must be on the
    // runtime classpath (implementation, not compileOnly). Apps still supply the binding.
    implementation(libs.slf4j.api)

    testImplementation(project(":testkit"))
    testImplementation(project(":did:did-core"))
    testImplementation(project(":kms:kms-core"))
    testImplementation(project(":credentials:credential-api"))
    testImplementation(project(":credentials:plugins:status-list:database"))  // StatusListRegistryFactory for tests

    // JAdES extension tests need the same plugin-specific types the extensions reference.
    testImplementation(project(":signatures:jades"))
    testImplementation(project(":signatures:trust-lists"))
    testImplementation(project(":signatures:tsa-core"))
    
    // JMH for performance benchmarks (uncomment when JMH plugin is added)
    // jmhImplementation(project(":testkit"))
    // jmhImplementation(project(":credentials:credential-api"))
    // jmhImplementation(libs.kotlinx.coroutines.core)
}


