plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.0")
    // Gradle API is automatically available in buildSrc
}

gradlePlugin {
    plugins {
        create("vericore.shared") {
            id = "vericore.shared"
            implementationClass = "VericoreSharedPlugin"
        }
    }
}
