plugins {
    id("vericore.shared")
    kotlin("jvm")
}

group = "com.geoknoesis.vericore"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(kotlin("reflect"))
    implementation(project(":core:vericore-core"))
    implementation(project(":chains:vericore-anchor"))
    implementation(project(":did:vericore-did"))
    implementation(project(":core:vericore-spi"))

    testImplementation(project(":core:vericore-testkit"))
    testImplementation(project(":did:vericore-did"))
    testImplementation(project(":kms:vericore-kms"))
}


