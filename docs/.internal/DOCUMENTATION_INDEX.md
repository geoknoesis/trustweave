---
title: TrustWeave Documentation Index
---

# TrustWeave Documentation Index

> TrustWeave is developed and maintained by [Geoknoesis LLC](https://www.geoknoesis.com).

## Main Documentation

- **[README.md](../README.md)**: Main project documentation with quick start guide
- **[CHANGELOG.md](../CHANGELOG.md)**: Detailed changelog of all optimizations and improvements
- **Runnable quick start**: `./gradlew :TrustWeave-examples:runQuickStartSample`

## Introduction

- **[Executive Overview](introduction/executive-overview.md)**: High-level overview of TrustWeave's value proposition, benefits, market position, and real-world applications
- **[What is TrustWeave?](introduction/what-is-TrustWeave.md)**: Library's purpose, design philosophy, and core principles
- **[Key Features](introduction/key-features.md)**: Comprehensive overview of TrustWeave's capabilities
- **[Use Cases](introduction/use-cases.md)**: Common applications and use case scenarios
- **[Architecture Overview](introduction/architecture-overview.md)**: Modular design, module structure, mental model, and extensibility patterns

## Core Concepts Documentation

- **[Core Concepts](core-concepts/README.md)**: Introduction to fundamental concepts
  - **[DIDs](core-concepts/dids.md)**: What they are, why they matter, and how to register methods
  - **[Verifiable Credentials](core-concepts/verifiable-credentials.md)**: Issuance/verification flow with code snippets
  - **[Wallets](core-concepts/wallets.md)**: Capability-based composition and DSL usage
  - **[Blockchain Anchoring](core-concepts/blockchain-anchoring.md)**: Anchoring workflow and client configuration
  - **[Smart Contracts](core-concepts/smart-contracts.md)**: Executable agreements with verifiable credentials
  - **[Key Management](core-concepts/key-management.md)**: KMS abstractions and rotation tips
  - **[JSON Canonicalization](core-concepts/json-canonicalization.md)**: Deterministic hashing and signing

## Tutorials

- **[Beginner Tutorial Series](tutorials/beginner-tutorial-series.md)**: Structured learning path for new developers
  - Tutorial 1: Your First DID
  - Tutorial 2: Issuing Your First Credential
  - Tutorial 3: Managing Credentials with Wallets
  - Tutorial 4: Building a Complete Workflow
  - Tutorial 5: Adding Blockchain Anchoring

- **[DID Operations Tutorial](tutorials/did-operations-tutorial.md)**: Comprehensive guide to DID operations
  - Creating, resolving, updating, and deactivating DIDs
  - Working with multiple DID methods
  - Advanced DID operations

- **[Wallet API Tutorial](tutorials/wallet-api-tutorial.md)**: Complete guide to using wallets
  - Creating wallets
  - Storing and organizing credentials
  - Querying credentials
  - Creating presentations
  - Lifecycle management

- **[Credential Issuance Tutorial](tutorials/credential-issuance-tutorial.md)**: Deep dive into credential issuance
  - Issuance workflows
  - Credential types and structures
  - Proof generation

- **[Common Patterns](getting-started/common-patterns.md)**: Common usage patterns and best practices
  - Issuer → Holder → Verifier workflow
  - Batch operations
  - Error recovery with fallbacks
  - Credential lifecycle management
  - Multi-chain anchoring
  - Wallet organization patterns

## User Experience (UX) Documentation

- **[UX Documentation](ux/README.md)**: User experience guides for TrustWeave applications
  - **[Trusted Domain UX Guide](ux/trusted-domain-ux-guide.md)**: Complete UX guide for creating, configuring, and using Trusted Domains
    - Step-by-step user journeys from signup to credential management
    - Detailed screen mockups and interaction flows
    - Backend sequence diagrams showing system interactions
    - Scenarios for DID creation, credential updates, and revocation
    - Error handling and mobile considerations

## Use Case Scenarios

See **[Scenarios Overview](scenarios/README.md)** for all available scenarios organized by category.

**🔐 Cybersecurity & Access Control:**
- **[Zero Trust Continuous Authentication](scenarios/zero-trust-authentication-scenario.md)**: Continuous authentication without traditional sessions
- **[Security Clearance & Access Control](scenarios/security-clearance-access-control-scenario.md)**: Privacy-preserving security clearance verification for classified systems
- **[Biometric Verification](scenarios/biometric-verification-scenario.md)**: Multi-modal biometric verification with liveness detection
- **[Security Training & Certification Verification](scenarios/security-training-certification-scenario.md)**: Instant verification of security certifications and training
- **[Software Supply Chain Security](scenarios/software-supply-chain-security-scenario.md)**: Software provenance, build attestation, and SBOM verification

**🆔 Identity & Verification:**
- **[Government Digital Identity](scenarios/government-digital-identity-scenario.md)**: Government-issued digital identity workflows
- **[Professional Identity](scenarios/professional-identity-scenario.md)**: Professional credential wallet management
- **[Age Verification](scenarios/age-verification-scenario.md)**: Privacy-preserving age verification for age-restricted services
- **[Financial Services (KYC)](scenarios/financial-services-kyc-scenario.md)**: KYC/AML compliance and credential reuse

**🎓 Education & Credentials:**
- **[Academic Credentials](scenarios/academic-credentials-scenario.md)**: University credential issuance and verification
- **[National Education Credentials Algeria](scenarios/national-education-credentials-algeria-scenario.md)**: AlgeroPass national-level education credential system

**💼 Employment & Professional:**
- **[Employee Onboarding and Background Verification](scenarios/employee-onboarding-scenario.md)**: Complete employee onboarding with credential verification

**🏥 Healthcare:**
- **[Healthcare Medical Records](scenarios/healthcare-medical-records-scenario.md)**: Patient credential workflows and privacy-preserving data sharing
- **[Vaccination and Health Passports](scenarios/vaccination-health-passport-scenario.md)**: Privacy-preserving health credentials for travel and access

**📦 Supply Chain & Provenance:**
- **[Supply Chain Traceability](scenarios/supply-chain-traceability-scenario.md)**: Complete supply chain provenance tracking
- **[Supply Chain & EUDR Compliance](scenarios/supply-chain-eudr-compliance-scenario.md)**: EU Deforestation Regulation compliance with EO data and Digital Product Passports
- **[Digital Workflow & Provenance](scenarios/digital-workflow-provenance-scenario.md)**: PROV-O workflow provenance tracking

**📍 Geospatial & Location:**
- **[Proof of Location](scenarios/proof-of-location-scenario.md)**: Geospatial location proofs and verification
- **[Earth Observation](scenarios/earth-observation-scenario.md)**: Complete EO data integrity workflow
- **[Spatial Web Authorization](scenarios/spatial-web-authorization-scenario.md)**: DID-based authorization for spatial entities

**🌍 Earth Observation & Climate:**
- **[Parametric Insurance with Earth Observation](scenarios/parametric-insurance-eo-scenario.md)**: Parametric insurance using EO data credentials. Solve the "Oracle Problem" with standardized, multi-provider data ecosystems
- **[Carbon Markets & Digital MRV (dMRV)](scenarios/carbon-markets-dmrv-scenario.md)**: Digital Measurement, Reporting, and Verification for carbon markets. Prevent double counting with blockchain-anchored credentials
- **[Supply Chain & EUDR Compliance](scenarios/supply-chain-eudr-compliance-scenario.md)**: EU Deforestation Regulation compliance with EO data evidence and Digital Product Passports

**📰 Media & Content:**
- **[News Industry](scenarios/news-industry-scenario.md)**: Content provenance and authenticity verification
- **[Event Ticketing and Access Control](scenarios/event-ticketing-scenario.md)**: Verifiable tickets with transfer control and fraud prevention

**💾 Data Management:**
- **[Data Catalog & DCAT](scenarios/data-catalog-dcat-scenario.md)**: Verifiable data catalog system using DCAT

**🔌 IoT & Devices:**
- **[IoT Device Identity](scenarios/iot-device-identity-scenario.md)**: Connected device onboarding and identity management
- **[IoT Sensor Data Provenance & Integrity](scenarios/iot-sensor-data-provenance-scenario.md)**: Verify sensor data authenticity and integrity
- **[IoT Firmware Update Verification](scenarios/iot-firmware-update-verification-scenario.md)**: Verify firmware authenticity and update authorization
- **[IoT Device Ownership Transfer](scenarios/iot-device-ownership-transfer-scenario.md)**: Secure device ownership transfer with audit trail

**🤝 Trust & Reputation:**
- **[Web of Trust](scenarios/web-of-trust-scenario.md)**: Trust networks and reputation systems

**🛡️ Insurance & Claims:**
- **[Insurance Claims and Verification](scenarios/insurance-claims-scenario.md)**: Complete insurance claims verification with fraud prevention
- **[Parametric Insurance with Earth Observation](scenarios/parametric-insurance-eo-scenario.md)**: Parametric insurance using EO data credentials for automated payouts

**🔒 Compliance & Security:**
- **[SOC2 Compliance](scenarios/soc2-compliance-scenario.md)**: SOC2 Type II compliance with immutable audit trails, access control, key management, and automated reporting

## API Reference

- **[Wallet API](api-reference/wallet-api.md)**: Complete wallet API reference
  - Core interfaces
  - Data models
  - Extension functions
  - Usage examples
- **[Credential Service API](api-reference/credential-service-api.md)**: SPI and typed options for issuers/verifiers

## Supported Plugins

- **[Supported Plugins](plugins.md)**: Comprehensive listing of all supported plugins organized by category
  - DID Method Plugins (did:key, did:web, did:ethr, did:ion, and more)
  - Blockchain Anchor Plugins (Ethereum, Base, Arbitrum, Algorand, Polygon)
  - Key Management Service (KMS) Plugins (AWS, Azure, Google Cloud, HashiCorp Vault)
  - Other Integrations (GoDiddy, walt.id)
  - Links to detailed integration guides for each plugin
- **[Integration Modules](integrations/README.md)**: Detailed integration guides with setup instructions

## Configuration

- **[Configuration Reference](configuration/README.md)**: Complete configuration options and customization guide
- **[Default Configuration](configuration/defaults.md)**: What defaults are used by `TrustWeave.create()` and how to customize them

## Advanced Topics

- **[Key Rotation](advanced/key-rotation.md)**: Step-by-step issuer rotation guidance with KMS and DID updates
- **[Verification Policies](advanced/verification-policies.md)**: Enforce expiration, revocation, audience, and anchoring checks
- **[Error Handling](advanced/error-handling.md)**: Structured error handling with TrustWeaveError types, error code quick reference, common pitfalls, and Result utilities
- **[Plugin Lifecycle](advanced/plugin-lifecycle.md)**: Initialize, start, stop, and cleanup plugins
- Testability tips are included throughout using `TrustWeave-testkit`

## Migration Guides

- **[Migration Guides](migration/README.md)**: Version compatibility matrix, deprecation policy, and migration instructions
- **[Migrating to 1.0.0](migration/migrating-to-1.0.0.md)**: Detailed migration guide covering type-safe options, Result-based APIs, error handling, and plugin lifecycle

## Security

- **[Security Best Practices](security/README.md)**: Comprehensive security guide covering key management, credential storage, DID management, network security, and compliance

## Contributing

- **[Creating Plugins](contributing/creating-plugins.md)**: Complete guide for implementing custom plugins
  - DID methods, blockchain clients, proof generators
  - Key management services, credential services, wallet factories
  - Registration methods (manual and SPI)
  - Plugin lifecycle management
  - Testing and best practices

## Module Documentation

### Core Modules
- **[TrustWeave-contract](modules/trustweave-contract.md)**: Smart Contract abstraction for executable agreements
  - Contract lifecycle management
  - Execution models (parametric, conditional, scheduled, etc.)
  - Verifiable credentials integration
  - Blockchain anchoring support

- **[TrustWeave-anchor/README.md](../chains/TrustWeave-anchor/src/main/kotlin/com/geoknoesis/TrustWeave/anchor/README.md)**: Comprehensive anchor package documentation
  - Type-safe options and chain IDs
  - Exception hierarchy
  - Usage examples
  - Architecture benefits

### Adapter Modules
- **[chains/plugins/ganache/README.md](../chains/plugins/ganache/README.md)**: Ganache adapter with TestContainers
- **[chains/plugins/indy/README.md](../chains/plugins/indy/README.md)**: Hyperledger Indy adapter

### Test Utilities
- **[TrustWeave-testkit/eo/README.md](../core/TrustWeave-testkit/src/main/kotlin/com/geoknoesis/TrustWeave/testkit/eo/README.md)**: EO test integration utilities

## Features & Plugins

- **[Features Documentation](features/README.md)**: Overview of all TrustWeave features
- **[Feature Details](features/IMPLEMENTED_FEATURES.md)**: Complete list of all features
- **[Usage Guide](features/USAGE_GUIDE.md)**: How to use features (Audit Logging, Metrics, QR Codes, etc.)

## Key Features Documented

### Type Safety
- ✅ Type-safe options (`AlgorandOptions`, `PolygonOptions`, `GanacheOptions`, `IndyOptions`)
- ✅ Type-safe chain IDs (`ChainId` sealed class)
- ✅ Compile-time validation

### Error Handling
- ✅ Exception hierarchy documentation
- ✅ Error context examples
- ✅ Migration guide

### Code Quality
- ✅ Gradle wrapper usage
- ✅ ktlint integration
- ✅ Build commands

### Performance
- ✅ Digest caching configuration
- ✅ JSON optimization details

### Architecture
- ✅ `AbstractBlockchainAnchorClient` benefits
- ✅ Code reuse patterns
- ✅ Resource management
- ✅ **Wallet management system** with ISP-based design
- ✅ **Type-safe capability checking** for wallets
- ✅ **Composable wallet interfaces** for extensibility

## Quick Reference

### Building
```bash
./gradlew build          # Unix/Linux/macOS
.\gradlew.bat build      # Windows
```

### Testing
```bash
./gradlew test
```

### Code Quality
```bash
./gradlew ktlintCheck    # Check formatting
./gradlew ktlintFormat   # Auto-format
```

### Type-Safe Configuration
```kotlin
import org.trustweave.anchor.options.AlgorandOptions
import org.trustweave.anchor.ChainId

val chainId = ChainId.Algorand.Testnet
val options = AlgorandOptions(algodUrl = "...", privateKey = "...")
val client = AlgorandBlockchainAnchorClient(chainId.toString(), options)
```

### Error Handling
```kotlin
import org.trustweave.anchor.exceptions.*

try {
    val result = client.writePayload(payload)
} catch (e: BlockchainTransactionException) {
    // Rich context: chainId, txHash, payloadSize, gasUsed
}
```

## Migration Guides

See [CHANGELOG.md](../CHANGELOG.md) for detailed migration guides:
- Migrating to type-safe options
- Migrating to type-safe chain IDs
- Updating error handling
- Resource management patterns

## Licensing

- TrustWeave is dual-licensed. Non-commercial and educational users can rely on the open source license; commercial deployments require a Geoknoesis commercial agreement.
- Full details: [Licensing Overview](licensing/README.md)

## Business & Partnerships

- **[Partner Program](PARTNER_PROGRAM.md)**: Comprehensive partner program guide for organizations and individuals interested in referring commercial customers
  - Partner tiers and benefits (Bronze, Silver, Gold)
  - Commission structure and remuneration details
  - Deal qualification criteria and registration process
  - Partner resources, training, and support
  - Application process and partner agreement details
- **[Partner Referral Agreement Template](PARTNER_REFERRAL_AGREEMENT_TEMPLATE.md)**: Legal template for partner referral agreements
  - Complete agreement terms and conditions
  - Commission structure and payment terms
  - Confidentiality and non-compete provisions
  - Dispute resolution and termination clauses
  - **Note:** Template for reference only - all agreements must be executed through Geoknoesis LLC's legal process

## FAQ

- [Frequently Asked Questions](faq.md) collects quick answers about samples, licensing, custom integrations, and policy enforcement.
