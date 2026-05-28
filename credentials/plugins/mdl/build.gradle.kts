plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.credentials"

dependencies {
    implementation(project(":credentials:credential-api"))
    implementation(project(":kms:kms-core"))
    implementation(project(":did:did-core"))
    implementation(project(":common"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    // CBOR: ISO 18013-5 compliant (tagged values, indefinite-length, mDoc-grade)
    implementation(libs.cbor.java)
    // COSE signing via Bouncy Castle
    implementation(libs.bouncycastle.prov)
    implementation(libs.bouncycastle.pkix)

    implementation(libs.okhttp)

    testImplementation(project(":testkit"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.runner.junit5)
}
