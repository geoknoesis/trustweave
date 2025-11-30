---
title: "Atlas Parametric MGA: Architecture Overview with TrustWeave"
---

# Atlas Parametric MGA: Architecture Overview with TrustWeave

> **How TrustWeave Powers Your Parametric Insurance MGA**

## Executive Summary

This document shows how **TrustWeave** serves as the trust and integrity foundation for your parametric insurance MGA platform. TrustWeave solves the "Oracle Problem" by providing standardized, verifiable EO data credentials that enable instant payouts with regulatory compliance.

## The Problem TrustWeave Solves

### Current Industry Challenge

**The Oracle Problem:**
- Each insurer builds custom API integrations for each EO data provider
- No standardized way to accept data from multiple providers (ESA, Planet, NASA, NOAA)
- Trust issues: Need cryptographic proof that data used for $50M payout is authentic
- Regulatory compliance: Need tamper-proof audit trails

### TrustWeave Solution

**Standardized EO Data Oracle:**
- ✅ Accept EO data from any certified provider using W3C Verifiable Credentials
- ✅ Cryptographic proof of data integrity prevents tampering
- ✅ Blockchain anchoring for tamper-proof audit trails
- ✅ Multi-provider support without custom integrations
- ✅ Automated trigger verification for instant payouts

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Atlas Parametric MGA Platform                   │
│                  "Instant Catastrophe Protection"                   │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┴───────────────┐
                    │                               │
        ┌───────────▼──────────┐        ┌──────────▼──────────┐
        │   EO Data Providers  │        │   Insurance          │
        │   (ESA, Planet,      │        │   Operations         │
        │    NASA, NOAA)       │        │                      │
        └───────────┬──────────┘        └──────────┬──────────┘
                    │                               │
                    │ Issues VC                     │ Verifies VC
                    │                               │
        ┌───────────▼───────────────────────────────▼──────────┐
        │         TrustWeave Trust Layer                         │
        │  ┌──────────────────────────────────────────────┐    │
        │  │  DID Management                              │    │
        │  │  - EO Provider DIDs                         │    │
        │  │  - Insurance Company DIDs                   │    │
        │  │  - Reinsurer DIDs                           │    │
        │  │  - Broker DIDs                              │    │
        │  └──────────────────────────────────────────────┘    │
        │  ┌──────────────────────────────────────────────┐    │
        │  │  Smart Contracts                             │    │
        │  │  - Parametric Insurance Contracts            │    │
        │  │  - Contract Lifecycle Management             │    │
        │  │  - Automatic Execution                       │    │
        │  └──────────────────────────────────────────────┘    │
        │  ┌──────────────────────────────────────────────┐    │
        │  │  Verifiable Credentials                      │    │
        │  │  - EO Data Credentials (SAR, NDVI, AOD, LST) │    │
        │  │  - Contract Credentials                      │    │
        │  │  - Payout Credentials                        │    │
        │  └──────────────────────────────────────────────┘    │
        │  ┌──────────────────────────────────────────────┐    │
        │  │  Blockchain Anchoring                        │    │
        │  │  - Algorand / Polygon                        │    │
        │  │  - Tamper-proof audit trails                 │    │
        │  │  - Regulatory compliance                     │    │
        │  └──────────────────────────────────────────────┘    │
        │  ┌──────────────────────────────────────────────┐    │
        │  │  Data Integrity Verification                │    │
        │  │  - Cryptographic digests                     │    │
        │  │  - Replay attack prevention                  │    │
        │  │  - Multi-source validation                   │    │
        │  └──────────────────────────────────────────────┘    │
        └───────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┴───────────────┐
                    │                               │
        ┌───────────▼──────────┐        ┌──────────▼──────────┐
        │   Trigger Engine     │        │   Payout            │
        │   - Threshold eval   │        │   Automation        │
        │   - Tier calculation │        │   - Banking API     │
        │   - Auto verification│        │   - KYC/AML         │
        └──────────────────────┘        └─────────────────────┘
```

## Product Implementation Map

### 1. SAR Flood Parametric

**TrustWeave Components:**
- **EO Data Credential**: Sentinel-1 SAR flood depth wrapped in VC
- **Blockchain Anchor**: Tamper-proof trigger record
- **Payout Credential**: Verifiable payout record

**Flow:**
```
Create Contract → Bind (VC + Anchor) → Activate →
Sentinel-1 SAR → EO Provider → Issues VC → Execute Contract →
Automatic Payout
```

### 2. Heatwave Parametric

**TrustWeave Components:**
- **EO Data Credential**: MODIS LST + ERA5 temperature data
- **Multi-Day Verification**: Consecutive days above threshold
- **Payout Credential**: Verifiable heatwave payout

**Flow:**
```
Create Contract → Bind (VC + Anchor) → Activate →
MODIS LST → EO Provider → Issues VC → Execute Contract →
Automatic Payout
```

### 3. Solar Attenuation Parametric

**TrustWeave Components:**
- **EO Data Credential**: AOD + Irradiance data
- **Attenuation Calculation**: Percentage drop verification
- **Payout Credential**: Verifiable solar payout

**Flow:**
```
Create Contract → Bind (VC + Anchor) → Activate →
AOD + Irradiance → EO Provider → Issues VC → Execute Contract →
Automatic Payout
```

## Key TrustWeave Features Used

### 1. DID Management

**Purpose**: Identity for all participants
```kotlin
// Create DIDs for EO providers, insurers, reinsurers
val eoProviderDid = trustWeave.createDid { method("key") }
val insuranceDid = trustWeave.createDid { method("key") }
```

### 2. Smart Contracts

**Purpose**: Executable agreements with automatic execution
```kotlin
// Create parametric insurance contract
val contract = trustWeave.contracts.draft(
    request = ContractDraftRequest(
        contractType = ContractType.Insurance,
        executionModel = ExecutionModel.Parametric(
            triggerType = TriggerType.EarthObservation,
            evaluationEngine = "parametric-insurance"
        ),
        parties = ContractParties(...),
        terms = ContractTerms(...)
    )
).getOrThrow()

// Bind contract (issues VC and anchors)
val bound = trustWeave.contracts.bindContract(
    contractId = contract.id,
    issuerDid = insurerDid,
    issuerKeyId = insurerKeyId
).getOrThrow()

// Activate contract
val active = trustWeave.contracts.activateContract(bound.contract.id).getOrThrow()
```

### 3. Verifiable Credentials

**Purpose**: Wrap EO data with cryptographic proof
```kotlin
// Issue EO data credential
val floodCredential = trustWeave.issue {
    credential {
        type("EarthObservationCredential", "InsuranceOracleCredential")
        issuer(eoProviderDid)
        subject {
            // Add floodData properties
            addClaims(floodData)
        }
        issued(Instant.now())
    }
    signedBy(issuerDid = eoProviderDid, keyId = eoProviderKeyId)
}
```

### 4. Contract Execution

**Purpose**: Automatically execute contracts based on EO data
```kotlin
// Execute contract with EO data
val result = trustWeave.contracts.executeContract(
    contract = active,
    executionContext = ExecutionContext(
        triggerData = buildJsonObject {
            put("floodDepthCm", 75.0)
            put("credentialId", floodCredential.id)
        }
    )
).getOrThrow()

if (result.executed) {
    // Process automatic payout
    processPayout(result)
}
```

### 5. Credential Verification

**Purpose**: Verify EO data before using for triggers
```kotlin
// Verify credential before trigger evaluation
val verification = trustWeave.verify {
    credential(floodCredential)
}
when (verification) {
    is VerificationResult.Valid -> {
        // Accept trigger
    }
    is VerificationResult.Invalid -> {
        // Reject trigger
    }
}
```

### 6. Blockchain Anchoring

**Purpose**: Tamper-proof audit trails
```kotlin
// Anchor trigger to blockchain
val anchorResult = trustWeave.blockchains.anchor(
    data = payoutCredential,
    chainId = "algorand:mainnet"
).getOrThrow()
```

### 7. Multi-Provider Support

**Purpose**: Accept EO data from any certified provider
```kotlin
// Accept data from ESA, Planet, NASA, NOAA
val eoData = acceptEoDataCredential(dataCredential)
// All providers use same VC format - no custom integrations!
```

## Business Value Delivered

### For Your MGA

1. **Cost Reduction**: Eliminate custom API integrations (80% cost savings)
2. **Speed to Market**: Launch products faster with standardized format
3. **Regulatory Compliance**: Blockchain-anchored audit trails
4. **Trust**: Cryptographic proof of data integrity
5. **Scalability**: Add new EO providers without code changes

### For Reinsurers

1. **Objective Triggers**: Verifiable EO data prevents disputes
2. **Low Moral Hazard**: Cryptographic proof prevents fraud
3. **Automated Underwriting**: Standardized data format
4. **Portfolio Analysis**: Rich EO data for risk modeling

### For Policyholders

1. **Transparency**: Verify data used for payouts
2. **Speed**: 24-72 hour payouts vs months
3. **Fairness**: Standardized data prevents manipulation
4. **Trust**: Cryptographic proof of integrity

## Implementation Roadmap

### Phase 1: MVP (Weeks 1-6)

**TrustWeave Setup:**
- ✅ Initialize TrustWeave with blockchain anchoring
- ✅ Create DIDs for EO providers
- ✅ Build SAR flood credential issuance
- ✅ Implement trigger verification

**Deliverables:**
- SAR flood product working
- EO data credentials issued
- Blockchain anchors created
- Basic trigger evaluation

### Phase 2: Production (Months 2-12)

**TrustWeave Enhancements:**
- ✅ Multi-provider EO data acceptance
- ✅ Heatwave product with LST credentials
- ✅ Solar attenuation product with AOD credentials
- ✅ Regulatory compliance features
- ✅ Reinsurer dashboard with VC verification

**Deliverables:**
- 3 products live
- Multi-provider support
- Regulatory compliance
- Reinsurer integration

### Phase 3: Scale (Months 12-24)

**TrustWeave Scale:**
- ✅ Hurricane product
- ✅ Drought/NDVI product
- ✅ Enterprise licensing
- ✅ Global expansion

## Competitive Advantage

### Why TrustWeave Gives You an Edge

1. **Only EO-First MGA**: Full-spectrum EO integration (SAR, NDVI, AOD, LST, InSAR)
2. **Standardized Format**: W3C-compliant VCs work with all providers
3. **Instant Verification**: Cryptographic proof enables 24-72 hour payouts
4. **Regulatory Ready**: Blockchain-anchored audit trails
5. **Multi-Provider**: Accept data from any certified provider

### vs. Competitors

| Feature | Atlas Parametric (TrustWeave) | FloodFlash | Arbol | Descartes |
|---------|----------------------------|------------|-------|-----------|
| EO-First Design | ✅ | ❌ | ❌ | ❌ |
| Multi-Provider Support | ✅ | ❌ | ❌ | ❌ |
| Blockchain Audit Trail | ✅ | ❌ | ❌ | ❌ |
| Standardized Format | ✅ | ❌ | ❌ | ❌ |
| Instant Verification | ✅ | ❌ | ❌ | ❌ |

## Technical Stack

### Core Platform
- **TrustWeave**: Trust and integrity foundation
- **Kotlin**: Primary language
- **Spring Boot**: API framework
- **PostgreSQL**: Policy and payout data

### EO Data Processing
- **Sentinel-1 SAR**: Flood detection
- **MODIS LST**: Heatwave detection
- **AOD + Irradiance**: Solar attenuation
- **NDVI**: Drought/agriculture

### Blockchain
- **Algorand** or **Polygon**: Anchoring (via TrustWeave)
- **Low Cost**: Anchor digests only, not full data

### Integration
- **Banking APIs**: Stripe, Plaid for payouts
- **KYC/AML**: Onfido, Jumio
- **Broker Portals**: React frontend

## Getting Started

### 1. Review Implementation Guide

See [Parametric Insurance MGA Implementation Guide](parametric-insurance-mga-implementation-guide.md) for complete code examples.

### 2. Explore TrustWeave

- [Quick Start](../getting-started/quick-start.md)
- [Parametric Insurance with EO Data](parametric-insurance-eo-scenario.md)
- [Earth Observation Scenario](earth-observation-scenario.md)

### 3. Start Building

```kotlin
// Initialize TrustWeave
val trustWeave = TrustWeave.build {
    factories(
        kmsFactory = TestkitKmsFactory(),
        didMethodFactory = TestkitDidMethodFactory()
    )
    keys { provider("inMemory"); algorithm("Ed25519") }
    did { method("key") { algorithm("Ed25519") } }
    blockchains {
        "algorand:mainnet" to AlgorandBlockchainAnchorClient(...)
    }
}

// Create EO provider DID
val eoProviderDid = trustWeave.createDid { method("key") }

// Resolve DID to get key ID
val resolution = trustWeave.resolveDid(eoProviderDid)
val issuerDoc = when (resolution) {
    is DidResolutionResult.Success -> resolution.document
    else -> throw IllegalStateException("Failed to resolve DID")
}
val eoProviderKeyId = issuerDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
    ?: throw IllegalStateException("No verification method found")

// Issue EO data credential
val floodCredential = trustWeave.issue {
    credential {
        type("EarthObservationCredential")
        issuer(eoProviderDid.value)
        subject {
            addClaims(floodData)
        }
        issued(Instant.now())
    }
    signedBy(issuerDid = eoProviderDid.value, keyId = eoProviderKeyId)
).getOrThrow()

// Anchor to blockchain
trustWeave.blockchains.anchor(
    data = floodCredential,
    serializer = VerifiableCredential.serializer(),
    chainId = "algorand:mainnet"
)
```

## Next Steps

1. **Read Implementation Guide**: [parametric-insurance-mga-implementation-guide.md](parametric-insurance-mga-implementation-guide.md)
2. **Review TrustWeave Docs**: [Getting Started](../getting-started/quick-start.md)
3. **Build MVP**: Start with SAR flood product
4. **Add Products**: Heatwave, solar attenuation
5. **Scale**: Multi-provider, global expansion

## Questions?

- **TrustWeave Documentation**: [docs/README.md](../README.md)
- **API Reference**: [API Reference](../api-reference/core-api.md)
- **Scenarios**: [Scenarios](README.md)

---

**Built with TrustWeave** - The Foundation for Decentralized Trust and Identity

