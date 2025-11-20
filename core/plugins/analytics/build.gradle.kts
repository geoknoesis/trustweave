plugins {
    id("vericore.shared")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":core:vericore-core"))
    implementation(project(":core:plugins:metrics"))
    
    // JSON serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}

