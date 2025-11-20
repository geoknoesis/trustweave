plugins {
    id("vericore.shared")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":core:vericore-core"))
    
    // QR Code generation - using a lightweight library
    // Note: We'll use a simple approach or add a QR code library if needed
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

