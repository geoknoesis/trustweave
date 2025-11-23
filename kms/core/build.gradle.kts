plugins {
    kotlin("jvm")
}

group = "com.trustweave"
dependencies {
    implementation(project(":common"))   // Root-level common (exceptions, common utilities)
    // KMS uses TrustWeaveException from root common module
}

