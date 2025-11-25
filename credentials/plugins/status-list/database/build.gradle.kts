plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.credentials"
dependencies {
    implementation(project(":credentials:credential-core"))
    // StatusListManagerFactory is in this module

    // Kotlin Coroutines
    implementation(libs.kotlinx.coroutines.core)
    
    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)
    
    // JDBC drivers
    implementation("org.postgresql:postgresql:42.7.1")
    implementation("com.mysql:mysql-connector-j:8.2.0")
    implementation("com.h2database:h2:2.2.224")
    
    // Connection pooling
    implementation("com.zaxxer:HikariCP:5.1.0")

    // Test dependencies
    testImplementation(project(":testkit"))
}

