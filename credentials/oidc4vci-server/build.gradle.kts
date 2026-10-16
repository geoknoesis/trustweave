plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-test-fixtures`
}

group = "org.trustweave.credentials"

dependencies {
    api(project(":observability"))
    api(project(":credentials:credential-api"))
    implementation(project(":credentials:plugins:oidc4vci"))
    implementation(project(":common"))
    api(project(":did:did-core"))
    implementation(project(":kms:kms-core"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.bundles.ktor.server)

    testImplementation(project(":testkit"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)

    // Oidc4VciIssuerStateStoreContract, reusable by any store implementation's tests.
    testFixturesImplementation(project(":credentials:plugins:oidc4vci"))
    testFixturesImplementation(libs.kotlin.test)
    testFixturesImplementation(libs.junit.jupiter.api)
}

// java-test-fixtures puts this module's own jar on the test classpath (testFixtures -> main); declare
// it a friend so tests keep seeing main's internal declarations.
tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileTestKotlin") {
    friendPaths.from(tasks.named<Jar>("jar").flatMap { it.archiveFile })
}
