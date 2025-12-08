plugins {
    kotlin("jvm")
}

group = "com.trustweave.kms"

dependencies {
    // API dependencies - exposed transitively to consumers
    api(project(":common"))
    api(project(":kms:kms-core"))
    
    // Implementation dependencies - internal only
    implementation(libs.kotlinx.coroutines.core)
    implementation("org.slf4j:slf4j-api:2.0.9")

    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation(testFixtures(project(":kms:kms-core"))) // Access test classes via testFixtures
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

