plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.credentials"
dependencies {
    implementation(project(":credentials:core"))
    
    // JWT library
    implementation("com.nimbusds:nimbus-jose-jwt:9.37.3")

    // Test dependencies
    testImplementation(project(":testkit"))
}

