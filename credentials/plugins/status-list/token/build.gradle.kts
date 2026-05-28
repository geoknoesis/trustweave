plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.credentials"

dependencies {
    implementation(project(":credentials:credential-api"))
    implementation(project(":credentials:plugins:status-list:database"))
    implementation(project(":kms:kms-core"))
    implementation(project(":common"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    implementation(libs.postgresql)
    implementation(libs.mysql.connector)
    implementation(libs.h2)
    implementation(libs.hikaricp)

    implementation(libs.nimbus.jose.jwt)

    implementation(libs.slf4j.api)

    testImplementation(project(":testkit"))
}
