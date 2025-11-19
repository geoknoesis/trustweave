pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        kotlin("jvm") version "2.2.0"
        kotlin("plugin.serialization") version "2.2.0"
    }
}

rootProject.name = "vericore"

include(
    "vericore-core",
    "vericore-anchor",
    "vericore-did",
    "vericore-did-base",
    "vericore-did-web",
    "vericore-did-ethr",
    "vericore-did-ion",
    "vericore-did-polygon",
    "vericore-did-sol",
    "vericore-did-peer",
    "vericore-did-ens",
    "vericore-did-plc",
    "vericore-did-cheqd",
    "vericore-kms",
    "vericore-json",
    "vericore-testkit",
    "vericore-waltid",
    "vericore-aws-kms",
    "vericore-azure-kms",
    "vericore-google-kms",
    "vericore-hashicorp-kms",
    "vericore-algorand",
    "vericore-polygon",
    "vericore-godiddy",
    "vericore-ganache",
    "vericore-indy",
    "vericore-examples",
    "vericore-bom",
    "vericore-all",
    "vericore-spi",
    "vericore-trust"
)

