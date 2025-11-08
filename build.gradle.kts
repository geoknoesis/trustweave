// Root build file - minimal configuration
// Shared configuration is in buildSrc

plugins {
    id("org.jetbrains.kotlinx.kover") version "0.7.6" apply false
}

allprojects {
    repositories {
        mavenCentral()
        maven("https://maven.waltid.dev/releases")
        maven("https://maven.waltid.dev/snapshots")
    }
}

// Apply Kover to all subprojects that have tests
subprojects {
    if (project.name != "buildSrc") {
        apply(plugin = "org.jetbrains.kotlinx.kover")
    }
}

tasks.wrapper {
    gradleVersion = "8.5"
}

