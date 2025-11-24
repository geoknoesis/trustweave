plugins {
    kotlin("jvm")
}

group = "com.trustweave"
dependencies {
    implementation(project(":common"))   // Root-level common (exceptions, common utilities)
    // KMS uses TrustWeaveException from root common module
    
    // Kotlinx coroutines for suspend functions in KeyManagementService interface
    implementation(libs.kotlinx.coroutines.core)
}

