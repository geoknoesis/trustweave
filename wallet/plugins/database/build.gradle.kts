plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.core"
dependencies {
    implementation(project(":credentials:credential-core"))
    implementation(project(":wallet:wallet-core"))  // Wallet interfaces

    
    // JDBC drivers
    implementation("org.postgresql:postgresql:42.7.1")
    implementation("com.mysql:mysql-connector-j:8.2.0")
    implementation("com.h2database:h2:2.2.224")
    
    // Connection pooling
    implementation("com.zaxxer:HikariCP:5.1.0")

    // Test dependencies
    testImplementation(project(":testkit"))
}

