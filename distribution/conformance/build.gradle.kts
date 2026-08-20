import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult
import org.gradle.kotlin.dsl.KotlinClosure2

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

    // Guards against the exact bug class found (2026-08-19) in DidCore11ConformanceTest: a @Test
    // method whose inferred return type is non-Unit (e.g. its body ends in the value-returning
    // kotlin.test.assertNotNull rather than a Unit-returning assertion) is silently dropped by
    // JUnit Jupiter at discovery time, with only a non-fatal warning in stderr — the task still
    // reports BUILD SUCCESSFUL while the suite quietly runs fewer tests than it declares. That
    // defeats this module's purpose: a conformance suite that under-runs without failing the build
    // is worse than one that is visibly red. This floor is deliberately exact to the count at the
    // time it was added rather than loosely padded, so it fails on the very next such regression
    // instead of tolerating a growing gap between declared and executed tests.
    //
    // Bump this number UP when intentionally adding conformance tests. If it fires unexpectedly,
    // do not just bump it — open the HTML report (reports/conformance/html/index.html) and check
    // its stderr panel for a "must not return a value. It will not be executed." warning first.
    val minimumExpectedTestCount = 38
    afterSuite(
        KotlinClosure2<TestDescriptor, TestResult, Unit>({ descriptor, result ->
            if (descriptor.parent == null) {
                val total = result.testCount.toInt()
                if (total < minimumExpectedTestCount) {
                    throw GradleException(
                        "Conformance suite executed only $total tests; expected at least " +
                            "$minimumExpectedTestCount. This usually means JUnit Jupiter silently " +
                            "dropped a @Test method at discovery time (e.g. a non-Unit-returning " +
                            "expression-bodied test) rather than a genuine drop in test count — " +
                            "check the HTML report's stderr panel before assuming this threshold " +
                            "is simply stale."
                    )
                }
            }
        })
    )
}
