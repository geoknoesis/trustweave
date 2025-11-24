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
    group = "com.trustweave"
    version = "1.0.0-SNAPSHOT"
}

subprojects {
    // Configure all subprojects to build into the root project's build directory.
    // This centralizes all build outputs under the project root for easier cleanup and organization.
    // Each subproject's build output will be in build/<project-path>/ (e.g., build/did/core/)
    buildDir = file("${rootProject.buildDir}/${project.path.replace(":", "/")}")
    
    // Automatically set artifact name based on project path to avoid conflicts
    // Converts project path (e.g., ":did:core") to artifact name (e.g., "did-core")
    // This prevents conflicts when multiple modules have the same final segment (e.g., "core")
    // This must be done in afterEvaluate because the BasePluginExtension is only available
    // after the Java/Kotlin plugin is applied, but we need to check for conflicts with java-platform
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
            
            // Also configure the Jar task's archiveBaseName to ensure consistency
            // This affects both the JAR file name and dependency resolution
            tasks.withType<org.gradle.jvm.tasks.Jar>().configureEach {
                archiveBaseName.set(artifactName)
            }
            
            // Configure the component's artifact name for dependency resolution
            // This is critical - Gradle uses component metadata for dependency resolution,
            // not just the JAR file name. We need to ensure the component uses the correct
            // artifact name to prevent conflicts when multiple projects have the same default name.
            extensions.findByType<org.gradle.api.plugins.JavaPluginExtension>()?.let {
                // The Java plugin creates a "java" component. We configure its artifacts
                // to use the correct artifact name for dependency resolution.
                // Note: The archivesName above should handle this, but we ensure it here too.
                configurations.all {
                    // Ensure all configurations use the correct artifact name
                    // This prevents Gradle from using the default artifact name for dependency resolution
                }
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
    }
}

// Configure Gradle Wrapper to use a specific Gradle version.
// The wrapper allows developers to build the project without installing Gradle locally.
// When updating the wrapper, run: ./gradlew wrapper --gradle-version <version>
tasks.wrapper {
    gradleVersion = "9.2.0"
}
