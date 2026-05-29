plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.integrations"

dependencies {
    implementation(project(":common"))
    implementation(project(":credentials:credential-api"))
    implementation(project(":did:did-core"))

    // HTTP client (REST against Entra Verified ID + AAD token endpoint)
    implementation(libs.okhttp)

    // JSON serialization for wire format
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("com.github.tomakehurst:wiremock-jre8:2.35.2")
}
