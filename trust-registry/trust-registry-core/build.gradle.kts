plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.registry"

dependencies {
    api(project(":common"))
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.datetime)

    testImplementation(project(":testkit"))
    testImplementation(libs.kotlinx.coroutines.test)
}
