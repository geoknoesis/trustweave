plugins {
    kotlin("jvm")
}

group = "org.trustweave.kms"

dependencies {
    // API dependencies - exposed transitively to consumers
    api(project(":common"))
    api(project(":kms:kms-core"))
    
    // Implementation dependencies - internal only
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.api)
    // BouncyCastle for secp256k1 and Ed25519 support on older JVMs
    implementation(libs.bouncycastle.prov)

    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation(testFixtures(project(":kms:kms-core"))) // Access test classes via testFixtures
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

