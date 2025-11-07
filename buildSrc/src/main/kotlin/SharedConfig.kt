import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.bundling.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

fun Project.configureKotlin() {
    // Apply Java plugin if not already applied
    if (!pluginManager.hasPlugin("java")) {
        apply(plugin = "java")
    }
    
    // Apply Kotlin JVM plugin if not already applied
    if (!pluginManager.hasPlugin("org.jetbrains.kotlin.jvm")) {
        apply(plugin = "org.jetbrains.kotlin.jvm")
    }
    
    // Configure Java toolchain to use Java 21
    extensions.configure<org.gradle.api.plugins.JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    // Configure Kotlin compilation
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }

    // Configure Java compilation
    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = "21"
        targetCompatibility = "21"
        options.release.set(21)
    }

    // Configure test task to use JUnit 5
    // Note: Java toolchain is configured above, so tests will use Java 21
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
    
    // Workaround for Windows file locking issue with JAR files
    // Configure jar tasks to skip when outputs are up-to-date
    afterEvaluate {
        tasks.withType<Jar>().configureEach {
            // Configure task to be up-to-date if jar file exists and inputs haven't changed
            outputs.upToDateWhen {
                val jarFile = archiveFile.get().asFile
                if (!jarFile.exists()) {
                    return@upToDateWhen false
                }
                
                // Check if inputs have changed
                val inputFiles = inputs.files.files
                if (inputFiles.isEmpty()) {
                    return@upToDateWhen true
                }
                
                val lastInputModified = inputFiles.maxOfOrNull { it.lastModified() } ?: 0L
                val jarModified = jarFile.lastModified()
                
                // If jar is newer than all inputs, consider it up-to-date
                jarModified >= lastInputModified
            }
            
            // Try to handle locked files before Gradle's internal deletion
            doFirst {
                val jarFile = archiveFile.get().asFile
                if (jarFile.exists()) {
                    // Try to delete, but if locked, rename to backup
                    try {
                        jarFile.delete()
                    } catch (e: Exception) {
                        // File is locked, try to rename it
                        try {
                            val backupFile = File(jarFile.parentFile, "${jarFile.name}.bak")
                            if (backupFile.exists()) backupFile.delete()
                            jarFile.renameTo(backupFile)
                        } catch (e2: Exception) {
                            // If rename fails and file is up-to-date, task will be skipped
                        }
                    }
                }
            }
        }
    }

    dependencies {
        "implementation"(Libs.kotlinxSerializationJson)
        "implementation"(Libs.kotlinxCoroutinesCore)
        "testImplementation"(Libs.junitJupiter)
        "testImplementation"(Libs.kotlinTest)
        "testRuntimeOnly"(Libs.junitJupiterEngine)
    }
}

