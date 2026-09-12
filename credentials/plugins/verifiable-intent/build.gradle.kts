plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.credentials"

dependencies {
    implementation(project(":common"))
    implementation(project(":kms:kms-core"))
    implementation(libs.nimbus.jose.jwt)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.slf4j.api)

    testImplementation(project(":testkit"))
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.postgresql)
    testImplementation(libs.hikaricp)
    testImplementation(project(":kms:plugins:inmemory"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotest.assertions.core)
}

// These tests write evidence that CI gates read back, and it is not a Gradle output unless it is
// declared here. Written outside the declared outputs, the JUnit XML survived a build-cache hit
// while the evidence silently did not — a cached test run then failed the reliability gate on a
// commit whose tests had all passed. Anchoring the path to the build directory and declaring it
// as an output makes the evidence travel with the cache entry that produced it.
tasks.test {
    // Keep qualification output outside Gradle's report tree. The test reporter is free to clean
    // build/reports after the test worker exits, which can otherwise erase evidence written by a
    // passing test before the following CI step reads it.
    val evidence = layout.buildDirectory.dir("qualification")
    systemProperty("trustweave.reports.dir", evidence.get().asFile.absolutePath)
    outputs.dir(evidence).withPropertyName("verifiableIntentEvidence")
}
