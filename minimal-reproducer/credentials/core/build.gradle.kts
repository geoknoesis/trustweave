plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

// Try to match the actual project's configuration more closely
// The actual project has workarounds, but let's see what happens without them

dependencies {
    // credentials:core depends on did:core
    // Testing if having TWO modules with ":core" suffix triggers the bug
    implementation(project(":common"))
    implementation(project(":did:did-core"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

// Add debug output like the actual project
println("=== Property Debug Info ===")
println("kotlin.build.archivesTaskOutputAsFriendModule (project property): ${project.findProperty("kotlin.build.archivesTaskOutputAsFriendModule")}")
println("===========================")

