// Configure plugin management - this defines repositories and plugin versions
// that can be used across all build scripts without specifying versions again.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    
    // Declare plugin versions here so subprojects can use them without specifying versions.
    // For example, subprojects can use kotlin("jvm") and Gradle will automatically use version 2.2.21.
    plugins {
        kotlin("jvm") version "2.2.21"
        kotlin("plugin.serialization") version "2.2.21"
    }
}

// Set the root project name (used for artifact names and project identification).
rootProject.name = "trustweave"

// Core modules
include("common")
include("trust")
include("testkit")
include("contract")

// DID domain
include("did:core")
include("did:registrar")
include("did:registrar-server")
include("did:plugins:base")
include("did:plugins:key")
include("did:plugins:web")
include("did:plugins:ethr")
include("did:plugins:ion")
include("did:plugins:polygon")
include("did:plugins:sol")
include("did:plugins:peer")
include("did:plugins:ens")
include("did:plugins:plc")
include("did:plugins:cheqd")
include("did:plugins:jwk")
include("did:plugins:godiddy")
include("did:plugins:threebox")
include("did:plugins:btcr")
include("did:plugins:tezos")
include("did:plugins:orb")

// Wallet domain
include("wallet:core")
include("wallet:plugins:database")
include("wallet:plugins:file")
include("wallet:plugins:cloud")

// KMS domain
include("kms:core")
include("kms:plugins:aws")
include("kms:plugins:azure")
include("kms:plugins:google")
include("kms:plugins:hashicorp")
include("kms:plugins:ibm")
include("kms:plugins:thales")
include("kms:plugins:cyberark")
include("kms:plugins:fortanix")
include("kms:plugins:thales-luna")
include("kms:plugins:utimaco")
include("kms:plugins:cloudhsm")
include("kms:plugins:entrust")
include("kms:plugins:waltid")
include("kms:plugins:venafi")

// Anchors domain
include("anchors:core")
include("anchors:plugins:algorand")
include("anchors:plugins:polygon")
include("anchors:plugins:ethereum")
include("anchors:plugins:base")
include("anchors:plugins:arbitrum")
include("anchors:plugins:optimism")
include("anchors:plugins:zksync")
include("anchors:plugins:bitcoin")
include("anchors:plugins:starknet")
include("anchors:plugins:cardano")
include("anchors:plugins:ganache")
include("anchors:plugins:indy")

// Credentials domain
include("credentials:core")
include("credentials:plugins:proof:bbs")
include("credentials:plugins:proof:jwt")
include("credentials:plugins:proof:ld")
include("credentials:plugins:status-list:database")
include("credentials:plugins:platforms:servicenow")
include("credentials:plugins:platforms:salesforce")
include("credentials:plugins:platforms:entra")
include("credentials:plugins:audit-logging")
include("credentials:plugins:metrics")
include("credentials:plugins:qr-code")
include("credentials:plugins:notifications")
include("credentials:plugins:versioning")
include("credentials:plugins:backup")
include("credentials:plugins:expiration")
include("credentials:plugins:analytics")
include("credentials:plugins:oidc4vci")
include("credentials:plugins:multi-party")
include("credentials:plugins:health-checks")
include("credentials:plugins:rendering")
include("credentials:plugins:didcomm")
include("credentials:plugins:chapi")

// Distribution
include("distribution:all")
include("distribution:bom")
include("distribution:examples")
