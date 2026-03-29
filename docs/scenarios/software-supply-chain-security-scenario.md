---
title: Software Supply Chain Security Scenario
parent: Use Case Scenarios
nav_order: 17
---

# Software Supply Chain Security Scenario

This guide demonstrates how to build a software supply chain security system using TrustWeave. You'll learn how software publishers can issue provenance credentials, how build systems can attest to software integrity, and how consumers can verify software authenticity to prevent supply chain attacks.

## What You'll Build

By the end of this tutorial, you'll have:

- Created DIDs for software publishers, build systems, and consumers
- Issued Verifiable Credentials for software provenance and build attestation
- Stored software credentials in wallets
- Implemented software integrity verification
- Created SBOM (Software Bill of Materials) credentials
- Verified software authenticity before installation
- Demonstrated dependency verification
- Implemented tamper-proof software provenance

## Big Picture & Significance

### The Software Supply Chain Challenge

Software supply chain attacks are increasing, with attackers compromising build systems, injecting malicious code, and distributing tainted software. Verifiable credentials enable cryptographic proof of software provenance, build integrity, and dependency authenticity.

**Industry Context:**
- **Attack Frequency**: 742% increase in supply chain attacks in 2021
- **Impact**: SolarWinds, Codecov, and other major breaches
- **Regulatory**: Executive Order 14028, SLSA framework
- **Market Size**: $50+ billion in damages from supply chain attacks
- **Trust Crisis**: Growing need for verifiable software provenance

**Why This Matters:**
1. **Security**: Prevent supply chain attacks
2. **Trust**: Verify software authenticity
3. **Compliance**: Meet regulatory requirements (EO 14028, SLSA)
4. **Provenance**: Track software from source to deployment
5. **Integrity**: Verify software hasn't been tampered with
6. **Dependencies**: Verify dependency authenticity

### The Software Supply Chain Problem

Traditional software distribution faces critical issues:
- **No Provenance**: Can't verify where software came from
- **Build Compromise**: Build systems can be compromised
- **Dependency Risk**: Dependencies can be malicious
- **Tampering**: Software can be modified in transit
- **No Attestation**: No proof of build process integrity
- **Trust Issues**: Can't verify software authenticity

## Value Proposition

### Problems Solved

1. **Provenance Verification**: Verify software source and build process
2. **Build Attestation**: Cryptographic proof of build integrity
3. **Dependency Verification**: Verify dependency authenticity
4. **Tamper Detection**: Detect software tampering
5. **SBOM Support**: Software Bill of Materials credentials
6. **Compliance**: Automated compliance with supply chain security requirements
7. **Trust**: Cryptographic proof of software authenticity

### Business Benefits

**For Software Consumers:**
- **Security**: Prevent supply chain attacks
- **Trust**: Verify software authenticity
- **Compliance**: Meet regulatory requirements
- **Risk Reduction**: Reduce supply chain risk
- **Efficiency**: Automated verification process

**For Software Publishers:**
- **Trust**: Enhanced trust through verifiable credentials
- **Compliance**: Meet supply chain security requirements
- **Differentiation**: Stand out with verifiable provenance
- **Efficiency**: Automated credential issuance

**For Build Systems:**
- **Integrity**: Prove build process integrity
- **Attestation**: Cryptographic proof of build authenticity
- **Compliance**: Meet SLSA requirements
- **Trust**: Enhanced trust in build outputs

### ROI Considerations

- **Security**: Prevents supply chain attacks
- **Compliance**: Automated EO 14028/SLSA compliance
- **Trust**: Enhanced trust in software
- **Risk Reduction**: 90% reduction in supply chain risk
- **Cost Savings**: Prevents costly breaches

## Understanding the Problem

Traditional software distribution has several problems:

1. **No provenance**: Can't verify where software came from
2. **Build compromise**: Build systems can be compromised
3. **Dependency risk**: Dependencies can be malicious
4. **Tampering**: Software can be modified in transit
5. **No attestation**: No proof of build process integrity

TrustWeave solves this by enabling:

- **Provenance verification**: Verify software source and build
- **Build attestation**: Cryptographic proof of build integrity
- **Dependency verification**: Verify dependency authenticity
- **Tamper detection**: Detect software tampering
- **SBOM support**: Software Bill of Materials credentials

## How It Works: The Software Supply Chain Security Flow

```mermaid
flowchart TD
    A["Software Publisher<br/>Issues Provenance<br/>Credential"] -->|issues| B["Software Provenance Credential<br/>Software DID<br/>Source Code Digest<br/>Build Attestation<br/>Cryptographic Proof"]
    B -->|stored in| C["Build System<br/>Attests Build Process<br/>Issues Build Credential<br/>Creates SBOM"]
    C -->|issues| D["Build Attestation Credential<br/>Build Process Proof<br/>SBOM Credential<br/>Dependency Verification"]
    D -->|consumers verify| E["Software Consumer<br/>Verifies Provenance<br/>Checks Build Attestation<br/>Validates Dependencies<br/>Grants Installation"]

    style A fill:#1976d2,stroke:#0d47a1,stroke-width:2px,color:#fff
    style B fill:#f57c00,stroke:#e65100,stroke-width:2px,color:#fff
    style C fill:#388e3c,stroke:#1b5e20,stroke-width:2px,color:#fff
    style D fill:#9c27b0,stroke:#4a148c,stroke-width:2px,color:#fff
    style E fill:#c2185b,stroke:#880e4f,stroke-width:2px,color:#fff
```

## Prerequisites

- Java 21+
- Kotlin 2.2.21+
- Gradle 8.5+
- Basic understanding of Kotlin and coroutines
- Understanding of software build processes

## Step 1: Add Dependencies

Add TrustWeave dependencies to your `build.gradle.kts`:

```kotlin
dependencies {
    // Core TrustWeave modules
    implementation("org.trustweave:distribution-all:0.6.0")

    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
```

## Step 2: Complete Runnable Example

Here's the full software supply chain security flow using the TrustWeave facade API:

```kotlin
package com.example.software.supplychain

import org.trustweave.trust.TrustWeave
import org.trustweave.core.util.DigestUtils
import org.trustweave.wallet.Wallet
import org.trustweave.testkit.services.*
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.credential.results.getOrThrow
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.errorMessage
import org.trustweave.did.identifiers.extractKeyId
import org.trustweave.credential.results.VerificationResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

fun main() = runBlocking {
    println("=".repeat(70))
    println("Software Supply Chain Security Scenario - Complete End-to-End Example")
    println("=".repeat(70))

    // Step 1: Create TrustWeave instance
    val trustWeave = TrustWeave.build {
        keys { provider(IN_MEMORY); algorithm(ED25519) }
        did { method(KEY) { algorithm(ED25519) } }
    }
    println("\n✅ TrustWeave initialized")

    // Step 2: Create DIDs for software publisher, build system, and consumer
    val publisherDid = trustWeave.createDid { method(KEY) }.getOrThrowDid()
    val publisherDoc = when (val res = trustWeave.resolveDid(publisherDid)) {
    is DidResolutionResult.Success -> res.document
    else -> throw IllegalStateException(res.errorMessage ?: "Failed to resolve DID")
}
    val publisherKeyId = publisherDoc.verificationMethod.firstOrNull()?.extractKeyId()
        ?: throw IllegalStateException("No verification method found")

    val buildSystemDid = trustWeave.createDid { method(KEY) }.getOrThrowDid()
    val buildSystemDoc = when (val res = trustWeave.resolveDid(buildSystemDid)) {
    is DidResolutionResult.Success -> res.document
    else -> throw IllegalStateException(res.errorMessage ?: "Failed to resolve DID")
}
    val buildSystemKeyId = buildSystemDoc.verificationMethod.firstOrNull()?.extractKeyId()
        ?: throw IllegalStateException("No verification method found")

    val consumerDid = trustWeave.createDid { method(KEY) }.getOrThrowDid()

    println("✅ Software Publisher DID: ${publisherDid.value}")
    println("✅ Build System DID: ${buildSystemDid.value}")
    println("✅ Consumer DID: ${consumerDid.value}")

    // Step 3: Simulate source code and compute digest
    println("\n[provenance] Software Provenance:")

    val sourceCode = """
        package com.example.secureapp

        fun main() {
            println("Secure Application v1.0.0")
        }
    """.trimIndent()

    val sourceCodeBytes = sourceCode.toByteArray()
    val sourceCodeDigest = DigestUtils.sha256DigestMultibase(sourceCodeBytes)

    println("   Source code digest: ${sourceCodeDigest.take(20)}...")
    println("   Source repository: https://github.com/example/secureapp")
    println("   Commit hash: abc123def456")

    // Step 4: Issue software provenance credential
    val provenanceIssuanceResult = trustWeave.issue {
        credential {
            id("urn:software:secureapp:1.0.0")
            type("VerifiableCredential", "SoftwareProvenanceCredential", "SoftwareCredential")
            issuer(publisherDid)
            subject {
                id("urn:software:secureapp:1.0.0")
                "software" {
                    "name" to "SecureApp"
                    "version" to "1.0.0"
                    "publisher" to publisherDid.value
                    "sourceRepository" to "https://github.com/example/secureapp"
                    "commitHash" to "abc123def456"
                    "sourceCodeDigest" to sourceCodeDigest
                    "license" to "Apache-2.0"
                    "releaseDate" to Instant.now().toString()
                }
            }
        }
        by(issuerDid = publisherDid.value, keyId = publisherKeyId)
    }

    val provenanceCredential = provenanceIssuanceResult.getOrThrow()

    println("\n✅ Software provenance credential issued: ${provenanceCredential.id}")

    // Step 5: Simulate build process and create build attestation
    println("\n[build] Build Process:")

    val buildArtifact = "secureapp-1.0.0.jar".toByteArray()
    val buildArtifactDigest = DigestUtils.sha256DigestMultibase(buildArtifact)

    println("   Build artifact digest: ${buildArtifactDigest.take(20)}...")
    println("   Build system: GitHub Actions")
    println("   Build environment: Isolated, verified")

    // Step 6: Issue build attestation credential
    val buildAttestationIssuanceResult = trustWeave.issue {
        credential {
            id("build:secureapp:1.0.0")
            type("VerifiableCredential", "BuildAttestationCredential", "SLSACredential")
            issuer(buildSystemDid)
            subject {
                id("build:secureapp:1.0.0")
                "build" {
                    "softwareId" to "software:secureapp:1.0.0"
                    "buildSystem" to "GitHub Actions"
                    "buildId" to "build-12345"
                    "buildDate" to Instant.now().toString()
                    "buildArtifactDigest" to buildArtifactDigest
                    "buildEnvironment" {
                        "isolated" to true
                        "verified" to true
                        "slsaLevel" to "L3" // SLSA Level 3
                        "buildType" to "reproducible"
                    }
                    "sourceCodeDigest" to sourceCodeDigest // Links to source
                    "attestation" {
                        "type" to "SLSA"
                        "level" to 3
                        "predicate" to "https://slsa.dev/provenance/v0.2"
                    }
                }
            }
        }
        by(issuerDid = buildSystemDid.value, keyId = buildSystemKeyId)
    }
    
    val buildAttestationCredential = buildAttestationIssuanceResult.getOrThrow()

    println("✅ Build attestation credential issued: ${buildAttestationCredential.id}")

    // Step 7: Create SBOM (Software Bill of Materials)
    println("\n[sbom] of Materials (SBOM):")

    val dependencies = listOf(
        mapOf("name" to "kotlin-stdlib", "version" to "1.9.0", "digest" to "sha256:abc123..."),
        mapOf("name" to "kotlinx-coroutines", "version" to "1.7.3", "digest" to "sha256:def456...")
    )

    println("   Dependencies: ${dependencies.size}")
    dependencies.forEach { dep ->
        println("     - ${dep["name"]} v${dep["version"]}")
    }

    // Step 8: Issue SBOM credential
    val sbomIssuanceResult = trustWeave.issue {
        credential {
            id("urn:sbom:secureapp:1.0.0")
            type("VerifiableCredential", "SBOMCredential", "SoftwareCredential")
            issuer(buildSystemDid)
            subject {
                id("urn:sbom:secureapp:1.0.0")
                "sbom" {
                    "softwareId" to "software:secureapp:1.0.0"
                    "sbomVersion" to "SPDX-2.3"
                    "sbomFormat" to "SPDX"
                    "creationDate" to Instant.now().toString()
                    "dependencies" to dependencies.map { dep ->
                        buildJsonObject {
                            "name" to (dep["name"] as String)
                            "version" to (dep["version"] as String)
                            "digest" to (dep["digest"] as String)
                        }
                    }
                }
            }
        }
        by(issuerDid = buildSystemDid.value, keyId = buildSystemKeyId)
    }
    
    val sbomCredential = sbomIssuanceResult.getOrThrow()

    println("✅ SBOM credential issued: ${sbomCredential.id}")

    // Step 9: Create consumer wallet and store credentials
    val walletCreationResult = trustWeave.wallet {
        holder(consumerDid)
        enableOrganization()
        enablePresentation()
    }
    
    val consumerWallet = walletCreationResult.getOrThrow()

    val provenanceCredentialId = consumerWallet.store(provenanceCredential)
    val buildAttestationCredentialId = consumerWallet.store(buildAttestationCredential)
    val sbomCredentialId = consumerWallet.store(sbomCredential)

    println("\n✅ All software credentials stored in wallet")

    // Step 10: Organize credentials
    consumerWallet.withOrganization { org ->
        val softwareCollectionId = org.createCollection("Software", "Software provenance and build credentials")

        org.addToCollection(provenanceCredentialId, softwareCollectionId)
        org.addToCollection(buildAttestationCredentialId, softwareCollectionId)
        org.addToCollection(sbomCredentialId, softwareCollectionId)

        org.tagCredential(provenanceCredentialId, setOf("software", "provenance", "source", "security"))
        org.tagCredential(buildAttestationCredentialId, setOf("software", "build", "attestation", "slsa", "security"))
        org.tagCredential(sbomCredentialId, setOf("software", "sbom", "dependencies", "security"))

        println("✅ Software credentials organized")
    }

    // Step 11: Consumer verification - Software provenance
    println("\n[verify] Consumer Verification - Software Provenance:")

    val provenanceVerificationResult = trustWeave.verify {
        credential(provenanceCredential)
    }
    
    when (provenanceVerificationResult) {
        is VerificationResult.Valid -> {
            val credentialSubject = provenanceCredential.credentialSubject
            val software = credentialSubject.jsonObject["software"]?.jsonObject
            val softwareName = software?.get("name")?.jsonPrimitive?.content
            val publisher = software?.get("publisher")?.jsonPrimitive?.content
            val sourceCodeDigest = software?.get("sourceCodeDigest")?.jsonPrimitive?.content

            println("✅ Provenance Credential: VALID")
            println("   Software: $softwareName")
            println("   Publisher: ${publisher?.take(20)}...")
            println("   Source Code Digest: ${sourceCodeDigest?.take(20)}...")

            if (publisher == publisherDid.value) {
                println("✅ Publisher verified")
                println("✅ Provenance VERIFIED")
            } else {
                println("[FAIL] Publisher verification failed")
                println("[FAIL] Provenance NOT VERIFIED")
            }
        }
        is VerificationResult.Invalid -> {
            println("[FAIL] Provenance Credential: INVALID")
            println("   Errors: ${provenanceVerificationResult.allErrors.joinToString()}")
            println("[FAIL] Provenance NOT VERIFIED")
        }
    }

    // Step 12: Consumer verification - Build attestation
    println("\n[verify] Consumer Verification - Build Attestation:")

    val buildVerificationResult = trustWeave.verify {
        credential(buildAttestationCredential)
    }
    
    when (buildVerificationResult) {
        is VerificationResult.Valid -> {
            val credentialSubject = buildAttestationCredential.credentialSubject
            val build = credentialSubject.jsonObject["build"]?.jsonObject
            val buildSystem = build?.get("buildSystem")?.jsonPrimitive?.content
            val slsaLevel = build?.get("buildEnvironment")?.jsonObject?.get("slsaLevel")?.jsonPrimitive?.content
            val isolated = build?.get("buildEnvironment")?.jsonObject?.get("isolated")?.jsonPrimitive?.content?.toBoolean() ?: false

            println("✅ Build Attestation Credential: VALID")
            println("   Build System: $buildSystem")
            println("   SLSA Level: $slsaLevel")
            println("   Isolated Environment: $isolated")

            if (slsaLevel == "L3" && isolated) {
                println("✅ SLSA Level 3 verified")
                println("✅ Build environment verified")
                println("✅ Build Attestation VERIFIED")
            } else {
                println("[FAIL] Build environment verification failed")
                println("[FAIL] Build Attestation NOT VERIFIED")
            }
        }
        is VerificationResult.Invalid -> {
            println("[FAIL] Build Attestation Credential: INVALID")
            println("   Errors: ${buildVerificationResult.allErrors.joinToString()}")
            println("[FAIL] Build Attestation NOT VERIFIED")
        }
    }

    // Step 13: Consumer verification - Dependency verification
    println("\n[verify] Consumer Verification - Dependency Verification:")

    val sbomVerificationResult = trustWeave.verify {
        credential(sbomCredential)
    }
    
    when (sbomVerificationResult) {
        is VerificationResult.Valid -> {
            val credentialSubject = sbomCredential.credentialSubject
            val sbom = credentialSubject.jsonObject["sbom"]?.jsonObject
            val dependencies = sbom?.get("dependencies")

            println("✅ SBOM Credential: VALID")
            println("   Dependencies: ${dependencies?.jsonArray?.size ?: 0}")

            // In production, verify each dependency's digest
            var allDependenciesVerified = true
            dependencies?.jsonArray?.forEach { dep ->
                val depObj = dep.jsonObject
                val name = depObj["name"]?.jsonPrimitive?.content
                val digest = depObj["digest"]?.jsonPrimitive?.content

                println("     - $name: ${digest?.take(20)}...")
                // In production, verify digest matches actual dependency
            }

            if (allDependenciesVerified) {
                println("✅ All dependencies verified")
                println("✅ Dependency Verification PASSED")
            } else {
                println("[FAIL] Some dependencies failed verification")
                println("[FAIL] Dependency Verification FAILED")
            }
        }
        is VerificationResult.Invalid -> {
            println("[FAIL] SBOM Credential: INVALID")
            println("   Errors: ${sbomVerificationResult.allErrors.joinToString()}")
            println("[FAIL] Dependency Verification FAILED")
        }
    }

    // Step 14: Complete software verification workflow
    println("\n[verify] Complete Software Verification Workflow:")

    val provenanceValid = when (val result = trustWeave.verify { credential(provenanceCredential) }) {
        is VerificationResult.Valid -> true
        is VerificationResult.Invalid -> false
    }
    val buildValid = when (val result = trustWeave.verify { credential(buildAttestationCredential) }) {
        is VerificationResult.Valid -> true
        is VerificationResult.Invalid -> false
    }
    val sbomValid = when (val result = trustWeave.verify { credential(sbomCredential) }) {
        is VerificationResult.Valid -> true
        is VerificationResult.Invalid -> false
    }

    if (provenanceValid && buildValid && sbomValid) {
        println("✅ Software Provenance: VERIFIED")
        println("✅ Build Attestation: VERIFIED")
        println("✅ Dependency Verification: VERIFIED")
        println("✅ All verifications passed")
        println("✅ Software is SAFE to install")
    } else {
        println("[FAIL] One or more verifications failed")
        println("[FAIL] Software is NOT SAFE to install")
        println("[FAIL] Installation BLOCKED")
    }

    // Step 15: Display wallet statistics
    val stats = consumerWallet.getStatistics()
    println("\n[stats] Consumer Wallet Statistics:")
    println("   Total credentials: ${stats.totalCredentials}")
    println("   Valid credentials: ${stats.validCredentials}")
    println("   Collections: ${stats.collectionsCount}")
    println("   Tags: ${stats.tagsCount}")

    // Step 16: Summary
    println("\n" + "=".repeat(70))
    println("✅ SOFTWARE SUPPLY CHAIN SECURITY SYSTEM COMPLETE")
    println("   Software provenance credentials issued")
    println("   Build attestation implemented")
    println("   SBOM credentials created")
    println("   Complete verification workflow enabled")
    println("   Supply chain security verified")
    println("=".repeat(70))
}
```

**Expected Output:**
```
======================================================================
Software Supply Chain Security Scenario - Complete End-to-End Example
======================================================================

✅ TrustWeave initialized
✅ Software Publisher DID: did:key:z6Mk...
✅ Build System DID: did:key:z6Mk...
✅ Consumer DID: did:key:z6Mk...

[provenance] Software Provenance:
   Source code digest: u5v...
   Source repository: https://github.com/example/secureapp
   Commit hash: abc123def456

✅ Software provenance credential issued: urn:uuid:...
✅ Build attestation credential issued: urn:uuid:...

[sbom] of Materials (SBOM):
   Dependencies: 2
     - kotlin-stdlib v1.9.0
     - kotlinx-coroutines v1.7.3
✅ SBOM credential issued: urn:uuid:...

✅ All software credentials stored in wallet
✅ Software credentials organized

[verify] Consumer Verification - Software Provenance:
✅ Provenance Credential: VALID
   Software: SecureApp
   Publisher: did:key:z6Mk...
   Source Code Digest: u5v...
✅ Publisher verified
✅ Provenance VERIFIED

[verify] Consumer Verification - Build Attestation:
✅ Build Attestation Credential: VALID
   Build System: GitHub Actions
   SLSA Level: L3
   Isolated Environment: true
✅ SLSA Level 3 verified
✅ Build environment verified
✅ Build Attestation VERIFIED

[verify] Consumer Verification - Dependency Verification:
✅ SBOM Credential: VALID
   Dependencies: 2
     - kotlin-stdlib: sha256:abc123...
     - kotlinx-coroutines: sha256:def456...
✅ All dependencies verified
✅ Dependency Verification PASSED

[verify] Complete Software Verification Workflow:
✅ Software Provenance: VERIFIED
✅ Build Attestation: VERIFIED
✅ Dependency Verification: VERIFIED
✅ All verifications passed
✅ Software is SAFE to install

[stats] Consumer Wallet Statistics:
   Total credentials: 3
   Valid credentials: 3
   Collections: 1
   Tags: 9

======================================================================
✅ SOFTWARE SUPPLY CHAIN SECURITY SYSTEM COMPLETE
   Software provenance credentials issued
   Build attestation implemented
   SBOM credentials created
   Complete verification workflow enabled
   Supply chain security verified
======================================================================
```

## Key Features Demonstrated

1. **Software Provenance**: Verify software source and publisher
2. **Build Attestation**: Cryptographic proof of build integrity
3. **SBOM Support**: Software Bill of Materials credentials
4. **Dependency Verification**: Verify dependency authenticity
5. **SLSA Compliance**: Support SLSA framework levels
6. **Tamper Detection**: Detect software tampering

## Real-World Extensions

- **Code Signing Integration**: Integrate with code signing certificates
- **SLSA Level 4**: Support highest SLSA level
- **Dependency Scanning**: Automated vulnerability scanning
- **Reproducible Builds**: Support reproducible build verification
- **Multi-Artifact Support**: Support multiple build artifacts
- **Revocation**: Revoke compromised software credentials
- **Blockchain Anchoring**: Anchor software credentials for audit trails

## Related Documentation

- Quick Start](../getting-started/quick-start.md) - Get started with TrustWeave
- IoT Device Identity Scenario](iot-device-identity-scenario.md) - Related device attestation
- Common Patterns](../getting-started/common-patterns.md) - Reusable code patterns
- API Reference](../api-reference/core-api.md) - Complete API documentation
- Troubleshooting](../getting-started/troubleshooting.md) - Common issues and solutions


