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
    // Only configure projects that have build.gradle.kts files (skip intermediate directories)
    val buildFile = project.projectDir.resolve("build.gradle.kts")
    if (buildFile.exists() && project.name != "buildSrc") {
        // Use project path instead of name to avoid conflicts (e.g., did:plugins:base vs chains:plugins:base)
        val buildDirName = project.path.replace(":", "-").replaceFirst("^-", "")
        layout.buildDirectory.set(rootProject.layout.buildDirectory.dir("modules/$buildDirName"))
        apply(plugin = "org.jetbrains.kotlinx.kover")
        
        // Kover configuration can be customized per-project if needed
        // Default thresholds are defined in TestCoverageConfig
    }
}

// Configure Maven publishing for all subprojects (except buildSrc and vericore-bom which has its own config)
subprojects {
    val buildFile = project.projectDir.resolve("build.gradle.kts")
    if (buildFile.exists() && project.name != "buildSrc" && project.name != "vericore-bom") {
        apply(plugin = "maven-publish")
    }
}

// Configure publishing extension after plugin is applied
subprojects {
    val buildFile = project.projectDir.resolve("build.gradle.kts")
    if (buildFile.exists() && project.name != "buildSrc" && project.name != "vericore-bom") {
        afterEvaluate {
            // Only configure if publishing plugin is applied and no publication exists yet
            if (pluginManager.hasPlugin("maven-publish")) {
                extensions.configure<org.gradle.api.publish.PublishingExtension>("publishing") {
                    publications {
                        // Only create if it doesn't exist
                        if (findByName("maven") == null) {
                            create<org.gradle.api.publish.maven.MavenPublication>("maven") {
                                // Use project.group if set, otherwise default to com.geoknoesis.vericore
                                groupId = project.group.toString()
                                // Use explicit artifactId for plugins, otherwise use project.name
                                artifactId = when {
                                    project.path.startsWith(":did:plugins:") -> 
                                        project.path.substringAfter(":did:plugins:")
                                    project.path.startsWith(":kms:plugins:") -> 
                                        project.path.substringAfter(":kms:plugins:")
                                    project.path.startsWith(":chains:plugins:") -> 
                                        project.path.substringAfter(":chains:plugins:")
                                    else -> project.name
                                }
                                version = project.version.toString()
                                
                                from(components["java"])
                                
                                pom {
                                    name.set(project.name)
                                    description.set(project.description ?: "")
                                    
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
                    }
                    
                    repositories {
                        mavenLocal() // Publishes to ~/.m2/repository
                    }
                }
            }
        }
    }
}

// Register convenient test tasks
afterEvaluate {
    TestTasks.register(project)
}

tasks.wrapper {
    gradleVersion = "8.5"
}

