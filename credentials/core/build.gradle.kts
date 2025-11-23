plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":did:core"))
    
    testImplementation(project(":testkit"))
    testImplementation(project(":kms:core"))
}
