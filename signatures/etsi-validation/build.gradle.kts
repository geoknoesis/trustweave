plugins {
    kotlin("jvm")
}

group = "org.trustweave.signatures"

dependencies {
    implementation(project(":common"))
    implementation(project(":signatures:jades"))
    implementation(project(":signatures:tsa-core"))
    implementation(project(":signatures:trust-lists"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.bundles.bouncycastle)
    implementation(libs.slf4j.api)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.bundles.test.runtime)
    testImplementation(libs.kotlinx.coroutines.test)
    // Test-only: KMS API surface is needed by the inlined TestKms fixture.
    testImplementation(project(":kms:kms-core"))
}
