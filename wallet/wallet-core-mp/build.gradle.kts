import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

group = "org.trustweave"

kotlin {
    // Apply the default hierarchy so adding ios/js targets later is a one-line change.
    applyDefaultHierarchyTemplate()

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            // Future: implementation(project(":credentials:credential-models-mp"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            // Reserved for JVM-only re-exports / actuals as migration progresses.
        }
        jvmTest.dependencies {
            implementation(libs.junit.jupiter)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
