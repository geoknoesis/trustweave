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
    
    testImplementation(project(":testkit"))
    testImplementation(project(":kms:kms-core"))
}
