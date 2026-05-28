plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.signatures"

dependencies {
    implementation(project(":common"))
    implementation(project(":kms:kms-core"))
    implementation(project(":signatures:tsa-core"))
    implementation(project(":signatures:trust-lists"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.nimbus.jose.jwt)
    implementation(libs.bundles.bouncycastle)
    implementation(libs.slf4j.api)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.bundles.test.runtime)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation(project(":kms:plugins:inmemory"))
    testImplementation(project(":testkit"))
}
