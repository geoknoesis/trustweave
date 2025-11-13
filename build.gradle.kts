// Root build file - minimal configuration
// Shared configuration is in buildSrc

plugins {
    id("org.jetbrains.kotlinx.kover") version "0.7.6" apply false
    `maven-publish` apply false
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

// Configure Maven publishing for all subprojects (except buildSrc)
subprojects {
    if (project.name != "buildSrc") {
        apply(plugin = "maven-publish")
        
        publishing {
            publications {
                create<MavenPublication>("maven") {
                    from(components["java"])
                    
                    pom {
                        name.set(project.name)
                        description.set(project.description ?: "")
                        groupId.set("com.geoknoesis.vericore")
                        artifactId.set(project.name)
                        version.set(project.version.toString())
                        
                        licenses {
                            license {
                                name.set("AGPL-3.0")
                                url.set("https://www.gnu.org/licenses/agpl-3.0.txt")
                            }
                        }
                        
                        developers {
                            developer {
                                id.set("vericore-team")
                                name.set("VeriCore Team")
                                email.set("info@geoknoesis.com")
                            }
                        }
                    }
                }
            }
            
            repositories {
                mavenLocal() // Publishes to ~/.m2/repository
            }
        }
    }
}

tasks.wrapper {
    gradleVersion = "8.5"
}

