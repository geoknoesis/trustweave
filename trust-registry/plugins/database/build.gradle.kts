plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.registry"

dependencies {
    implementation(project(":trust-registry:trust-registry-core"))
    implementation(project(":common"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.hikaricp)
    runtimeOnly(libs.h2)

    testImplementation(project(":testkit"))
    testImplementation(libs.h2)
    testImplementation(libs.hikaricp)
    testImplementation(libs.kotlinx.coroutines.test)
}
