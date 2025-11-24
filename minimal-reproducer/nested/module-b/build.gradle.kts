plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    // Module B depends on Module C (creates transitive dependency chain)
    // This is like :did:core depending on :common
    implementation(project(":module-c"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

