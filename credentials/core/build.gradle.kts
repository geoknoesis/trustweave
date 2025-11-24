plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

// Debug: Print property values to verify they're being read
println("=== Property Debug Info ===")
println("kotlin.build.archivesTaskOutputAsFriendModule (project property): ${project.findProperty("kotlin.build.archivesTaskOutputAsFriendModule")}")
println("kotlin.build.archivesTaskOutputAsFriendModule (system property): ${System.getProperty("kotlin.build.archivesTaskOutputAsFriendModule")}")
println("kotlin.build.archivesTaskOutputAsFriendModule (gradle property): ${project.providers.gradleProperty("kotlin.build.archivesTaskOutputAsFriendModule").orNull}")
println("All gradle properties containing 'archivesTaskOutputAsFriendModule':")
project.properties.filterKeys { it.contains("archivesTaskOutputAsFriendModule", ignoreCase = true) }.forEach { (key, value) ->
    println("  $key = $value")
}
println("===========================")

// Early configuration: Intercept tasks as they're created
tasks.whenTaskAdded {
    if (name == "compileJava") {
        // Disable compileJava immediately when it's created (no Java sources)
        enabled = false
    }
    if (name == "compileKotlin" && this is org.jetbrains.kotlin.gradle.tasks.KotlinCompile) {
        // Try to remove jar dependency immediately when compileKotlin is created
        val compileKotlinTask = this as org.jetbrains.kotlin.gradle.tasks.KotlinCompile
        val jarTask = project.tasks.findByName("jar")
        if (jarTask != null) {
            // Remove jar dependency as soon as task is created
            val deps = compileKotlinTask.dependsOn.toMutableSet()
            deps.remove(jarTask)
            compileKotlinTask.setDependsOn(deps)
        }
    }
}

dependencies {
    // RESTORED: Original configuration
    implementation(project(":common"))
    implementation(project(":did:core"))
    
    testImplementation(project(":testkit"))
    testImplementation(project(":kms:core"))
}

// Fix circular dependency: compileKotlin -> jar -> classes -> compileJava -> compileKotlin
// Multiple workarounds attempted to break the cycle at different points
afterEvaluate {
    val compileKotlinTask = tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin")
    val jarTask = tasks.named<org.gradle.api.tasks.bundling.Jar>("jar")
    val classesTask = tasks.named("classes")
    val compileJavaTask = tasks.named<JavaCompile>("compileJava")
    
    // Workaround 1: Disable compileJava since there are no Java sources
    compileJavaTask.configure {
        enabled = false
    }
    
    // Workaround 2: Remove compileJava from classes dependencies
    classesTask.configure {
        val deps = dependsOn.toMutableSet()
        deps.remove(compileJavaTask.get())
        setDependsOn(deps)
    }
    
    // Workaround 3: Remove compileJava from compileKotlin dependencies (if present)
    compileKotlinTask.configure {
        val deps = dependsOn.toMutableSet()
        deps.remove(compileJavaTask.get())
        deps.remove(jarTask.get())
        setDependsOn(deps)
    }
    
    // Workaround 4: Remove jar from compileKotlin dependencies (most critical)
    compileKotlinTask.configure {
        val jarTaskRef = tasks.findByName("jar")
        if (jarTaskRef != null) {
            val deps = dependsOn.toMutableSet()
            deps.remove(jarTaskRef)
            setDependsOn(deps)
        }
    }
    
    // Workaround 5: Make jar depend on output files instead of classes/compileKotlin tasks
    jarTask.configure {
        val deps = dependsOn.toMutableSet()
        deps.remove(classesTask.get())
        deps.remove(compileKotlinTask.get())
        setDependsOn(deps)
        
        // Use file inputs from compileKotlin output
        from(compileKotlinTask.get().destinationDirectory)
        mustRunAfter(compileKotlinTask)
    }
    
    // Workaround 6: Configure all tasks to use ordering instead of dependencies
    tasks.all {
        if (this == jarTask.get()) {
            mustRunAfter(compileKotlinTask)
        }
        if (this == compileKotlinTask.get()) {
            val jarRef = tasks.findByName("jar")
            if (jarRef != null) {
                val deps = dependsOn.toMutableSet()
                deps.remove(jarRef)
                setDependsOn(deps)
            }
        }
    }
}

// Workaround 7: Remove compileJava from all task dependencies since there are no Java files
afterEvaluate {
    val compileJavaTask = tasks.findByName("compileJava")
    if (compileJavaTask != null) {
        // Disable compileJava
        compileJavaTask.enabled = false
        
        // Remove compileJava from all tasks that depend on it
        tasks.all {
            val deps = dependsOn.toMutableSet()
            if (deps.remove(compileJavaTask)) {
                setDependsOn(deps)
            }
        }
    }
}