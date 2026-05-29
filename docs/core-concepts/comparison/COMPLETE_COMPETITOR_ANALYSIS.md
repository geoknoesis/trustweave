---
title: TrustWeave vs. Competitors - Complete Analysis
redirect_from:
  - /comparison/COMPLETE_COMPETITOR_ANALYSIS/
parent: Comparison
grand_parent: Core Concepts
---

# TrustWeave vs. Competitors - Complete Analysis

> **Comprehensive comparison of TrustWeave with all relevant competitors and main players in the SSI/DID/VC ecosystem**

**Last Updated:** 2025-01-15
**TrustWeave Version:** 0.6.0

---

## Executive Summary

TrustWeave competes in the Self-Sovereign Identity (SSI), Decentralized Identifier (DID), and Verifiable Credentials (VC) space against both open-source SDKs and commercial platforms. This document provides a comprehensive comparison across all major competitors.

**Key Differentiators:**
- **Only platform with built-in Smart Contract capabilities**
- Most comprehensive blockchain support** (6+ chains with CAIP-2)
- Most extensive KMS integration** (10+ enterprise providers)
- Unique Trust Domain SaaS concept** (no-code trust management)
- Most feature-rich SDK** (24+ features vs. competitors' 5-10)

---

## Main Competitors Overview

| **Competitor** | **Type** | **Language** | **License** | **Maintainer** | **Primary Focus** | **DID Methods** | **Blockchain** | **KMS** | **Smart Contracts** | **Wallet** | **SaaS** | **DIDComm** | **OIDC4VCI** |
|---------------|----------|--------------|-------------|----------------|-------------------|-----------------|----------------|---------|---------------------|------------|----------|-------------|--------------|
| **TrustWeave** | SDK + SaaS | Kotlin | Dual (AGPL/Commercial) | Geoknoesis LLC | Neutral, reusable trust core | ✅ 11+ | ✅ 6+ chains | ✅ 10+ | ✅ | ✅ | ✅ (Planned) | ❌ | ✅ |
| **WaltId** | SDK | Kotlin/Java | Apache 2.0 | walt.id | Wallet-first SSI infrastructure | ✅ 5+ | ⚠️ Limited | ⚠️ Limited | ❌ | ✅ | ❌ | ✅ | ✅ |
| **Veramo** | SDK | TypeScript/JS | Apache 2.0 | uPort/Veramo | Modular verifiable data framework | ✅ 8+ | ⚠️ Limited | ✅ 3+ | ❌ | ✅ | ❌ | ✅ | ✅ |
| **didkit** | SDK | Rust | Apache 2.0 | Spruce Systems | Standards-compliant toolkit | ✅ 5+ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ⚠️ Partial |
| **did-jwt** | Library | TypeScript/JS | Apache 2.0 | uPort | JWT-based credentials | ⚠️ 3+ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **did-java** | Library | Java | Apache 2.0 | Various | DID operations for Java | ⚠️ 3+ | ❌ | ⚠️ Limited | ❌ | ❌ | ❌ | ❌ | ❌ |
| **go-did** | Library | Go | MIT | Various | Minimal DID operations | ⚠️ 3+ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Affinidi** | Platform | Multi | Commercial | Affinidi | Enterprise SSI platform | ✅ | ⚠️ Limited | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ |
| **Trinsic** | Platform | Multi | Commercial | Trinsic | Enterprise SSI platform | ✅ | ⚠️ Limited | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ |
| **Evernym/Indicio** | Platform | Multi | Commercial | Indicio | Enterprise SSI platform | ✅ | ❌ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ |
| **Spruce ID** | SDK/Platform | Rust/TypeScript | Apache 2.0 | Spruce Systems | Identity toolkit | ✅ 5+ | ⚠️ Limited | ⚠️ Limited | ❌ | ⚠️ Limited | ⚠️ Limited | ✅ | ✅ |
| **Microsoft ION** | Network | TypeScript | MIT | Microsoft | Decentralized identity network | ✅ (did:ion) | ✅ (Bitcoin) | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Hyperledger Indy** | Framework | Python/Java | Apache 2.0 | Linux Foundation | Distributed ledger for identity | ✅ (did:sov) | ✅ (Indy ledger) | ✅ | ❌ | ✅ | ❌ | ✅ | ⚠️ Partial |
| **Aries Framework** | Framework | Python/JS/Go | Apache 2.0 | Linux Foundation | DIDComm messaging framework | ✅ | ⚠️ Limited | ✅ | ❌ | ✅ | ❌ | ✅ | ⚠️ Partial |
| **MATTR** | Platform | Multi | Commercial | MATTR | Enterprise digital identity | ✅ | ⚠️ Limited | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ |
| **Bloom** | Platform | Multi | Commercial | Bloom | Consumer identity platform | ✅ | ⚠️ Limited | ✅ | ❌ | ✅ | ✅ | ❌ | ⚠️ Partial |
| **Sovrin** | Network | Multi | Apache 2.0 | Sovrin Foundation | Self-sovereign identity network | ✅ (did:sov) | ✅ (Sovrin ledger) | ✅ | ❌ | ✅ | ❌ | ✅ | ⚠️ Partial |
| **Cheqd** | Network | TypeScript | Apache 2.0 | Cheqd | Payment-enabled identity network | ✅ (did:cheqd) | ✅ (Cosmos) | ⚠️ Limited | ❌ | ⚠️ Limited | ❌ | ⚠️ Limited | ⚠️ Limited |
| **Polygon ID** | Platform | TypeScript | Apache 2.0 | Polygon | Zero-knowledge identity | ✅ (did:polygon) | ✅ (Polygon) | ⚠️ Limited | ⚠️ ZK proofs | ✅ | ✅ | ⚠️ Limited | ⚠️ Limited |
| **Ontology** | Network | Java/Go | Apache 2.0 | Ontology | Blockchain identity network | ✅ (did:ont) | ✅ (Ontology) | ✅ | ⚠️ Limited | ✅ | ❌ | ⚠️ Limited | ❌ |
| **Civic** | Platform | Multi | Commercial | Civic | Identity verification platform | ✅ | ⚠️ Limited | ✅ | ❌ | ✅ | ✅ | ❌ | ⚠️ Partial |
| **SelfKey** | Platform | Multi | Commercial | SelfKey | Self-sovereign identity wallet | ✅ | ⚠️ Limited | ✅ | ❌ | ✅ | ✅ | ❌ | ❌ |
| **uPort** | SDK | TypeScript | Apache 2.0 | uPort | Mobile identity SDK | ✅ | ⚠️ Limited | ⚠️ Limited | ❌ | ✅ | ❌ | ✅ | ⚠️ Partial |
| **Jolocom** | SDK | TypeScript | Apache 2.0 | Jolocom | Self-sovereign identity SDK | ✅ | ⚠️ Limited | ⚠️ Limited | ❌ | ✅ | ❌ | ✅ | ⚠️ Partial |
| **DIF Identity Hub** | Spec/Impl | TypeScript | Apache 2.0 | DIF | Decentralized storage spec | ✅ | ❌ | ❌ | ❌ | ⚠️ Limited | ❌ | ⚠️ Limited | ❌ |

**Legend:**
- Full Support
- Partial/Limited Support
- Not Supported

---

## Detailed Feature Comparison

### Core SDK Features

| **Feature** | **TrustWeave** | **WaltId** | **Veramo** | **didkit** | **Affinidi** | **Trinsic** | **Hyperledger Indy** |
|------------|---------------|------------|------------|------------|--------------|-------------|---------------------|
| **DID Methods** | ✅ 11+ | ✅ 5+ | ✅ 8+ | ✅ 5+ | ✅ Multiple | ✅ Multiple | ✅ did:sov |
| **Blockchain Anchoring** | ✅ 6+ chains | ⚠️ Limited | ⚠️ Limited | ❌ | ⚠️ Limited | ⚠️ Limited | ✅ Indy ledger |
| **Smart Contracts** | ✅ Full lifecycle | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **KMS Providers** | ✅ 10+ | ⚠️ Limited | ✅ 3+ | ❌ | ✅ Multiple | ✅ Multiple | ✅ |
| **Wallet** | ✅ Full-featured | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ |
| **Trust Registry** | ✅ Web of Trust | ⚠️ Limited | ⚠️ Limited | ❌ | ⚠️ Limited | ⚠️ Limited | ✅ |
| **Delegation** | ✅ Multi-hop | ⚠️ Limited | ⚠️ Limited | ❌ | ⚠️ Limited | ⚠️ Limited | ✅ |
| **Audit Logging** | ✅ | ⚠️ Limited | ⚠️ Limited | ❌ | ✅ | ✅ | ✅ |
| **Metrics/Telemetry** | ✅ | ❌ | ⚠️ Limited | ❌ | ✅ | ✅ | ⚠️ Limited |
| **QR Codes** | ✅ | ⚠️ Limited | ⚠️ Limited | ❌ | ✅ | ✅ | ⚠️ Limited |
| **Notifications** | ✅ Push/Webhooks | ⚠️ Limited | ⚠️ Limited | ❌ | ✅ | ✅ | ❌ |
| **Versioning** | ✅ | ❌ | ❌ | ❌ | ⚠️ Limited | ⚠️ Limited | ❌ |
| **Backup/Recovery** | ✅ | ⚠️ Limited | ⚠️ Limited | ❌ | ✅ | ✅ | ⚠️ Limited |
| **Expiration Management** | ✅ | ❌ | ❌ | ❌ | ⚠️ Limited | ⚠️ Limited | ❌ |
| **Credential Rendering** | ✅ HTML/PDF | ⚠️ Limited | ⚠️ Limited | ❌ | ✅ | ✅ | ❌ |
| **OIDC4VCI** | ✅ | ✅ | ✅ | ⚠️ Partial | ✅ | ✅ | ⚠️ Partial |
| **DIDComm v2** | ❌ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ (v1) |
| **CHAPI** | ✅ | ❌ | ❌ | ❌ | ⚠️ Limited | ⚠️ Limited | ❌ |
| **Multi-Party Issuance** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Analytics** | ✅ | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ |
| **Health Checks** | ✅ | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ |

### SaaS/Platform Features

| **Feature** | **TrustWeave Cloud** | **Affinidi** | **Trinsic** | **MATTR** | **Evernym/Indicio** | **Polygon ID** |
|-------------|---------------------|-------------|-------------|-----------|---------------------|----------------|
| **Managed Service** | ✅ (Planned) | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Trust Domain Management** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **No-Code Trust Anchor Mgmt** | ✅ | ⚠️ Limited | ⚠️ Limited | ⚠️ Limited | ⚠️ Limited | ❌ |
| **Template System** | ✅ | ⚠️ Limited | ⚠️ Limited | ⚠️ Limited | ⚠️ Limited | ❌ |
| **Trust Network Visualization** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Visual Policy Builder** | ✅ | ⚠️ Limited | ⚠️ Limited | ⚠️ Limited | ⚠️ Limited | ❌ |
| **Bulk Import/Export** | ✅ | ⚠️ Limited | ⚠️ Limited | ⚠️ Limited | ⚠️ Limited | ❌ |
| **Team Collaboration** | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ Limited |
| **REST API** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Webhooks** | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ Limited |
| **SSO Integration** | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ Limited |
| **API Key Management** | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ Limited |
| **Compliance Reports** | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ Limited |

### Blockchain Support

| **Blockchain** | **TrustWeave** | **WaltId** | **Veramo** | **didkit** | **Polygon ID** | **Hyperledger Indy** |
|----------------|---------------|------------|------------|------------|----------------|---------------------|
| **Ethereum** | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| **Polygon** | ✅ | ⚠️ Partial | ⚠️ Partial | ❌ | ✅ | ❌ |
| **Base** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Arbitrum** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Algorand** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Bitcoin** | ⚠️ (via ION) | ⚠️ (via ION) | ⚠️ (via ION) | ⚠️ (via ION) | ❌ | ❌ |
| **Solana** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Indy Ledger** | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ |
| **Sovrin Ledger** | ⚠️ (via Indy) | ❌ | ❌ | ❌ | ❌ | ✅ |
| **Cosmos/Cheqd** | ✅ | ⚠️ Partial | ⚠️ Partial | ❌ | ❌ | ❌ |
| **CAIP-2 Support** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |

### Enterprise Features

| **Feature** | **TrustWeave** | **Affinidi** | **Trinsic** | **MATTR** | **Evernym/Indicio** |
|-------------|---------------|-------------|-------------|-----------|---------------------|
| **Enterprise KMS** | ✅ 10+ providers | ✅ Multiple | ✅ Multiple | ✅ Multiple | ✅ Multiple |
| **SOC2 Compliance** | ✅ (Planned) | ✅ | ✅ | ✅ | ✅ |
| **HIPAA Compliance** | ✅ (Planned) | ✅ | ✅ | ✅ | ✅ |
| **GDPR Compliance** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **SLA Guarantees** | ✅ (Planned) | ✅ | ✅ | ✅ | ✅ |
| **Dedicated Support** | ✅ (Planned) | ✅ | ✅ | ✅ | ✅ |
| **On-Premise Option** | ✅ (Planned) | ⚠️ Limited | ⚠️ Limited | ⚠️ Limited | ✅ |
| **White Labeling** | ✅ (Planned) | ✅ | ✅ | ✅ | ⚠️ Limited |

### DID Methods Support

| **DID Method** | **TrustWeave** | **WaltId** | **Veramo** | **didkit** | **Affinidi** | **Trinsic** |
|----------------|---------------|------------|------------|------------|--------------|-------------|
| **did:key** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **did:web** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **did:ion** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **did:ethr** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **did:polygon** | ✅ | ⚠️ Partial | ⚠️ Partial | ❌ | ✅ | ✅ |
| **did:sol** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **did:peer** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **did:jwk** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **did:ens** | ✅ | ❌ | ⚠️ Partial | ❌ | ⚠️ Limited | ⚠️ Limited |
| **did:plc** | ✅ | ❌ | ⚠️ Partial | ❌ | ❌ | ❌ |
| **did:cheqd** | ✅ | ⚠️ Partial | ⚠️ Partial | ❌ | ⚠️ Limited | ⚠️ Limited |
| **did:sov** | ✅ (via Indy) | ❌ | ❌ | ❌ | ✅ | ✅ |
| **did:ont** | ⚠️ Limited | ❌ | ❌ | ❌ | ❌ | ❌ |

### KMS Provider Support

| **KMS Provider** | **TrustWeave** | **WaltId** | **Veramo** | **Affinidi** | **Trinsic** |
|------------------|---------------|------------|------------|--------------|-------------|
| **AWS KMS** | ✅ | ❌ | ✅ | ✅ | ✅ |
| **Azure Key Vault** | ✅ | ❌ | ⚠️ Partial | ✅ | ✅ |
| **Google Cloud KMS** | ✅ | ❌ | ⚠️ Partial | ✅ | ✅ |
| **HashiCorp Vault** | ✅ | ❌ | ✅ | ✅ | ✅ |
| **Thales CipherTrust** | ✅ | ❌ | ❌ | ⚠️ Limited | ⚠️ Limited |
| **CyberArk Conjur** | ✅ | ❌ | ❌ | ⚠️ Limited | ⚠️ Limited |
| **IBM Key Protect** | ✅ | ❌ | ❌ | ⚠️ Limited | ⚠️ Limited |
| **Fortanix DSM** | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Entrust nShield** | ✅ | ❌ | ❌ | ❌ | ❌ |
| **WaltId KMS** | ✅ (via plugin) | ✅ | ❌ | ❌ | ❌ |
| **In-Memory** | ✅ | ✅ | ✅ | ✅ | ✅ |

---

## Competitive Analysis by Category

### 1. Open Source SDKs

#### TrustWeave
**Strengths:**
- Most comprehensive feature set (24+ features)
- Only SDK with Smart Contract capabilities
- Best blockchain support (6+ chains, CAIP-2)
- Most extensive KMS integration (10+ providers)
- Strong type safety (Kotlin)
- Domain-agnostic design

**Weaknesses:**
- No DIDComm v2 support (yet)
- Smaller community than Veramo
- TypeScript SDK not yet available

**Best For:** Enterprise applications, blockchain integration, multi-chain deployments, smart contracts

#### WaltId
**Strengths:**
- Wallet-first architecture
- DIDComm support
- Kotlin/Java ecosystem
- Good SSI infrastructure tooling

**Weaknesses:**
- Limited blockchain support
- Limited KMS providers
- No smart contracts

**Best For:** Wallet applications, SSI infrastructure, Kotlin/Java projects

#### Veramo
**Strengths:**
- Large community
- TypeScript/JavaScript ecosystem
- DIDComm support
- Modular plugin architecture
- Good web support

**Weaknesses:**
- Limited blockchain support
- Limited KMS providers
- No smart contracts

**Best For:** Web applications, Node.js projects, TypeScript/JavaScript ecosystems

#### didkit (Spruce Systems)
**Strengths:**
- Rust performance
- Memory safety
- Cross-language bindings
- Strong standards compliance

**Weaknesses:**
- No blockchain support
- No KMS support
- No wallet support
- No DIDComm

**Best For:** Performance-critical applications, Rust ecosystems, security-sensitive use cases

### 2. Commercial SaaS Platforms

#### Affinidi
**Strengths:**
- Mature SaaS platform
- Enterprise features
- Good compliance (SOC2, HIPAA)
- Multiple DID methods
- DIDComm support

**Weaknesses:**
- Limited blockchain support
- No smart contracts
- Less flexible than open-source SDKs

**Best For:** Enterprises needing managed service, compliance requirements

#### Trinsic
**Strengths:**
- Mature SaaS platform
- Enterprise features
- Good compliance
- Multiple DID methods
- DIDComm support

**Weaknesses:**
- Limited blockchain support
- No smart contracts
- Vendor lock-in concerns

**Best For:** Enterprises needing managed service, quick deployment

#### MATTR
**Strengths:**
- Enterprise-focused
- Good compliance
- Multiple DID methods
- DIDComm support

**Weaknesses:**
- Limited blockchain support
- No smart contracts
- Higher pricing

**Best For:** Large enterprises, government, regulated industries

#### Evernym/Indicio
**Strengths:**
- Hyperledger Indy integration
- DIDComm support
- Enterprise features
- On-premise option

**Weaknesses:**
- Tied to Indy ledger
- No smart contracts
- Limited blockchain support

**Best For:** Organizations committed to Hyperledger Indy ecosystem

### 3. Blockchain-Native Platforms

#### Polygon ID
**Strengths:**
- Zero-knowledge proofs
- Polygon blockchain integration
- Managed service available
- Good for privacy-preserving credentials

**Weaknesses:**
- Limited to Polygon ecosystem
- Limited DID methods
- No smart contracts (beyond ZK)

**Best For:** Privacy-focused applications, Polygon ecosystem

#### Cheqd
**Strengths:**
- Payment-enabled identity
- Cosmos blockchain
- Open source

**Weaknesses:**
- Limited ecosystem
- Limited features
- No smart contracts

**Best For:** Payment-enabled identity use cases, Cosmos ecosystem

### 4. Network/Infrastructure

#### Hyperledger Indy
**Strengths:**
- Mature network
- Enterprise adoption
- DIDComm support
- Trust registry

**Weaknesses:**
- Tied to Indy ledger
- No smart contracts
- Limited blockchain interoperability

**Best For:** Organizations needing distributed ledger for identity

#### Microsoft ION
**Strengths:**
- Bitcoin anchoring
- Microsoft backing
- Decentralized network

**Weaknesses:**
- Limited to did:ion
- No wallet/credential management
- No smart contracts

**Best For:** Applications needing Bitcoin-anchored DIDs

---

## TrustWeave Competitive Position

### Unique Advantages

1. **Smart Contracts** - Only platform with built-in contract drafting, binding, and execution
2. **Multi-Chain Blockchain** - Most comprehensive blockchain support (6+ chains) with CAIP-2 compliance
3. **Enterprise KMS** - Most extensive KMS integration (10+ providers)
4. **Trust Domain SaaS** - Unique no-code trust management concept
5. **Feature Plugins** - Most comprehensive feature set (24+ features)
6. **Domain-Agnostic** - True neutrality across domains, chains, DID methods, KMS

### Competitive Gaps

1. **DIDComm v2** - Not yet implemented (WaltId, Veramo lead)
2. **Community Size** - Smaller than Veramo/Spruce (growing)
3. **SaaS Maturity** - Planned (Affinidi, Trinsic are live)
4. **TypeScript SDK** - Planned (Veramo leads in web ecosystem)

### Market Positioning

**SDK Market:**
- **Strengths:** Blockchain, smart contracts, enterprise KMS
- **Position:** Best for enterprise, multi-chain, blockchain-native applications
- **Competitors:** WaltId (wallet focus), Veramo (web focus), didkit (performance focus)

**SaaS Market:**
- **Strengths:** Unique Trust Domain concept, comprehensive features
- **Position:** First-to-market with no-code trust management
- **Competitors:** Affinidi, Trinsic (mature platforms), MATTR (enterprise focus)

**Enterprise Market:**
- **Strengths:** Comprehensive features, extensive KMS support, smart contracts
- **Position:** Competitive on features, needs compliance certifications
- **Competitors:** Affinidi, Trinsic, MATTR, Evernym (all have compliance)

---

## Recommendations

### For Developers Choosing a Solution

**Choose TrustWeave if:**
- You need blockchain anchoring and smart contract capabilities
- You require multi-chain support
- You need extensive KMS integration
- You're building in Kotlin/JVM ecosystem
- You need domain-agnostic trust infrastructure
- You want the most feature-rich SDK

**Choose WaltId if:**
- You're building wallet applications
- You need DIDComm messaging
- You're in Kotlin/Java ecosystem
- You need SSI infrastructure tooling

**Choose Veramo if:**
- You're building web applications
- You prefer TypeScript/JavaScript
- You need DIDComm messaging
- You want large community support

**Choose Affinidi/Trinsic if:**
- You need managed SaaS platform
- You require immediate compliance (SOC2, HIPAA)
- You want quick deployment without infrastructure management
- You don't need smart contracts

**Choose didkit if:**
- You need maximum performance
- You're building in Rust
- You require memory safety
- You need cross-language bindings

---

## Feature Count Summary

| **Category** | **TrustWeave** | **WaltId** | **Veramo** | **didkit** | **Affinidi** | **Trinsic** |
|-------------|---------------|------------|------------|------------|--------------|-------------|
| **Core Features** | 10 | 6 | 7 | 3 | 8 | 8 |
| **Feature Plugins** | 14 | 2 | 3 | 0 | 5 | 5 |
| **DID Methods** | 11+ | 5+ | 8+ | 5+ | Multiple | Multiple |
| **Blockchains** | 6+ | 1-2 | 1-2 | 0 | 1-2 | 1-2 |
| **KMS Providers** | 10+ | 1-2 | 3+ | 0 | Multiple | Multiple |
| **Total Features** | **24+** | **8+** | **10+** | **3+** | **13+** | **13+** |

---

## Conclusion

TrustWeave offers the most comprehensive feature set in the SSI/DID/VC ecosystem, with unique capabilities in smart contracts, multi-chain blockchain support, and enterprise KMS integration. While it has gaps in DIDComm v2 and SaaS maturity, its feature richness and domain-agnostic design position it well for enterprise and blockchain-native applications.

**Key Takeaways:**
1. **TrustWeave leads in:** Smart contracts, blockchain support, KMS integration, feature richness
2. **TrustWeave lags in:** DIDComm v2, community size, SaaS maturity
3. **Market opportunity:** Unique Trust Domain SaaS concept, blockchain-native positioning
4. **Competitive advantage:** Only platform combining smart contracts + comprehensive SSI features

---

**Document Version:** 1.0
**Last Updated:** 2025-01-15
**Maintained By:** Geoknoesis LLC




