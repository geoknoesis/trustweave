plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.credentials"

dependencies {
    implementation(project(":common"))
    implementation(project(":kms:kms-core"))
    implementation(libs.nimbus.jose.jwt)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.slf4j.api)

    testImplementation(project(":testkit"))
    testImplementation(project(":kms:plugins:inmemory"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotest.assertions.core)
}
