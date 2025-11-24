plugins {
    kotlin("jvm") apply false
}

allprojects {
    repositories {
        mavenCentral()
    }
    group = "com.example"
    version = "1.0.0-SNAPSHOT"
}

subprojects {
    // TEST: Disabled artifact naming to see if it's related to the bug
    // Automatically set artifact name based on project path to avoid conflicts
    // This matches the actual project's configuration
    // afterEvaluate {
    //     if (!plugins.hasPlugin("java-platform")) {
    //         val artifactName = project.path
    //             .removePrefix(":")
    //             .replace(":", "-")
    //         
    //         extensions.findByType<org.gradle.api.plugins.BasePluginExtension>()?.let {
    //             it.archivesName.set(artifactName)
    //         }
    //     }
    // }
    
    // Configure Kotlin compiler options
    afterEvaluate {
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
            compilerOptions {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
                freeCompilerArgs.add("-Xjsr305=strict")
            }
        }
        
        extensions.findByType<org.gradle.api.plugins.JavaPluginExtension>()?.apply {
            toolchain {
                languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(21))
            }
        }
    }
}
