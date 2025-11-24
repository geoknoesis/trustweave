plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

// Try to match the actual project's configuration more closely
// The actual project has workarounds, but let's see what happens without them

dependencies {
    // Module A depends on nested Module B (like :credentials:core depending on :did:core)
    // This creates the transitive chain: module-a → nested:module-b → module-c
    // The nested path might trigger the circular dependency bug
    implementation(project(":nested:module-b"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

// Add debug output like the actual project
println("=== Property Debug Info ===")
println("kotlin.build.archivesTaskOutputAsFriendModule (project property): ${project.findProperty("kotlin.build.archivesTaskOutputAsFriendModule")}")
println("===========================")
