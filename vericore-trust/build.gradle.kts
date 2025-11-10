plugins {
    id("vericore.shared")
    kotlin("jvm")
}

group = "io.geoknoesis.vericore"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(kotlin("reflect"))
    implementation(project(":vericore-core"))
    implementation(project(":vericore-anchor"))
    implementation(project(":vericore-did"))
    implementation(project(":vericore-spi"))

    testImplementation(project(":vericore-testkit"))
    testImplementation(project(":vericore-did"))
    testImplementation(project(":vericore-kms"))
}


