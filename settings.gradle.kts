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

// Core modules
include("core:vericore-core")
include("core:vericore-spi")
include("core:vericore-json")
include("core:vericore-trust")
include("core:vericore-testkit")

// DID domain
include("did:vericore-did")
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

// KMS domain
include("kms:vericore-kms")
include("kms:plugins:aws")
include("kms:plugins:azure")
include("kms:plugins:google")
include("kms:plugins:hashicorp")
include("kms:plugins:waltid")

// Chains domain
include("chains:vericore-anchor")
include("chains:plugins:algorand")
include("chains:plugins:polygon")
include("chains:plugins:ethereum")
include("chains:plugins:base")
include("chains:plugins:arbitrum")
include("chains:plugins:ganache")
include("chains:plugins:indy")

// Distribution modules
include("distribution:vericore-all")
include("distribution:vericore-bom")
include("distribution:vericore-examples")

// Project directory mappings - Core
project(":core:vericore-core").projectDir = file("core/vericore-core")
project(":core:vericore-spi").projectDir = file("core/vericore-spi")
project(":core:vericore-json").projectDir = file("core/vericore-json")
project(":core:vericore-trust").projectDir = file("core/vericore-trust")
project(":core:vericore-testkit").projectDir = file("core/vericore-testkit")

// Project directory mappings - DID domain
project(":did:vericore-did").projectDir = file("did/vericore-did")
project(":did:plugins:base").projectDir = file("did/plugins/base")
project(":did:plugins:key").projectDir = file("did/plugins/key")
project(":did:plugins:web").projectDir = file("did/plugins/web")
project(":did:plugins:ethr").projectDir = file("did/plugins/ethr")
project(":did:plugins:ion").projectDir = file("did/plugins/ion")
project(":did:plugins:polygon").projectDir = file("did/plugins/polygon")
project(":did:plugins:sol").projectDir = file("did/plugins/sol")
project(":did:plugins:peer").projectDir = file("did/plugins/peer")
project(":did:plugins:ens").projectDir = file("did/plugins/ens")
project(":did:plugins:plc").projectDir = file("did/plugins/plc")
project(":did:plugins:cheqd").projectDir = file("did/plugins/cheqd")
project(":did:plugins:jwk").projectDir = file("did/plugins/jwk")
project(":did:plugins:godiddy").projectDir = file("did/plugins/godiddy")

// Project directory mappings - KMS domain
project(":kms:vericore-kms").projectDir = file("kms/vericore-kms")
project(":kms:plugins:aws").projectDir = file("kms/plugins/aws")
project(":kms:plugins:azure").projectDir = file("kms/plugins/azure")
project(":kms:plugins:google").projectDir = file("kms/plugins/google")
project(":kms:plugins:hashicorp").projectDir = file("kms/plugins/hashicorp")
project(":kms:plugins:waltid").projectDir = file("kms/plugins/waltid")

// Project directory mappings - Chains domain
project(":chains:vericore-anchor").projectDir = file("chains/vericore-anchor")
project(":chains:plugins:algorand").projectDir = file("chains/plugins/algorand")
project(":chains:plugins:polygon").projectDir = file("chains/plugins/polygon")
project(":chains:plugins:ethereum").projectDir = file("chains/plugins/ethereum")
project(":chains:plugins:base").projectDir = file("chains/plugins/base")
project(":chains:plugins:arbitrum").projectDir = file("chains/plugins/arbitrum")
project(":chains:plugins:ganache").projectDir = file("chains/plugins/ganache")
project(":chains:plugins:indy").projectDir = file("chains/plugins/indy")

// Project directory mappings - Distribution
project(":distribution:vericore-all").projectDir = file("distribution/vericore-all")
project(":distribution:vericore-bom").projectDir = file("distribution/vericore-bom")
project(":distribution:vericore-examples").projectDir = file("distribution/vericore-examples")
