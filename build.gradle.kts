plugins {
    // Declare Kotlin plugin here so its types (e.g., KotlinCompile) are available in this build script.
    // We use 'apply false' because we're configuring Kotlin tasks in subprojects, not applying the plugin to the root.
    // The plugin version is resolved from settings.gradle.kts where it's already declared.
    kotlin("jvm") apply false
}

// Configure common settings for all projects (root + all subprojects).
// This includes repository configuration and project metadata (group/version).
allprojects {
    repositories {
        mavenCentral()
    }
    group = "org.trustweave"
    version = "1.0.0-SNAPSHOT"
}

subprojects {

    // Configure all subprojects to build into the root project's build directory.
    // This centralizes all build outputs under the project root for easier cleanup and organization.
    // Each subproject's build output will be in build/<project-path>/ (e.g., build/did/core/)
    buildDir = file("${rootProject.buildDir}/${project.path.replace(":", "/")}")

    // Force consistent Kotlin stdlib version across all modules to avoid binary compatibility issues
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-stdlib:2.2.21")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.21")
        }
    }

    // Automatically set artifact name based on project path to avoid conflicts
    // Converts project path (e.g., ":did:did-core") to artifact name (e.g., "did-did-core")
    // This prevents conflicts when multiple modules have the same final segment
    // NOTE: Previously excluded credentials:core from this configuration due to circular dependency issue
    // The circular dependency was caused by Kotlin plugin's archivesTaskOutputAsFriendModule feature
    // This has been fixed by renaming modules to avoid multiple ":core" suffixes
    afterEvaluate {
        // Only set archivesName if the BasePluginExtension is available
        // (provided by Java plugin, which is applied by Kotlin JVM plugin)
        // Skip projects that use java-platform plugin (like distribution:bom)
        if (!plugins.hasPlugin("java-platform")) {
            val artifactName = project.path
                .removePrefix(":")  // Remove leading colon
                .replace(":", "-")   // Replace colons with hyphens

            // Set archivesName on BasePluginExtension (affects all archive tasks)
            extensions.findByType<org.gradle.api.plugins.BasePluginExtension>()?.let {
                it.archivesName.set(artifactName)
            }
        }
    }

    afterEvaluate {
        // Standardize test dependencies across all modules.
        // This ensures consistency and makes it easier to update test dependency versions.
        // Modules can still add additional test dependencies if needed.
        // Only apply if the Java plugin is applied (which provides the testImplementation configuration).
        extensions.findByType<org.gradle.api.plugins.JavaPluginExtension>()?.let {
            project.dependencies {
                add("testImplementation", libs.bundles.test)
                add("testRuntimeOnly", libs.junit.jupiter.engine)
                add("testRuntimeOnly", libs.junit.platform.launcher)
            }
        }
        // Configure Kotlin compiler options for all subprojects.
        // Without explicit configuration, Gradle defaults to whatever JVM version Gradle itself is running on,
        // which can vary across environments and cause inconsistent builds.
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
            compilerOptions {
                // Explicitly set JVM target to 21 to ensure all modules compile to the same bytecode version.
                // This prevents issues where different developers/build environments use different JVM targets.
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
                // Enable strict null-safety for Java interop. Without this, Kotlin's null-safety doesn't
                // properly respect @Nullable/@NonNull annotations from Java libraries.
                freeCompilerArgs.add("-Xjsr305=strict")
            }
        }

        // Configure Java toolchain to ensure all subprojects use Java 21.
        // Without this, Gradle may use whatever Java version is available on the system,
        // leading to inconsistent build results across different environments.
        extensions.findByType<org.gradle.api.plugins.JavaPluginExtension>()?.apply {
            toolchain {
                languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(21))
            }
        }

        // Configure all test tasks to use JUnit Platform (JUnit 5).
        // Without this, Gradle defaults to JUnit 4, which doesn't match our JUnit Jupiter dependencies.
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }

        // Apply maven-publish plugin and configure publishing for all subprojects that produce JAR files
        // Skip java-platform projects (like BOM) as they configure their own publishing
        if ((plugins.hasPlugin("org.jetbrains.kotlin.jvm") || plugins.hasPlugin("java")) 
            && !plugins.hasPlugin("java-platform")) {
            // Apply maven-publish plugin if not already applied
            if (!plugins.hasPlugin("maven-publish")) {
                apply(plugin = "maven-publish")
            }
            
            // Configure Maven publishing
            configure<org.gradle.api.publish.PublishingExtension> {
                publications {
                    create<org.gradle.api.publish.maven.MavenPublication>("maven") {
                        from(components["java"])
                        
                        // Set artifact ID based on project path (matches archivesName configuration)
                        val artifactName = project.path
                            .removePrefix(":")
                            .replace(":", "-")
                        artifactId = artifactName
                        
                        pom {
                            name.set(project.name)
                            description.set(project.description ?: "TrustWeave ${project.name} module")
                            url.set("https://github.com/geoknoesis/trustweave")
                            
                            licenses {
                                license {
                                    name.set("AGPL-3.0")
                                    url.set("https://www.gnu.org/licenses/agpl-3.0.txt")
                                }
                            }
                            
                            developers {
                                developer {
                                    id.set("trustweave-team")
                                    name.set("TrustWeave Team")
                                    email.set("info@geoknoesis.com")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Configure Gradle Wrapper to use a specific Gradle version.
// The wrapper allows developers to build the project without installing Gradle locally.
// When updating the wrapper, run: ./gradlew wrapper --gradle-version <version>
tasks.wrapper {
    gradleVersion = "9.2.0"
}
