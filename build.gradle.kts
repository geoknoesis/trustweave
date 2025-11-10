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
            // Write jars to a dedicated directory to avoid collisions with any
            // files that might be held open by tools during iterative builds.
            destinationDirectory.set(layout.buildDirectory.dir("packaged-libs"))
        }
    }
}

// Apply Kover to all subprojects that have tests
subprojects {
    // Configure all module builds to go under a unified build directory
    if (project.name != "buildSrc") {
        layout.buildDirectory.set(rootProject.layout.buildDirectory.dir("modules/${project.name}"))
        apply(plugin = "org.jetbrains.kotlinx.kover")
    }
}

tasks.wrapper {
    gradleVersion = "8.5"
}

