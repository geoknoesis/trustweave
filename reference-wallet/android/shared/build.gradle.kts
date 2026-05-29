import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Shared multiplatform wallet logic.
 *
 * Phase 2.5c scope:
 *  - SD-JWT VC encoding/decoding (commonMain, no platform deps)
 *  - base64url helpers via kotlin.io.encoding.Base64 (stable since Kotlin 1.9)
 *  - expect/actual for SHA-256 + secure random + Ed25519 sign/verify
 *  - JVM actuals (used by Android — BouncyCastle for Ed25519)
 *
 * Phase 2.5d scope:
 *  - iOS targets (iosX64, iosArm64, iosSimulatorArm64) — added when Xcode is available
 *  - iOS actuals using CommonCrypto + CryptoKit
 *
 * Note: this module deliberately does NOT depend on the trustweave main repo's
 * wallet-core-mp / credential-models-mp KMP modules. Those are the broader SDK
 * surface (issuer + verifier + holder); this is a focused holder-wallet starter.
 * A future refactor could collapse this into wallet-core-mp once that module gains
 * the SD-JWT VC + selective-disclosure capability surface.
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    applyDefaultHierarchyTemplate()

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }

    // iOS targets land in Phase 2.5d when an Xcode-equipped contributor wires them
    // up. Scaffold here keeps applyDefaultHierarchyTemplate() so adding them is a
    // one-line change per target with no source-set rearrangement.
    // iosX64(); iosArm64(); iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                // BouncyCastle for Ed25519 + SubjectPublicKeyInfo parsing on JVM/Android.
                implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
    }
}
