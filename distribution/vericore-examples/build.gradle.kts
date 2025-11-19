import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlinx.kover") version "0.7.6"
}

group = "com.geoknoesis.vericore"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(project(":core:vericore-core"))
    implementation(project(":core:vericore-spi"))
    implementation(project(":core:vericore-trust"))
    implementation(project(":core:vericore-json"))
    implementation(project(":distribution:vericore-all"))
    implementation(project(":chains:vericore-anchor"))
    implementation(project(":did:vericore-did"))
    implementation(project(":kms:vericore-kms"))
    implementation(project(":core:vericore-testkit"))
    
    // For blockchain examples
    implementation(project(":chains:plugins:ganache"))
    implementation(project(":chains:plugins:indy"))
    implementation(project(":chains:plugins:ethereum"))
    implementation(project(":chains:plugins:base"))
    implementation(project(":chains:plugins:arbitrum"))
    implementation(project(":did:plugins:key"))
    implementation(project(":did:plugins:jwk"))
    
    // Test dependencies
    testImplementation(Libs.kotlinTest)
    testImplementation(Libs.junitJupiter)
}

// Configure Java toolchain for all JavaExec tasks
val javaToolchain = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}

// Configure main class for running examples
tasks.register<JavaExec>("runEarthObservation") {
    group = "examples"
    description = "Run Earth Observation scenario example"
    mainClass.set("com.geoknoesis.vericore.examples.eo.EarthObservationExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runAcademicCredentials") {
    group = "examples"
    description = "Run Academic Credentials scenario example"
    mainClass.set("com.geoknoesis.vericore.examples.academic.AcademicCredentialsExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runProfessionalIdentity") {
    group = "examples"
    description = "Run Professional Identity scenario example"
    mainClass.set("com.geoknoesis.vericore.examples.professional.ProfessionalIdentityExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runProofOfLocation") {
    group = "examples"
    description = "Run Proof of Location scenario example"
    mainClass.set("com.geoknoesis.vericore.examples.location.ProofOfLocationExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runSpatialWeb") {
    group = "examples"
    description = "Run Spatial Web Authorization scenario example"
    mainClass.set("com.geoknoesis.vericore.examples.spatial.SpatialWebExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runDigitalWorkflow") {
    group = "examples"
    description = "Run Digital Workflow Provenance scenario example"
    mainClass.set("com.geoknoesis.vericore.examples.workflow.DigitalWorkflowExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runNewsIndustry") {
    group = "examples"
    description = "Run News Industry scenario example"
    mainClass.set("com.geoknoesis.vericore.examples.news.NewsIndustryExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runDataCatalog") {
    group = "examples"
    description = "Run Data Catalog DCAT scenario example"
    mainClass.set("com.geoknoesis.vericore.examples.dcat.DataCatalogExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runHealthcare") {
    group = "examples"
    description = "Run Healthcare Medical Records scenario example"
    mainClass.set("com.geoknoesis.vericore.examples.healthcare.HealthcareExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runGovernment") {
    group = "examples"
    description = "Run Government Digital Identity scenario example"
    mainClass.set("com.geoknoesis.vericore.examples.government.GovernmentIdentityExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runSupplyChain") {
    group = "examples"
    description = "Run Supply Chain Traceability scenario example"
    mainClass.set("com.geoknoesis.vericore.examples.supplychain.SupplyChainExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runFinancialServices") {
    group = "examples"
    description = "Run Financial Services KYC scenario example"
    mainClass.set("com.geoknoesis.vericore.examples.financial.FinancialServicesExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runIoT") {
    group = "examples"
    description = "Run IoT Device Identity scenario example"
    mainClass.set("com.geoknoesis.vericore.examples.iot.IoTDeviceExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runNationalEducation") {
    group = "examples"
    description = "Run National Education Credentials Algeria scenario example"
    mainClass.set("com.geoknoesis.vericore.examples.national.NationalEducationExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runQuickStartSample") {
    group = "examples"
    description = "Run Quick Start credential issuance sample"
    mainClass.set("com.geoknoesis.vericore.examples.quickstart.QuickStartSampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runIndyIntegration") {
    group = "examples"
    description = "Run Indy Integration scenario example"
    mainClass.set("com.geoknoesis.vericore.examples.indy.IndyIntegrationExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runKeyDid") {
    group = "examples"
    description = "Run did:key example"
    mainClass.set("com.geoknoesis.vericore.examples.did_key.KeyDidExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runJwkDid") {
    group = "examples"
    description = "Run did:jwk example"
    mainClass.set("com.geoknoesis.vericore.examples.did_jwk.JwkDidExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}

tasks.register<JavaExec>("runBlockchainAnchoring") {
    group = "examples"
    description = "Run blockchain anchoring example (Ethereum, Base, Arbitrum)"
    mainClass.set("com.geoknoesis.vericore.examples.blockchain.BlockchainAnchoringExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchain)
}
