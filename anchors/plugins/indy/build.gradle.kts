plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.chains"

// Custom integration test source set per CLAUDE.md acceptance criteria #4 — keeps
// container-driven tests separated from fast unit tests.
sourceSets {
    create("integrationTest") {
        kotlin.srcDir("src/integrationTest/kotlin")
        resources.srcDir("src/integrationTest/resources")
        compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        runtimeClasspath += output + compileClasspath
    }
}

val integrationTestImplementation by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}
val integrationTestRuntimeOnly by configurations.getting {
    extendsFrom(configurations.testRuntimeOnly.get())
}

dependencies {
    implementation(project(":common"))
    implementation(project(":anchors:anchor-core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.bundles.ktor.client)

    // Ed25519 signing for Indy ATTRIB requests.
    implementation(libs.bouncycastle.prov)

    // Test dependencies
    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.bundles.test.runtime)
    testImplementation("com.github.tomakehurst:wiremock-jre8-standalone:2.35.2")

    integrationTestImplementation(libs.testcontainers)
    integrationTestImplementation(libs.testcontainers.junit)
}

val integrationTest by tasks.registering(Test::class) {
    description = "Runs integration tests that hit a real Hyperledger Indy pool via TestContainers."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    shouldRunAfter("test")
    useJUnitPlatform()
    systemProperty("trustweave.indy.integration", System.getenv("TRUSTWEAVE_INDY_INTEGRATION") ?: "auto")
}
