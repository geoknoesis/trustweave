pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        kotlin("jvm") version "2.2.21"
        kotlin("plugin.serialization") version "2.2.21"
    }
}

rootProject.name = "minimal-reproducer"

include("credentials:credential-core")  // Renamed from credentials:core
include("did:did-core")  // Renamed from did:core
include("common")
