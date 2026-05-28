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
    implementation(libs.bundles.ktor.server)

    testImplementation(project(":trust-registry:trust-registry-core"))
    testImplementation(project(":testkit"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
}
