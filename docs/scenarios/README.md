---
title: Use Case Scenarios
nav_order: 40
parent: null
nav_exclude: false
keywords:
  - scenarios
  - use cases
  - examples
  - workflows
  - real-world
  - healthcare
  - education
  - supply chain
  - iot
---

# TrustWeave Use Case Scenarios

> **Version:** 1.0.0-SNAPSHOT
> Complete end-to-end workflows demonstrating TrustWeave in real-world applications.

## Overview

Each scenario provides a complete, runnable example showing how to use TrustWeave for specific use cases. All scenarios follow the same pattern:

1. **Setup** – Create TrustWeave instance and configure components
2. **DID Creation** – Create identifiers for actors (issuers, holders, verifiers)
3. **Credential Operations** – Issue, store, and verify credentials
4. **Advanced Features** – Wallet organization, presentations, blockchain anchoring
5. **Verification** – Complete verification workflows

## Popular Scenarios

Get started quickly with these commonly used scenarios:

- **[Academic Credentials](academic-credentials-scenario.md)** ⭐ - University credential issuance and verification
- **[Employee Onboarding](employee-onboarding-scenario.md)** ⭐ - Complete employee onboarding system
- **[Healthcare Medical Records](healthcare-medical-records-scenario.md)** ⭐ - Patient credential workflows
- **[Financial Services (KYC)](financial-services-kyc-scenario.md)** ⭐ - KYC/AML compliance workflows
- **[Government Digital ID](government-digital-identity-scenario.md)** ⭐ - Government-issued digital identity

## Scenarios by Domain

Scenarios are organized by industry domain and use case. Each scenario includes a complete, runnable example with industry context and best practices.

### 🆔 Identity & Access Management

**Digital Identity:**
- **[Government Digital Identity](government-digital-identity-scenario.md)** ⭐
  Citizens receive, store, and present official IDs. Complete workflow for government-issued digital identity.

- **[Professional Identity](professional-identity-scenario.md)** ⭐
  Professional credential wallet management. Demonstrates storing and organizing professional certifications and licenses.

**Authentication & Authorization:**
- **[Zero Trust Continuous Authentication](zero-trust-authentication-scenario.md)** ⭐
  Continuous authentication system without traditional sessions. Short-lived credentials, device attestation, risk-based access control, and continuous re-authentication.

- **[Security Clearance & Access Control](security-clearance-access-control-scenario.md)** ⭐
  Privacy-preserving security clearance verification for classified systems. Multi-level access control (Top Secret, Secret, Confidential) with selective disclosure.

- **[Biometric Verification](biometric-verification-scenario.md)** ⭐
  Complete biometric verification system with fingerprint, face, and voice. Privacy-preserving biometric templates with liveness detection and multi-modal verification.

**Identity Verification:**
- **[Age Verification](age-verification-scenario.md)** ⭐
  Privacy-preserving age verification for age-restricted services. Verify age without revealing personal information. Includes photo association for face verification.

- **[Web of Trust](web-of-trust-scenario.md)** ⭐
  Build trust networks and reputation systems. Demonstrate how entities establish and verify trust relationships.

### 🎓 Education & Professional Development

**Academic Credentials:**
- **[Academic Credentials](academic-credentials-scenario.md)** ⭐
  University credential issuance and verification. Demonstrates degree credentials, transcript management, and wallet organization.

- **[National Education Credentials Algeria](national-education-credentials-algeria-scenario.md)** ⭐
  AlgeroPass national-level education credential system. Shows how to build a national credential infrastructure.

**Professional Development:**
- **[Employee Onboarding and Background Verification](employee-onboarding-scenario.md)** ⭐
  Complete employee onboarding system with credential verification. Demonstrates education, work history, certification, and background check credentials.

- **[Security Training & Certification Verification](security-training-certification-scenario.md)** ⭐
  Instant verification of security certifications (CISSP, CEH, Security+, etc.) and training credentials. Privacy-preserving verification for HR and compliance.

### 🏥 Healthcare

- **[Healthcare Medical Records](healthcare-medical-records-scenario.md)** ⭐
  Share consented medical data across providers with audit trails. Patient credential workflows and privacy-preserving data sharing.

- **[Vaccination and Health Passports](vaccination-health-passport-scenario.md)** ⭐
  Vaccination credential issuance and health passport verification. Privacy-preserving health credentials for travel and access control.

### 💰 Financial Services

- **[Financial Services (KYC)](financial-services-kyc-scenario.md)** ⭐
  Streamline onboarding and reuse credentials across institutions. KYC/AML compliance workflows.

### 🏛️ Government & Public Sector

- **[Government Digital Identity](government-digital-identity-scenario.md)** ⭐
  Citizens receive, store, and present official IDs. Complete workflow for government-issued digital identity.

- **[National Education Credentials Algeria](national-education-credentials-algeria-scenario.md)** ⭐
  AlgeroPass national-level education credential system. Shows how to build a national credential infrastructure.

### 📦 Supply Chain & Logistics

**Physical Supply Chain:**
- **[Supply Chain Traceability](supply-chain-traceability-scenario.md)** ⭐
  Follow goods from origin to shelf with verifiable checkpoints. Complete supply chain provenance tracking.

- **[Supply Chain & EUDR Compliance](supply-chain-eudr-compliance-scenario.md)** ⭐
  EU Deforestation Regulation (EUDR) compliance with EO data. Digital Product Passports (DPP) using verifiable credentials. Automated compliance verification for 2025 EUDR requirements.

**Digital Provenance:**
- **[Digital Workflow & Provenance](digital-workflow-provenance-scenario.md)** ⭐
  PROV-O workflow provenance tracking for digital information. Track information flow through complex workflows.

- **[Software Supply Chain Security](software-supply-chain-security-scenario.md)** ⭐
  Software provenance, build attestation, and SBOM verification. Prevent supply chain attacks with cryptographic proof of software authenticity and dependency verification.

### 📍 Geospatial & Earth Observation

**Location Services:**
- **[Proof of Location](proof-of-location-scenario.md)** ⭐
  Capture sensor evidence, notarise location, and present proofs. Geospatial location proofs and verification.

- **[Spatial Web Authorization](spatial-web-authorization-scenario.md)** ⭐
  Grant AR/VR permissions using verifiable policies. DID-based authorization for spatial entities.


**Earth Observation:**
- **[Earth Observation](earth-observation-scenario.md)** ⭐
  Manage satellite imagery provenance with digest anchoring. Complete EO data integrity workflow with Linksets.

- **[Field Data Collection](field-data-collection-scenario.md)** ⭐
  Collect and verify field data with worker authorization credentials. Issue verifiable credentials for collection events, anchor data to blockchain for tamper-proof records, and verify data integrity. Applicable to forestry, agriculture, infrastructure inspection, and environmental monitoring.

### 🌍 Climate & Environmental

**Carbon Markets:**
- **[Carbon Markets & Digital MRV (dMRV)](carbon-markets-dmrv-scenario.md)** ⭐
  Digital Measurement, Reporting, and Verification for carbon markets using EO data. Prevent double counting with blockchain-anchored credentials. Track carbon credit lifecycle from issuance to retirement. Supports nested climate accounting (Nation → City → Company) and Token Taxonomy Framework (TTF) integration.

**Regulatory Compliance:**
- **[Supply Chain & EUDR Compliance](supply-chain-eudr-compliance-scenario.md)** ⭐
  EU Deforestation Regulation (EUDR) compliance using EO data for geospatial non-deforestation proof. Build Digital Product Passports (DPP) with verifiable credentials. Automated compliance verification with Climate TRACE integration. Meet 2025 EUDR requirements with cryptographic proof.

### 🛡️ Insurance

**Traditional Insurance:**
- **[Insurance Claims and Verification](insurance-claims-scenario.md)** ⭐
  Complete insurance claims verification system. Demonstrates claim credentials, damage assessment, repair verification, and fraud prevention.

**Parametric Insurance:**
- **[Parametric Insurance with Earth Observation](parametric-insurance-eo-scenario.md)** ⭐
  Build parametric insurance systems using EO data credentials. Solve the "Oracle Problem" by enabling standardized, multi-provider data ecosystems. Accept data from any certified provider (ESA, Planet, NASA) without custom integrations. Prevents replay attacks and data corruption for $50M+ payouts.

- **[Parametric Insurance for Travel Disruptions](parametric-insurance-travel-scenario.md)** ⭐
  Build parametric travel insurance system similar to Chubb Travel Pro using TrustWeave. Automatic payouts for flight delays, weather guarantees, baggage delays, and medical emergencies. Accept data from multiple providers (airlines, IATA, weather services) without custom integrations. Prevents fraud and enables quick payouts.

- **[Smart Contract: Parametric Insurance](smart-contract-parametric-insurance-scenario.md)** ⭐
  Parametric insurance using TrustWeave Smart Contracts abstraction. Demonstrates contract lifecycle, automatic execution based on EO data triggers, verifiable credentials, and blockchain anchoring. Complete workflow from contract creation to automatic payout.

### 🔌 IoT & Devices

**Device Identity & Management:**
- **[IoT Device Identity](iot-device-identity-scenario.md)** ⭐
  Connected device onboarding and identity management. Provision devices, rotate keys, and secure telemetry feeds.

- **[IoT Device Ownership Transfer](iot-device-ownership-transfer-scenario.md)** ⭐
  Secure device ownership transfer with complete audit trail. Transfer authorization, previous owner revocation, and ownership history tracking.

**Sensor Data & Provenance:**
- **[IoT Sensor Data Provenance & Integrity](iot-sensor-data-provenance-scenario.md)** ⭐
  Verify sensor data authenticity and integrity. Sensor attestation, data provenance, calibration tracking, and tamper detection.

**Firmware & Updates:**
- **[IoT Firmware Update Verification](iot-firmware-update-verification-scenario.md)** ⭐
  Verify firmware authenticity and update authorization. Firmware attestation, update policies, version control, and rollback support.

### 📰 Media & Content

- **[News Industry](news-industry-scenario.md)** ⭐
  Content provenance and authenticity verification for news media. Anchor articles, trace updates, and verify journalistic sources.

- **[Event Ticketing and Access Control](event-ticketing-scenario.md)** ⭐
  Verifiable event tickets with transfer control and access verification. Prevent fraud and scalping with cryptographic proof.

### 💾 Data Management

- **[Data Catalog & DCAT](data-catalog-dcat-scenario.md)** ⭐
  Verifiable data catalog system using DCAT for government and enterprise. Metadata provenance and catalog management.

### 🔒 Cybersecurity & Compliance

**Compliance:**
- **[SOC2 Compliance](soc2-compliance-scenario.md)** ⭐
  Build SOC2 Type II compliant systems using TrustWeave. Immutable audit trails with blockchain anchoring, access control with verifiable credentials, key rotation with history preservation, change management, and automated compliance reporting. Demonstrates how to achieve SOC2 certification with verifiable proof.

**Security:**
- **[Software Supply Chain Security](software-supply-chain-security-scenario.md)** ⭐
  Software provenance, build attestation, and SBOM verification. Prevent supply chain attacks with cryptographic proof of software authenticity and dependency verification.

- **[Security Training & Certification Verification](security-training-certification-scenario.md)** ⭐
  Instant verification of security certifications (CISSP, CEH, Security+, etc.) and training credentials. Privacy-preserving verification for HR and compliance.

## How to Use These Scenarios

1. **Start with Quick Start** – If you haven't already, complete the [Quick Start Guide](../getting-started/quick-start.md) to understand the basics.

2. **Choose Your Scenario** – Pick a scenario that matches your use case or domain.

3. **Run the Example** – Each scenario includes a complete, runnable `main()` function you can copy and execute.

4. **Customize** – Adapt the examples to your specific requirements.

5. **Explore Patterns** – See [Common Patterns](../getting-started/common-patterns.md) for reusable code patterns.

## Scenario Structure

Each scenario follows this structure:

```kotlin
fun main() = runBlocking {
    // 1. Setup
    val TrustWeave = TrustWeave.create()

    // 2. Create DIDs
    import com.trustweave.trust.types.DidCreationResult
    import com.trustweave.trust.types.IssuanceResult
    import com.trustweave.trust.types.WalletCreationResult
    
    val issuerDidResult = trustWeave.createDid { method("key") }
    val issuerDid = when (issuerDidResult) {
        is DidCreationResult.Success -> issuerDidResult.did
        else -> throw IllegalStateException("Failed to create issuer DID: ${issuerDidResult.reason}")
    }
    
    val holderDidResult = trustWeave.createDid { method("key") }
    val holderDid = when (holderDidResult) {
        is DidCreationResult.Success -> holderDidResult.did
        else -> throw IllegalStateException("Failed to create holder DID: ${holderDidResult.reason}")
    }

    // 3. Issue Credential
    val issuanceResult = trustWeave.issue {
        credential {
            issuer(issuerDid.value)
            subject { id(holderDid.value) }
            issued(Instant.now())
        }
        signedBy(issuerDid = issuerDid.value, keyId = "key-1")
    }
    
    val credential = when (issuanceResult) {
        is IssuanceResult.Success -> issuanceResult.credential
        else -> throw IllegalStateException("Failed to issue credential: ${issuanceResult.reason}")
    }

    // 4. Store in Wallet
    val walletResult = trustWeave.wallet {
        holder(holderDid.value)
    }
    
    val wallet = when (walletResult) {
        is WalletCreationResult.Success -> walletResult.wallet
        else -> throw IllegalStateException("Failed to create wallet: ${walletResult.reason}")
    }
    val credentialId = wallet.store(credential)

    // 5. Verify
    val verification = TrustWeave.verifyCredential(credential).getOrThrow()

    // 6. Advanced features (scenario-specific)
    // ...
}
```

## Prerequisites

- Java 21+
- Kotlin 2.2.21+
- TrustWeave dependency (see [Installation](../getting-started/installation.md))

## Related Documentation

- [Quick Start](../getting-started/quick-start.md) – Get started with TrustWeave
- [Common Patterns](../getting-started/common-patterns.md) – Reusable code patterns
- [API Reference](../api-reference/core-api.md) – Complete API documentation
- [Core Concepts](../core-concepts/README.md) – Fundamental concepts
- [Troubleshooting](../getting-started/troubleshooting.md) – Common issues and solutions

## Contributing Scenarios

Have a use case that's not covered? See [Contributing](../contributing/README.md) for guidelines on creating new scenarios.
