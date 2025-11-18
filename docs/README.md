# VeriCore

<div align="center">

![VeriCore Logo](https://via.placeholder.com/200x200/1976d2/ffffff?text=VC)

### Beautiful, Type-Safe Trust and Identity for Kotlin

**A neutral, reusable trust and identity core** library designed to be domain-agnostic, chain-agnostic, DID-method-agnostic, and KMS-agnostic.

[![Version](https://img.shields.io/badge/version-1.0.0--SNAPSHOT-blue.svg)](https://github.com/geoknoesis/vericore)
[![License](https://img.shields.io/badge/license-Dual-green.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.0-orange.svg)](https://kotlinlang.org)

[Quick Start](#quick-start) • [Documentation](getting-started/quick-start.md) • [Scenarios](scenarios/README.md) • [GitHub](https://github.com/geoknoesis/vericore)

</div>

---

## 🚀 What is VeriCore?

VeriCore is a **production-ready Kotlin library** for building decentralized identity and trust systems. Built by [Geoknoesis LLC](https://www.geoknoesis.com), VeriCore provides the building blocks you need to implement W3C-compliant verifiable credentials, decentralized identifiers (DIDs), and blockchain anchoring—all with a beautiful, type-safe API.

### ✨ Why VeriCore?

- 🎯 **Domain-Agnostic** - Works for any use case (education, healthcare, IoT, supply chain, etc.)
- 🔗 **Chain-Agnostic** - Supports any blockchain via pluggable adapters
- 🆔 **DID-Method-Agnostic** - Works with any DID method (did:key, did:web, did:ion, etc.)
- 🔐 **KMS-Agnostic** - Supports any key management system
- 🛡️ **Type-Safe** - Leverages Kotlin's type system for compile-time safety
- ⚡ **Coroutine-Based** - Built for modern async/await patterns
- 🧪 **Testable** - Comprehensive test utilities and in-memory implementations

---

## ⚡ Quick Start

Get started with VeriCore in **30 seconds**:

```kotlin
import com.geoknoesis.vericore.VeriCore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun main() = runBlocking {
    // Create VeriCore instance
    val vericore = VeriCore.create()
    
    // Create a DID
    val did = vericore.createDid().getOrThrow()
    println("Created DID: ${did.id}")
    
    // Issue a verifiable credential
    val credential = vericore.issueCredential(
        issuerDid = did.id,
        issuerKeyId = did.verificationMethod.firstOrNull()?.id ?: error("No key found"),
        credentialSubject = buildJsonObject {
            put("id", "did:example:alice")
            put("name", "Alice")
            put("email", "alice@example.com")
        },
        types = listOf("VerifiableCredential", "PersonCredential")
    ).getOrThrow()
    
    // Verify the credential
    val verification = vericore.verifyCredential(credential).getOrThrow()
    println("Credential valid: ${verification.valid}")
    
    // Create a wallet and store the credential
    val wallet = vericore.createWallet(holderDid = "did:example:alice").getOrThrow()
    val credentialId = wallet.store(credential)
    println("✅ Stored credential: $credentialId")
}
```

**Installation:**

```kotlin
dependencies {
    implementation("com.geoknoesis.vericore:vericore-all:1.0.0-SNAPSHOT")
}
```

[📖 Full Quick Start Guide →](getting-started/quick-start.md)

---

## 🎯 Core Features

### 🛡️ W3C Compliant

Full support for W3C Verifiable Credentials 1.1 and DID Core 1.0 specifications

### 🔑 Decentralized Identifiers

Create, resolve, and manage DIDs with any DID method via pluggable interfaces

### 📜 Verifiable Credentials

Issue, verify, and manage verifiable credentials with cryptographic proofs

### 🔗 Blockchain Anchoring

Anchor data to any blockchain with chain-agnostic interfaces

### 💼 Wallet Management

Store, organize, and present credentials with powerful wallet capabilities

### 🔐 Key Management

Pluggable key management supporting multiple algorithms and backends

---

## 🌟 Real-World Use Cases

VeriCore powers trust and identity systems across multiple domains. Explore **25+ complete scenarios** with runnable code examples:

### 🔐 Cybersecurity & Access Control

- **[Zero Trust Continuous Authentication](scenarios/zero-trust-authentication-scenario.md)** - Continuous authentication without sessions
- **[Security Clearance & Access Control](scenarios/security-clearance-access-control-scenario.md)** - Multi-level security clearance verification
- **[Software Supply Chain Security](scenarios/software-supply-chain-security-scenario.md)** - Prevent supply chain attacks with verifiable provenance

### 🆔 Identity & Verification

- **[Government Digital Identity](scenarios/government-digital-identity-scenario.md)** - Government-issued digital identity workflows
- **[Age Verification](scenarios/age-verification-scenario.md)** - Privacy-preserving age verification with photo association
- **[Biometric Verification](scenarios/biometric-verification-scenario.md)** - Multi-modal biometric verification (fingerprint, face, voice)

### 🎓 Education & Credentials

- **[Academic Credentials](scenarios/academic-credentials-scenario.md)** - University credential issuance and verification
- **[National Education Credentials](scenarios/national-education-credentials-algeria-scenario.md)** - National-level credential infrastructure

### 🏥 Healthcare

- **[Healthcare Medical Records](scenarios/healthcare-medical-records-scenario.md)** - Privacy-preserving medical data sharing
- **[Vaccination Health Passports](scenarios/vaccination-health-passport-scenario.md)** - Health credentials for travel and access

### 🔌 IoT & Devices

- **[IoT Device Identity](scenarios/iot-device-identity-scenario.md)** - Device onboarding and identity management
- **[IoT Sensor Data Provenance](scenarios/iot-sensor-data-provenance-scenario.md)** - Verify sensor data authenticity and integrity
- **[IoT Firmware Update Verification](scenarios/iot-firmware-update-verification-scenario.md)** - Secure firmware update verification

[📚 View All Scenarios →](scenarios/README.md)

---

## 🏗️ Architecture

VeriCore is built on a modular, pluggable architecture:

```
┌─────────────────────────────────────────────────────────┐
│                    VeriCore Facade                       │
│              (Unified API Entry Point)                   │
└─────────────────────────────────────────────────────────┘
                          │
        ┌─────────────────┼─────────────────┐
        │                 │                 │
┌───────▼──────┐  ┌───────▼──────┐  ┌───────▼──────┐
│  DID Layer   │  │ Credential    │  │   Wallet     │
│              │  │   Service     │  │   Service    │
└───────┬──────┘  └───────┬──────┘  └───────┬──────┘
        │                 │                 │
┌───────▼─────────────────▼─────────────────▼───────┐
│         Pluggable Adapters (SPI)                   │
│  • DID Methods  • KMS  • Blockchains  • Services   │
└────────────────────────────────────────────────────┘
```

**Key Design Principles:**

- **Neutrality** - No domain-specific or chain-specific logic in core
- **Pluggability** - All external dependencies via interfaces
- **Type Safety** - Compile-time guarantees with Kotlin
- **Testability** - In-memory implementations for all interfaces

[📖 Architecture Overview →](introduction/architecture-overview.md)

---

## 📚 Documentation

### 🚀 [Getting Started](getting-started/quick-start.md)

Installation, quick start guide, and common patterns

### 📖 [Core Concepts](core-concepts/README.md)

DIDs, Verifiable Credentials, Wallets, and Blockchain Anchoring

### 🔧 [API Reference](api-reference/core-api.md)

Complete API documentation with examples

### 🎓 [Use Case Scenarios](scenarios/README.md)

25+ complete, runnable examples for real-world use cases

### ⚙️ [Advanced Topics](advanced/key-rotation.md)

Key rotation, verification policies, plugin lifecycle, and more

### ❓ [FAQ](faq.md)

Common questions and answers

---

## 🎓 Learn by Example

Each scenario includes:

- ✅ **Complete, runnable code** - Copy, paste, and run
- ✅ **Industry context** - Real-world use cases and value propositions
- ✅ **Best practices** - Production-ready patterns
- ✅ **Visual diagrams** - Mermaid flowcharts showing the architecture
- ✅ **Expected output** - See what success looks like

**Popular Examples:**

```kotlin
// Academic Credentials
val degree = vericore.issueCredential(
    issuerDid = universityDid,
    credentialSubject = buildJsonObject {
        put("id", studentDid)
        put("degree", "Bachelor of Science")
        put("major", "Computer Science")
    },
    types = listOf("VerifiableCredential", "DegreeCredential")
).getOrThrow()

// Age Verification (Privacy-Preserving)
val ageCredential = vericore.issueCredential(
    issuerDid = identityProviderDid,
    credentialSubject = buildJsonObject {
        put("id", individualDid)
        put("ageVerification", buildJsonObject {
            put("age", 25)
            put("minimumAge", 18)
            // No personal details exposed!
        })
    }
).getOrThrow()

// IoT Sensor Data Provenance
val sensorData = vericore.issueCredential(
    issuerDid = sensorDid,
    credentialSubject = buildJsonObject {
        put("sensorData", buildJsonObject {
            put("dataDigest", dataDigest)
            put("timestamp", Instant.now().toString())
            put("calibrationStatus", "Valid")
        })
    }
).getOrThrow()
```

[🚀 Explore All Scenarios →](scenarios/README.md)

---

## 🛠️ Developer Experience

VeriCore is designed for developer happiness:

### Type-Safe Configuration

```kotlin
val vericore = VeriCore.create {
    didMethod("key") {
        algorithm = DidCreationOptions.KeyAlgorithm.ED25519
    }
    walletFactory {
        enableOrganization = true
        enablePresentation = true
    }
}
```

### Predictable Error Handling

```kotlin
val result = vericore.createDid()
result.fold(
    onSuccess = { did -> println("Created: ${did.id}") },
    onFailure = { error -> 
        when (error) {
            is VeriCoreError.DidMethodNotRegistered -> 
                println("Method not registered: ${error.method}")
            else -> println("Error: ${error.message}")
        }
    }
)
```

### Composable DSLs

```kotlin
val wallet = vericore.createWallet(holderDid = userDid) {
    label = "My Wallet"
    enableOrganization = true
    enablePresentation = true
    property("autoUnlock", true)
}.getOrThrow()
```

---

## 🤝 Community & Support

- **Created by**: [Geoknoesis LLC](https://www.geoknoesis.com)
- **License**: Dual license (Open source for non-commercial, Commercial for production)
- **Version**: 1.0.0-SNAPSHOT
- **GitHub**: [geoknoesis/vericore](https://github.com/geoknoesis/vericore)

---

## 📖 Next Steps

### ▶️ [Quick Start](getting-started/quick-start.md)

Get up and running in 5 minutes

### 💡 [Use Case Scenarios](scenarios/README.md)

Explore 25+ real-world examples

### 📚 [Core Concepts](core-concepts/README.md)

Learn the fundamentals

### 🔌 [API Reference](api-reference/core-api.md)

Complete API documentation

---

<div align="center">

**Ready to build the future of trust and identity?**

[Get Started Now →](getting-started/quick-start.md)

Made with ❤️ by [Geoknoesis LLC](https://www.geoknoesis.com)

</div>
