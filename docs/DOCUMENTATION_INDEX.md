# VeriCore Documentation Index

> VeriCore is developed and maintained by [Geoknoesis LLC](https://www.geoknoesis.com).

## Main Documentation

- **[README.md](../README.md)**: Main project documentation with quick start guide
- **[CHANGELOG.md](../CHANGELOG.md)**: Detailed changelog of all optimizations and improvements
- **Runnable quick start**: `./gradlew :vericore-examples:runQuickStartSample`

## Core Concepts Documentation

- **[Core Concepts](core-concepts/README.md)**: Introduction to fundamental concepts
  - **[DIDs](core-concepts/dids.md)**: What they are, why they matter, and how to register methods
  - **[Verifiable Credentials](core-concepts/verifiable-credentials.md)**: Issuance/verification flow with code snippets
  - **[Wallets](core-concepts/wallets.md)**: Capability-based composition and DSL usage
  - **[Blockchain Anchoring](core-concepts/blockchain-anchoring.md)**: Anchoring workflow and client configuration
  - **[Key Management](core-concepts/key-management.md)**: KMS abstractions and rotation tips
  - **[JSON Canonicalization](core-concepts/json-canonicalization.md)**: Deterministic hashing and signing

## Tutorials

- **[Wallet API Tutorial](tutorials/wallet-api-tutorial.md)**: Complete guide to using wallets
  - Creating wallets
  - Storing and organizing credentials
  - Querying credentials
  - Creating presentations
  - Lifecycle management

- **[Common Patterns](getting-started/common-patterns.md)**: Common usage patterns and best practices
  - Issuer → Holder → Verifier workflow
  - Batch operations
  - Error recovery with fallbacks
  - Credential lifecycle management
  - Multi-chain anchoring
  - Wallet organization patterns

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
- **[Digital Workflow & Provenance](scenarios/digital-workflow-provenance-scenario.md)**: PROV-O workflow provenance tracking

**📍 Geospatial & Location:**
- **[Proof of Location](scenarios/proof-of-location-scenario.md)**: Geospatial location proofs and verification
- **[Earth Observation](scenarios/earth-observation-scenario.md)**: Complete EO data integrity workflow
- **[Spatial Web Authorization](scenarios/spatial-web-authorization-scenario.md)**: DID-based authorization for spatial entities

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

## Advanced Topics

- **[Key Rotation](advanced/key-rotation.md)**: Step-by-step issuer rotation guidance with KMS and DID updates
- **[Verification Policies](advanced/verification-policies.md)**: Enforce expiration, revocation, audience, and anchoring checks
- **[Error Handling](advanced/error-handling.md)**: Structured error handling with VeriCoreError types and Result utilities
- **[Plugin Lifecycle](advanced/plugin-lifecycle.md)**: Initialize, start, stop, and cleanup plugins
- Testability tips are included throughout using `vericore-testkit`

## Contributing

- **[Creating Plugins](contributing/creating-plugins.md)**: Complete guide for implementing custom plugins
  - DID methods, blockchain clients, proof generators
  - Key management services, credential services, wallet factories
  - Registration methods (manual and SPI)
  - Plugin lifecycle management
  - Testing and best practices

## Module Documentation

### Core Modules
- **[vericore-anchor/README.md](../chains/vericore-anchor/src/main/kotlin/com/geoknoesis/vericore/anchor/README.md)**: Comprehensive anchor package documentation
  - Type-safe options and chain IDs
  - Exception hierarchy
  - Usage examples
  - Architecture benefits

### Adapter Modules
- **[chains/plugins/ganache/README.md](../chains/plugins/ganache/README.md)**: Ganache adapter with TestContainers
- **[chains/plugins/indy/README.md](../chains/plugins/indy/README.md)**: Hyperledger Indy adapter

### Test Utilities
- **[vericore-testkit/eo/README.md](../core/vericore-testkit/src/main/kotlin/com/geoknoesis/vericore/testkit/eo/README.md)**: EO test integration utilities

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
import com.geoknoesis.vericore.anchor.options.AlgorandOptions
import com.geoknoesis.vericore.anchor.ChainId

val chainId = ChainId.Algorand.Testnet
val options = AlgorandOptions(algodUrl = "...", privateKey = "...")
val client = AlgorandBlockchainAnchorClient(chainId.toString(), options)
```

### Error Handling
```kotlin
import com.geoknoesis.vericore.anchor.exceptions.*

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

- VeriCore is dual-licensed. Non-commercial and educational users can rely on the open source license; commercial deployments require a Geoknoesis commercial agreement.
- Full details: [Licensing Overview](licensing/README.md)

## FAQ

- [Frequently Asked Questions](faq.md) collects quick answers about samples, licensing, custom integrations, and policy enforcement.
