plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.geoknoesis.vericore.core"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(project(":core:vericore-core"))
    implementation(project(":core:vericore-spi"))
    
    // JDBC drivers
    implementation("org.postgresql:postgresql:42.7.1")
    implementation("com.mysql:mysql-connector-j:8.2.0")
    implementation("com.h2database:h2:2.2.224")
    
    // Connection pooling
    implementation("com.zaxxer:HikariCP:5.1.0")

    // Test dependencies
    testImplementation(project(":core:vericore-testkit"))
}

