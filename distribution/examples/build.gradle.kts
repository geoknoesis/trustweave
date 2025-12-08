import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(libs.plugins.kover)
}

group = "com.trustweave"
dependencies {
    implementation(project(":credentials:credential-api"))

    implementation(project(":trust"))
    implementation(project(":distribution:all"))
    implementation(project(":anchors:anchor-core"))
    implementation(project(":did:did-core"))
    implementation(project(":kms:kms-core"))
    implementation(project(":testkit"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // For blockchain examples
    implementation(project(":anchors:plugins:ganache"))
    implementation(project(":anchors:plugins:indy"))
    implementation(project(":anchors:plugins:ethereum"))
    implementation(project(":anchors:plugins:base"))
    implementation(project(":anchors:plugins:arbitrum"))
    implementation(project(":did:plugins:key"))
    implementation(project(":did:plugins:jwk"))

    // Test dependencies are standardized in root build.gradle.kts
}

// Configure Java toolchain for all JavaExec tasks
val javaToolchain = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}

// Configure main class for running examples
tasks.register<JavaExec>("runEarthObservation") {
    group = "examples"
    description = "Run Earth Observation scenario example"
    mainClass.set("com.trustweave.examples.eo.EarthObservationExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runAcademicCredentials") {
    group = "examples"
    description = "Run Academic Credentials scenario example"
    mainClass.set("com.trustweave.examples.academic.AcademicCredentialsExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runProfessionalIdentity") {
    group = "examples"
    description = "Run Professional Identity scenario example"
    mainClass.set("com.trustweave.examples.professional.ProfessionalIdentityExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runProofOfLocation") {
    group = "examples"
    description = "Run Proof of Location scenario example"
    mainClass.set("com.trustweave.examples.location.ProofOfLocationExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runSpatialWeb") {
    group = "examples"
    description = "Run Spatial Web Authorization scenario example"
    mainClass.set("com.trustweave.examples.spatial.SpatialWebExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runDigitalWorkflow") {
    group = "examples"
    description = "Run Digital Workflow Provenance scenario example"
    mainClass.set("com.trustweave.examples.workflow.DigitalWorkflowExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runNewsIndustry") {
    group = "examples"
    description = "Run News Industry scenario example"
    mainClass.set("com.trustweave.examples.news.NewsIndustryExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runDataCatalog") {
    group = "examples"
    description = "Run Data Catalog DCAT scenario example"
    mainClass.set("com.trustweave.examples.dcat.DataCatalogExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runHealthcare") {
    group = "examples"
    description = "Run Healthcare Medical Records scenario example"
    mainClass.set("com.trustweave.examples.healthcare.HealthcareExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runGovernment") {
    group = "examples"
    description = "Run Government Digital Identity scenario example"
    mainClass.set("com.trustweave.examples.government.GovernmentIdentityExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runSupplyChain") {
    group = "examples"
    description = "Run Supply Chain Traceability scenario example"
    mainClass.set("com.trustweave.examples.supplychain.SupplyChainExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runFinancialServices") {
    group = "examples"
    description = "Run Financial Services KYC scenario example"
    mainClass.set("com.trustweave.examples.financial.FinancialServicesExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runIoT") {
    group = "examples"
    description = "Run IoT Device Identity scenario example"
    mainClass.set("com.trustweave.examples.iot.IoTDeviceExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runNationalEducation") {
    group = "examples"
    description = "Run National Education Credentials Algeria scenario example"
    mainClass.set("com.trustweave.examples.national.NationalEducationExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runQuickStartSample") {
    group = "examples"
    description = "Run Quick Start credential issuance sample"
    mainClass.set("com.trustweave.examples.quickstart.QuickStartSampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runIndyIntegration") {
    group = "examples"
    description = "Run Indy Integration scenario example"
    mainClass.set("com.trustweave.examples.indy.IndyIntegrationExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runKeyDid") {
    group = "examples"
    description = "Run did:key example"
    mainClass.set("com.trustweave.examples.did_key.KeyDidExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runJwkDid") {
    group = "examples"
    description = "Run did:jwk example"
    mainClass.set("com.trustweave.examples.did_jwk.JwkDidExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runBlockchainAnchoring") {
    group = "examples"
    description = "Run blockchain anchoring example (Ethereum, Base, Arbitrum)"
    mainClass.set("com.trustweave.examples.blockchain.BlockchainAnchoringExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}
