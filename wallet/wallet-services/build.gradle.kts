plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave"

dependencies {
    api(project(":wallet:wallet-core"))
    implementation(project(":common"))
    implementation(project(":credentials:credential-api"))
    implementation(project(":did:did-core"))
    implementation(project(":credentials:plugins:oidc4vp"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
}
