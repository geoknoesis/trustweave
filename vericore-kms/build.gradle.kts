plugins {
    id("vericore.shared")
    kotlin("jvm")
}

group = "com.geoknoesis.vericore"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(project(":vericore-core"))
}

