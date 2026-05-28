plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.credentials"

// org.didcommx:didcomm is a fat JAR that embeds com.nimbusds.* (built against ~9.16-preview).
// A separate nimbus-jose-jwt on the classpath causes split packages / NoSuchMethodError at runtime.
configurations.configureEach {
    exclude(group = "com.nimbusds", module = "nimbus-jose-jwt")
}

dependencies {
    // Credential API (includes exchange API)
    implementation(project(":credentials:credential-api"))
    
    implementation(project(":did:did-core"))
    implementation(project(":kms:kms-core"))
    implementation(project(":common"))

    // Kotlinx dependencies
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    // Logging
    implementation(libs.slf4j.api)

    // Cryptography
    implementation(libs.bouncycastle.prov)
    implementation(libs.bouncycastle.pkix)

    // DIDComm library (production crypto implementation)
    implementation("org.didcommx:didcomm:0.3.2")

    // HTTP client for message delivery
    implementation(libs.okhttp)

    // Database support (optional, for persistent storage)
    // Uncomment when using database storage:
    // implementation("com.zaxxer:HikariCP:5.0.1")
    // implementation("org.postgresql:postgresql:42.6.0")

    // MongoDB support (optional, for NoSQL storage)
    // Uncomment when using MongoDB storage:
    // implementation("org.mongodb:mongodb-driver-kotlin-coroutine:4.11.0")
    // For compilation only (Document class reference):
    compileOnly("org.mongodb:bson:4.11.0")

    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation(project(":kms:kms-core"))
}

