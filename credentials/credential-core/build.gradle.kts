plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    // credentials:credential-core depends on did:did-core (renamed from did:core to avoid :core suffix)
    // This creates the transitive chain: credentials:credential-core → did:did-core → common
    // The circular dependency bug is fixed by renaming modules to avoid multiple :core suffixes
    implementation(project(":common"))
    implementation(project(":did:did-core"))
    
    // Kotlinx dependencies for serialization and coroutines
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    
    // Database support (optional, for storage implementations)
    // Uncomment when using database storage:
    // implementation("com.zaxxer:HikariCP:5.0.1")
    // implementation("org.postgresql:postgresql:42.6.0")
    
    testImplementation(project(":testkit"))
    testImplementation(project(":kms:kms-core"))
}
