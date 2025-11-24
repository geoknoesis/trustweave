plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    // did:did-core depends on common (creates transitive dependency chain)
    implementation(project(":common"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
