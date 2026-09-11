plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

tasks.test {
    val dockerHost =
        System.getenv("DOCKER_HOST")?.takeIf { it.isNotBlank() }
            ?: if (System.getProperty("os.name").startsWith("Windows")) "npipe:////./pipe/dockerDesktopLinuxEngine" else null
    if (dockerHost != null) {
        environment("DOCKER_HOST", dockerHost)
        systemProperty("DOCKER_HOST", dockerHost)
    }
}

group = "org.trustweave.wallet"

dependencies {
    implementation(project(":common"))
    implementation(project(":credentials:credential-api"))
    implementation(project(":wallet:wallet-core")) // Wallet interfaces
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // JDBC drivers (PostgreSQL and H2 — MySQL is not supported, see DatabaseWallet KDoc)
    implementation(libs.postgresql)
    implementation(libs.h2)

    // Connection pooling
    implementation(libs.hikaricp)

    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation(libs.testcontainers.postgresql)
}
