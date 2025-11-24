plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    // did:core depends on common (creates transitive dependency chain)
    // This is like the actual project
    implementation(project(":common"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

