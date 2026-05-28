plugins {
    kotlin("jvm")
}

group = "org.trustweave.signatures"

dependencies {
    implementation(project(":common"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.bundles.bouncycastle)
    implementation(libs.slf4j.api)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.bundles.test.runtime)
    testImplementation(libs.kotlinx.coroutines.test)
}
