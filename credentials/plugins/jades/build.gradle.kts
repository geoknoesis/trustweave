plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.credentials"

dependencies {
    implementation(project(":credentials:credential-api"))
    implementation(project(":common"))
    implementation(project(":kms:kms-core"))
    implementation(project(":did:did-core"))
    implementation(project(":signatures:jades"))
    implementation(project(":signatures:tsa-core"))
    implementation(project(":signatures:trust-lists"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.slf4j.api)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.bundles.test.runtime)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.bundles.bouncycastle)
    testImplementation(project(":testkit"))
}
