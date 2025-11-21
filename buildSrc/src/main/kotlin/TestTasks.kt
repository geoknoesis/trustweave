import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

/**
 * Convenient Gradle test tasks for running plugin tests.
 */
object TestTasks {
    
    /**
     * Registers convenient test tasks for a project.
     */
    fun register(project: Project) {
        // Only register for root project
        if (project != project.rootProject) {
            return
        }
        
        // Configure dependencies during configuration phase, not execution
        project.afterEvaluate {
            // Task to run all DID plugin tests
            val testDidPlugins = project.tasks.register("testDidPlugins") {
                group = "verification"
                description = "Run all DID method plugin tests"
            }
            
            // Task to run all KMS plugin tests
            val testKmsPlugins = project.tasks.register("testKmsPlugins") {
                group = "verification"
                description = "Run all KMS plugin tests"
            }
            
            // Task to run all chain plugin tests
            val testChainPlugins = project.tasks.register("testChainPlugins") {
                group = "verification"
                description = "Run all chain plugin tests"
            }
            
            // Task to run all plugin tests
            project.tasks.register("testAllPlugins") {
                group = "verification"
                description = "Run all plugin tests"
                dependsOn(testDidPlugins, testKmsPlugins, testChainPlugins)
            }
            
            // Task to generate coverage report for all modules
            val testCoverageReport = project.tasks.register("testCoverageReport") {
                group = "verification"
                description = "Generate test coverage report for all modules"
            }
            
            // Task to verify coverage thresholds
            val testCoverageVerify = project.tasks.register("testCoverageVerify") {
                group = "verification"
                description = "Verify test coverage meets thresholds"
            }
            
            // Set up dependencies for plugin test tasks
            project.subprojects.forEach { subproject ->
                when {
                    subproject.path.startsWith(":did:plugins:") -> {
                        subproject.tasks.withType<Test>().forEach { testTask ->
                            testDidPlugins.configure {
                                dependsOn(testTask)
                            }
                        }
                    }
                    subproject.path.startsWith(":kms:plugins:") -> {
                        subproject.tasks.withType<Test>().forEach { testTask ->
                            testKmsPlugins.configure {
                                dependsOn(testTask)
                            }
                        }
                    }
                    subproject.path.startsWith(":chains:plugins:") -> {
                        subproject.tasks.withType<Test>().forEach { testTask ->
                            testChainPlugins.configure {
                                dependsOn(testTask)
                            }
                        }
                    }
                }
                
                // Set up coverage tasks
                if (subproject.pluginManager.hasPlugin("org.jetbrains.kotlinx.kover")) {
                    subproject.tasks.findByName("koverReport")?.let { koverReportTask ->
                        testCoverageReport.configure {
                            dependsOn(koverReportTask)
                        }
                    }
                    subproject.tasks.findByName("koverVerify")?.let { koverVerifyTask ->
                        testCoverageVerify.configure {
                            dependsOn(koverVerifyTask)
                        }
                    }
                }
            }
        }
    }
}

