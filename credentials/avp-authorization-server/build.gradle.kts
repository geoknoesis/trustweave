plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.credentials"

dependencies {
    implementation(project(":credentials:plugins:avp-micro"))
    implementation(project(":common"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.bundles.ktor.server)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
}
