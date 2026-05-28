plugins {
    kotlin("jvm")
}
group = "org.trustweave.kms"
dependencies {
    implementation(project(":common"))
    implementation(project(":kms:kms-core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.bundles.bouncycastle)
    implementation(libs.slf4j.api)
    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.bundles.test.runtime)
    testImplementation(libs.kotlinx.coroutines.test)
    // SoftHSM2-based integration tests (auto-skip if Docker / softhsm2 native lib unavailable).
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit)
}
