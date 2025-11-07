// Root build file - minimal configuration
// Shared configuration is in buildSrc

allprojects {
    repositories {
        mavenCentral()
        maven("https://maven.waltid.dev/releases")
        maven("https://maven.waltid.dev/snapshots")
    }
}

tasks.wrapper {
    gradleVersion = "8.5"
}

