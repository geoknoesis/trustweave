plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.credentials"

dependencies {
    implementation(project(":credentials:credential-api"))
    implementation(project(":did:did-core"))
    implementation(project(":credentials:plugins:openid-federation"))
    implementation(project(":common"))
    implementation(libs.nimbus.jose.jwt)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    testImplementation(project(":testkit"))
    testImplementation(libs.kotlinx.coroutines.test)
}
