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
    
    // License configuration
    afterEvaluate {
        tasks.withType<Jar>().configureEach {
            manifest {
                attributes(
                    mapOf(
                        "Implementation-Title" to project.name,
                        "Implementation-Version" to project.version,
                        "Implementation-Vendor" to "GeoKnoesis",
                        "Bundle-License" to "https://www.gnu.org/licenses/agpl-3.0.html",
                        "License" to "AGPL-3.0"
                    )
                )
            }
        }
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

