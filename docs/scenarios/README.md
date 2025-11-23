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

## Available Scenarios

Scenarios are organized by domain and use case. Each scenario includes a complete, runnable example with industry context and best practices.

### 🔐 Cybersecurity & Access Control

**Authentication & Authorization:**
- **[Zero Trust Continuous Authentication](zero-trust-authentication-scenario.md)** ⭐  
  Continuous authentication system without traditional sessions. Short-lived credentials, device attestation, risk-based access control, and continuous re-authentication.

- **[Security Clearance & Access Control](security-clearance-access-control-scenario.md)** ⭐  
  Privacy-preserving security clearance verification for classified systems. Multi-level access control (Top Secret, Secret, Confidential) with selective disclosure.

- **[Biometric Verification](biometric-verification-scenario.md)** ⭐  
  Complete biometric verification system with fingerprint, face, and voice. Privacy-preserving biometric templates with liveness detection and multi-modal verification.

**Security Training & Compliance:**
- **[Security Training & Certification Verification](security-training-certification-scenario.md)** ⭐  
  Instant verification of security certifications (CISSP, CEH, Security+, etc.) and training credentials. Privacy-preserving verification for HR and compliance.

- **[SOC2 Compliance](soc2-compliance-scenario.md)** ⭐  
  SOC2 Type II compliance with immutable audit trails, access control, key management, and automated reporting. See also [Compliance & Security](#compliance--security) section.

**Software Security:**
- **[Software Supply Chain Security](software-supply-chain-security-scenario.md)** ⭐  
  Software provenance, build attestation, and SBOM verification. Prevent supply chain attacks with cryptographic proof of software authenticity and dependency verification.

### 🆔 Identity & Verification

**Digital Identity:**
- **[Government Digital Identity](government-digital-identity-scenario.md)** ⭐  
  Citizens receive, store, and present official IDs. Complete workflow for government-issued digital identity.

- **[Professional Identity](professional-identity-scenario.md)** ⭐  
  Professional credential wallet management. Demonstrates storing and organizing professional certifications and licenses.

**Age & Identity Verification:**
- **[Age Verification](age-verification-scenario.md)** ⭐  
  Privacy-preserving age verification for age-restricted services. Verify age without revealing personal information. Includes photo association for face verification.

**Financial Services:**
- **[Financial Services (KYC)](financial-services-kyc-scenario.md)** ⭐  
  Streamline onboarding and reuse credentials across institutions. KYC/AML compliance workflows.

### 🎓 Education & Credentials

- **[Academic Credentials](academic-credentials-scenario.md)** ⭐  
  University credential issuance and verification. Demonstrates degree credentials, transcript management, and wallet organization.

- **[National Education Credentials Algeria](national-education-credentials-algeria-scenario.md)** ⭐  
  AlgeroPass national-level education credential system. Shows how to build a national credential infrastructure.

### 💼 Employment & Professional

- **[Employee Onboarding and Background Verification](employee-onboarding-scenario.md)** ⭐  
  Complete employee onboarding system with credential verification. Demonstrates education, work history, certification, and background check credentials.

### 🏥 Healthcare

- **[Healthcare Medical Records](healthcare-medical-records-scenario.md)** ⭐  
  Share consented medical data across providers with audit trails. Patient credential workflows and privacy-preserving data sharing.

- **[Vaccination and Health Passports](vaccination-health-passport-scenario.md)** ⭐  
  Vaccination credential issuance and health passport verification. Privacy-preserving health credentials for travel and access control.

### 📦 Supply Chain & Provenance

**Physical Supply Chain:**
- **[Supply Chain Traceability](supply-chain-traceability-scenario.md)** ⭐  
  Follow goods from origin to shelf with verifiable checkpoints. Complete supply chain provenance tracking.

- **[Supply Chain & EUDR Compliance](supply-chain-eudr-compliance-scenario.md)** ⭐  
  EU Deforestation Regulation (EUDR) compliance with EO data. Digital Product Passports (DPP) using verifiable credentials. Automated compliance verification for 2025 EUDR requirements. See also [Earth Observation & Climate Applications](#earth-observation--climate-applications) section.

**Digital Provenance:**
- **[Digital Workflow & Provenance](digital-workflow-provenance-scenario.md)** ⭐  
  PROV-O workflow provenance tracking for digital information. Track information flow through complex workflows.

### 📍 Geospatial & Location

- **[Proof of Location](proof-of-location-scenario.md)** ⭐  
  Capture sensor evidence, notarise location, and present proofs. Geospatial location proofs and verification.

- **[Earth Observation](earth-observation-scenario.md)** ⭐  
  Manage satellite imagery provenance with digest anchoring. Complete EO data integrity workflow with Linksets.

- **[Spatial Web Authorization](spatial-web-authorization-scenario.md)** ⭐  
  Grant AR/VR permissions using verifiable policies. DID-based authorization for spatial entities.

### 🌍 Earth Observation & Climate Applications

**Parametric Insurance:**
- **[Parametric Insurance with Earth Observation](parametric-insurance-eo-scenario.md)** ⭐  
  Build parametric insurance systems using EO data credentials. Solve the "Oracle Problem" by enabling standardized, multi-provider data ecosystems. Accept data from any certified provider (ESA, Planet, NASA) without custom integrations. Prevents replay attacks and data corruption for $50M+ payouts.

**Carbon Markets:**
- **[Carbon Markets & Digital MRV (dMRV)](carbon-markets-dmrv-scenario.md)** ⭐  
  Digital Measurement, Reporting, and Verification for carbon markets using EO data. Prevent double counting with blockchain-anchored credentials. Track carbon credit lifecycle from issuance to retirement. Supports nested climate accounting (Nation → City → Company) and Token Taxonomy Framework (TTF) integration.

**Regulatory Compliance:**
- **[Supply Chain & EUDR Compliance](supply-chain-eudr-compliance-scenario.md)** ⭐  
  EU Deforestation Regulation (EUDR) compliance using EO data for geospatial non-deforestation proof. Build Digital Product Passports (DPP) with verifiable credentials. Automated compliance verification with Climate TRACE integration. Meet 2025 EUDR requirements with cryptographic proof.

### 📰 Media & Content

- **[News Industry](news-industry-scenario.md)** ⭐  
  Content provenance and authenticity verification for news media. Anchor articles, trace updates, and verify journalistic sources.

- **[Event Ticketing and Access Control](event-ticketing-scenario.md)** ⭐  
  Verifiable event tickets with transfer control and access verification. Prevent fraud and scalping with cryptographic proof.

### 💾 Data Management

- **[Data Catalog & DCAT](data-catalog-dcat-scenario.md)** ⭐  
  Verifiable data catalog system using DCAT for government and enterprise. Metadata provenance and catalog management.

### 🔌 IoT & Devices

**Device Identity & Management:**
- **[IoT Device Identity](iot-device-identity-scenario.md)** ⭐  
  Connected device onboarding and identity management. Provision devices, rotate keys, and secure telemetry feeds.

**Sensor Data & Provenance:**
- **[IoT Sensor Data Provenance & Integrity](iot-sensor-data-provenance-scenario.md)** ⭐  
  Verify sensor data authenticity and integrity. Sensor attestation, data provenance, calibration tracking, and tamper detection.

**Firmware & Updates:**
- **[IoT Firmware Update Verification](iot-firmware-update-verification-scenario.md)** ⭐  
  Verify firmware authenticity and update authorization. Firmware attestation, update policies, version control, and rollback support.

**Ownership & Transfer:**
- **[IoT Device Ownership Transfer](iot-device-ownership-transfer-scenario.md)** ⭐  
  Secure device ownership transfer with complete audit trail. Transfer authorization, previous owner revocation, and ownership history tracking.

### 🤝 Trust & Reputation

- **[Web of Trust](web-of-trust-scenario.md)** ⭐  
  Build trust networks and reputation systems. Demonstrate how entities establish and verify trust relationships.

### 🛡️ Insurance & Claims

- **[Insurance Claims and Verification](insurance-claims-scenario.md)** ⭐  
  Complete insurance claims verification system. Demonstrates claim credentials, damage assessment, repair verification, and fraud prevention.

- **[Parametric Insurance for Travel Disruptions](parametric-insurance-travel-scenario.md)** ⭐  
  Build parametric travel insurance system similar to Chubb Travel Pro using TrustWeave. Automatic payouts for flight delays, weather guarantees, baggage delays, and medical emergencies. Accept data from multiple providers (airlines, IATA, weather services) without custom integrations. Prevents fraud and enables quick payouts.

- **[Parametric Insurance with Earth Observation](parametric-insurance-eo-scenario.md)** ⭐  
  Parametric insurance using EO data credentials. Standardized data oracle system accepting data from multiple providers (ESA, Planet, NASA). Prevents replay attacks and data corruption. See also [Earth Observation & Climate Applications](#earth-observation--climate-applications) section.

- **[Smart Contract: Parametric Insurance](smart-contract-parametric-insurance-scenario.md)** ⭐  
  Parametric insurance using TrustWeave Smart Contracts abstraction. Demonstrates contract lifecycle, automatic execution based on EO data triggers, verifiable credentials, and blockchain anchoring. Complete workflow from contract creation to automatic payout.

### 🔒 Compliance & Security

- **[SOC2 Compliance](soc2-compliance-scenario.md)** ⭐  
  Build SOC2 Type II compliant systems using TrustWeave. Immutable audit trails with blockchain anchoring, access control with verifiable credentials, key rotation with history preservation, change management, and automated compliance reporting. Demonstrates how to achieve SOC2 certification with verifiable proof.

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
    val issuerDid = TrustWeave.dids.create()
    val holderDid = TrustWeave.dids.create()
    
    // 3. Issue Credential
    val credential = TrustWeave.issueCredential(...).getOrThrow()
    
    // 4. Store in Wallet
    val wallet = TrustWeave.createWallet(holderDid.id).getOrThrow()
    val credentialId = wallet.store(credential)
    
    // 5. Verify
    val verification = TrustWeave.verifyCredential(credential).getOrThrow()
    
    // 6. Advanced features (scenario-specific)
    // ...
}
```

## Prerequisites

- Java 21+
- Kotlin 2.2.0+
- TrustWeave dependency (see [Installation](../getting-started/installation.md))

## Related Documentation

- [Quick Start](../getting-started/quick-start.md) – Get started with TrustWeave
- [Common Patterns](../getting-started/common-patterns.md) – Reusable code patterns
- [API Reference](../api-reference/core-api.md) – Complete API documentation
- [Core Concepts](../core-concepts/README.md) – Fundamental concepts
- [Troubleshooting](../getting-started/troubleshooting.md) – Common issues and solutions

## Contributing Scenarios

Have a use case that's not covered? See [Contributing](../contributing/README.md) for guidelines on creating new scenarios.

