plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.credentials"

dependencies {
    implementation(project(":credentials:credential-api"))
    implementation(project(":credentials:plugins:status-list:bitstring"))
    implementation(project(":credentials:plugins:status-list:token"))
    implementation(project(":common"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.bundles.ktor.server)

    testImplementation(project(":testkit"))
    testImplementation(libs.kotlinx.coroutines.test)
}
