plugins {
    kotlin("jvm")
}

group = "org.trustweave.signatures"

dependencies {
    implementation(project(":common"))
    implementation(project(":kms:kms-core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.slf4j.api)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.bundles.test.runtime)
    testImplementation(libs.kotlinx.coroutines.test)
}
