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
    afterEvaluate {
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
