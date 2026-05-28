plugins {
    kotlin("jvm")
}

group = "org.trustweave.distribution"

sourceSets {
    create("conformanceTest") {
        kotlin.srcDir("src/conformanceTest/kotlin")
        compileClasspath += sourceSets["main"].output + configurations["testRuntimeClasspath"]
        runtimeClasspath += output + compileClasspath
    }
}

val conformanceTestImplementation by configurations.getting {
    extendsFrom(configurations["testImplementation"])
}
val conformanceTestRuntimeOnly by configurations.getting {
    extendsFrom(configurations["testRuntimeOnly"])
}

dependencies {
    conformanceTestImplementation(project(":common"))
    conformanceTestImplementation(project(":kms:kms-core"))
    conformanceTestImplementation(project(":credentials:credential-api"))
    conformanceTestImplementation(project(":credentials:plugins:presentation-exchange"))
    conformanceTestImplementation(project(":did:did-core"))
    conformanceTestImplementation(project(":did:plugins:base"))
    conformanceTestImplementation(project(":did:plugins:key"))
    conformanceTestImplementation(project(":testkit"))
    conformanceTestImplementation(libs.kotlinx.serialization.json)
    conformanceTestImplementation(libs.kotlinx.coroutines.core)
    conformanceTestImplementation(libs.kotlinx.coroutines.test)
    conformanceTestImplementation(libs.kotlinx.datetime)
    conformanceTestImplementation(libs.junit.jupiter)
    conformanceTestRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.register<Test>("conformanceTest") {
    description = "Runs all Phase 1 conformance test suites"
    group = "verification"
    testClassesDirs = sourceSets["conformanceTest"].output.classesDirs
    classpath = sourceSets["conformanceTest"].runtimeClasspath
    useJUnitPlatform()
    reports {
        html.outputLocation.set(layout.buildDirectory.dir("reports/conformance/html"))
        junitXml.outputLocation.set(layout.buildDirectory.dir("reports/conformance/xml"))
    }
}
