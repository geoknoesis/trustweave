plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.core"
dependencies {
    implementation(project(":common"))
    implementation(project(":credentials:credential-api"))
    implementation(project(":wallet:wallet-core"))  // Wallet interfaces
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)


    // JDBC drivers (PostgreSQL and H2 — MySQL is not supported, see DatabaseWallet KDoc)
    implementation("org.postgresql:postgresql:42.7.1")
    implementation("com.h2database:h2:2.2.224")

    // Connection pooling
    implementation("com.zaxxer:HikariCP:5.1.0")

    // Test dependencies
    testImplementation(project(":testkit"))
}

