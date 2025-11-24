plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    // did:main depends on common (creates transitive dependency chain)
    // Changed from did:core to did:main to test if "core" suffix matters
    implementation(project(":common"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
