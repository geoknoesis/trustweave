plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")

}

dependencies {

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

