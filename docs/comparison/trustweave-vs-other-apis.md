---
title: "TrustWeave vs. Other APIs: Comprehensive Comparison"
---

# TrustWeave vs. Other APIs: Comprehensive Comparison

This document provides a detailed comparison of TrustWeave with other decentralized identity and verifiable credentials APIs, including WaltId and equivalent libraries in other programming languages.

## Overview

| Framework/Library | Language | Primary Focus | License | Maintainer |
|-------------------|----------|---------------|---------|------------|
| **TrustWeave** | Kotlin | Neutral, reusable trust and identity core | Dual (AGPL v3.0 / Commercial) | Geoknoesis LLC |
| **WaltId** | Kotlin/Java | Identity wallet and SSI infrastructure | Apache 2.0 | walt.id |
| **Veramo** | TypeScript/JavaScript | Modular framework for verifiable data | Apache 2.0 | uPort/Veramo |
| **did-jwt** | TypeScript/JavaScript | DID and JWT operations | Apache 2.0 | uPort |
| **didkit** | Rust | DID and VC toolkit | Apache 2.0 | Spruce Systems |
| **didkit-python** | Python | Python bindings for didkit | Apache 2.0 | Spruce Systems |
| **go-did** | Go | DID operations in Go | MIT | Various |
| **did-java** | Java | DID operations for Java | Apache 2.0 | Various |

## Detailed Feature Comparison

### Core Architecture & Design Philosophy

| Feature | TrustWeave | WaltId | Veramo | didkit | did-jwt | did-java | go-did |
|---------|------------|--------|--------|--------|---------|----------|--------|
| **Architecture** | Modular, plugin-based | Modular, service-oriented | Plugin-based framework | Library-based | Library-based | Library-based | Library-based |
| **Design Philosophy** | Domain-agnostic, chain-agnostic, DID-method-agnostic, KMS-agnostic | Wallet-first, SSI infrastructure | Modular, extensible | Standards-compliant toolkit | JWT-focused | Standards-compliant | Minimal, focused |
| **Type Safety** | ✅ Strong (Kotlin type system) | ✅ Strong (Kotlin/Java) | ✅ Strong (TypeScript) | ✅ Strong (Rust) | ⚠️ Moderate (TypeScript) | ✅ Strong (Java) | ⚠️ Moderate (Go) |
| **Async Model** | Coroutines (suspend functions) | Coroutines/CompletableFuture | Promises/async-await | Async/await | Promises/async-await | CompletableFuture | Goroutines |
| **Plugin System** | ✅ SPI-based plugins | ✅ Service-based | ✅ Plugin architecture | ❌ | ❌ | ❌ | ❌ |
| **Testability** | ✅ Comprehensive test utilities | ✅ Test implementations | ✅ Test utilities | ✅ Unit tests | ✅ Unit tests | ✅ Unit tests | ✅ Unit tests |

### DID Methods Support

| DID Method | TrustWeave | WaltId | Veramo | didkit | did-jwt | did-java | go-did |
|-------------|------------|--------|--------|--------|---------|----------|--------|
| **did:key** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **did:web** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **did:ion** | ✅ | ✅ | ✅ | ✅ | ❌ | ⚠️ Partial | ❌ |
| **did:ethr** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ Partial |
| **did:polygon** | ✅ | ⚠️ Partial | ⚠️ Partial | ❌ | ❌ | ❌ | ❌ |
| **did:sol** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **did:peer** | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| **did:jwk** | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ Partial | ⚠️ Partial |
| **did:ens** | ✅ | ❌ | ⚠️ Partial | ❌ | ❌ | ❌ | ❌ |
| **did:plc** | ✅ | ❌ | ⚠️ Partial | ❌ | ❌ | ❌ | ❌ |
| **did:cheqd** | ✅ | ⚠️ Partial | ⚠️ Partial | ❌ | ❌ | ❌ | ❌ |
| **Extensibility** | ✅ Pluggable via interfaces | ✅ Pluggable | ✅ Pluggable | ⚠️ Limited | ❌ | ⚠️ Limited | ⚠️ Limited |

### Verifiable Credentials Support

| Feature | TrustWeave | WaltId | Veramo | didkit | did-jwt | did-java | go-did |
|---------|------------|--------|--------|--------|---------|----------|--------|
| **W3C VC Compliance** | ✅ v1.1 | ✅ v1.1 | ✅ v1.1 | ✅ v1.1 | ⚠️ Partial | ✅ v1.1 | ⚠️ Partial |
| **Credential Issuance** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ Partial |
| **Credential Verification** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ Partial |
| **Credential Revocation** | ✅ (Status Lists) | ✅ | ✅ | ✅ | ⚠️ Limited | ⚠️ Limited | ❌ |
| **Status List Support** | ✅ (Blockchain-anchored) | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| **Selective Disclosure** | ✅ | ✅ | ✅ | ✅ (BBS+) | ❌ | ❌ | ❌ |
| **Credential Presentations** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ Partial |
| **Proof Types** | Ed25519, BBS+, JWT | Ed25519, BBS+, JWT | Ed25519, BBS+, JWT, EIP712 | Ed25519, BBS+, JWT | JWT, EIP712 | JWT, Ed25519 | JWT, Ed25519 |

### Blockchain Support

| Feature | TrustWeave | WaltId | Veramo | didkit | did-jwt | did-java | go-did |
|---------|------------|--------|--------|--------|---------|----------|--------|
| **Blockchain Anchoring** | ✅ | ⚠️ Limited | ⚠️ Limited | ❌ | ❌ | ❌ | ❌ |
| **Ethereum** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ Partial |
| **Algorand** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Polygon** | ✅ | ⚠️ Partial | ⚠️ Partial | ❌ | ❌ | ❌ | ❌ |
| **Base** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Arbitrum** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Chain Agnostic** | ✅ (CAIP-2) | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Pluggable Chains** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Smart Contracts** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |

### Key Management Service (KMS) Support

| KMS Provider | TrustWeave | WaltId | Veramo | didkit | did-jwt | did-java | go-did |
|--------------|------------|--------|--------|--------|---------|----------|--------|
| **In-Memory** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **AWS KMS** | ✅ | ❌ | ✅ | ❌ | ❌ | ⚠️ Partial | ❌ |
| **Azure Key Vault** | ✅ | ❌ | ⚠️ Partial | ❌ | ❌ | ⚠️ Partial | ❌ |
| **Google Cloud KMS** | ✅ | ❌ | ⚠️ Partial | ❌ | ❌ | ❌ | ❌ |
| **HashiCorp Vault** | ✅ | ❌ | ✅ | ❌ | ❌ | ⚠️ Partial | ❌ |
| **WaltId KMS** | ✅ (via plugin) | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Pluggable KMS** | ✅ | ⚠️ Limited | ✅ | ❌ | ❌ | ⚠️ Limited | ❌ |

### Wallet Support

| Feature | TrustWeave | WaltId | Veramo | didkit | did-jwt | did-java | go-did |
|---------|------------|--------|--------|--------|---------|----------|--------|
| **Wallet Creation** | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Credential Storage** | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Wallet Providers** | InMemory, Database, FileSystem, S3 | InMemory, Database | InMemory, Database | N/A | N/A | N/A | N/A |
| **Organization Features** | ✅ (Collections, Tags) | ⚠️ Limited | ⚠️ Limited | N/A | N/A | N/A | N/A |
| **Presentation Support** | ✅ | ✅ | ✅ | N/A | N/A | N/A | N/A |

### Smart Contracts

| Feature | TrustWeave | WaltId | Veramo | didkit | did-jwt | did-java | go-did |
|---------|------------|--------|--------|--------|---------|----------|--------|
| **Contract Drafting** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Contract Binding** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Contract Execution** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Parametric Contracts** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |

### Standards Compliance

| Standard | TrustWeave | WaltId | Veramo | didkit | did-jwt | did-java | go-did |
|----------|------------|--------|--------|--------|---------|----------|--------|
| **W3C DID Core** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **W3C VC Data Model v1.1** | ✅ | ✅ | ✅ | ✅ | ⚠️ Partial | ✅ | ⚠️ Partial |
| **JSON-LD** | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ⚠️ Partial |
| **CAIP-2** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Multibase** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **DIDComm** | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |

### Development Experience

| Aspect | TrustWeave | WaltId | Veramo | didkit | did-jwt | did-java | go-did |
|---------|------------|--------|--------|--------|---------|----------|--------|
| **Documentation** | ✅ Comprehensive | ✅ Good | ✅ Good | ✅ Good | ⚠️ Moderate | ⚠️ Moderate | ⚠️ Limited |
| **API Reference** | ✅ Complete | ✅ Good | ✅ Good | ✅ Good | ⚠️ Moderate | ⚠️ Moderate | ⚠️ Limited |
| **Examples** | ✅ Extensive | ✅ Good | ✅ Good | ✅ Good | ⚠️ Moderate | ⚠️ Moderate | ⚠️ Limited |
| **Type Safety** | ✅ Excellent | ✅ Excellent | ✅ Excellent | ✅ Excellent | ✅ Good | ✅ Excellent | ⚠️ Moderate |
| **Error Handling** | ✅ Typed errors | ✅ Typed errors | ✅ Typed errors | ✅ Result types | ⚠️ Exceptions | ✅ Exceptions | ⚠️ Errors |
| **DSL Support** | ✅ | ⚠️ Limited | ⚠️ Limited | ❌ | ❌ | ❌ | ❌ |

### Performance & Scalability

| Aspect | TrustWeave | WaltId | Veramo | didkit | did-jwt | did-java | go-did |
|---------|------------|--------|--------|--------|---------|----------|--------|
| **Concurrency Model** | Coroutines | Coroutines/Threads | Event Loop | Async | Event Loop | Threads | Goroutines |
| **Memory Efficiency** | ✅ Good | ✅ Good | ⚠️ Moderate | ✅ Excellent | ✅ Good | ✅ Good | ✅ Excellent |
| **Network Efficiency** | ✅ Good | ✅ Good | ✅ Good | ✅ Good | ✅ Good | ✅ Good | ✅ Excellent |
| **Caching Support** | ✅ | ✅ | ✅ | ⚠️ Limited | ❌ | ⚠️ Limited | ❌ |

### Use Cases & Target Audience

| Use Case | TrustWeave | WaltId | Veramo | didkit | did-jwt | did-java | go-did |
|----------|------------|--------|--------|--------|---------|----------|--------|
| **Enterprise Applications** | ✅ Excellent | ✅ Good | ✅ Good | ⚠️ Moderate | ⚠️ Moderate | ✅ Good | ⚠️ Moderate |
| **Mobile Applications** | ✅ (Kotlin Multiplatform) | ✅ (Android) | ✅ (React Native) | ⚠️ Limited | ✅ | ✅ | ⚠️ Limited |
| **Web Applications** | ✅ (Kotlin/JS) | ✅ | ✅ | ✅ | ✅ | ⚠️ Limited | ⚠️ Limited |
| **Blockchain Integration** | ✅ Excellent | ⚠️ Limited | ⚠️ Limited | ❌ | ⚠️ Limited | ⚠️ Limited | ⚠️ Limited |
| **IoT Applications** | ✅ | ⚠️ Limited | ⚠️ Limited | ✅ | ⚠️ Limited | ✅ | ✅ |
| **Server-Side Applications** | ✅ Excellent | ✅ Excellent | ✅ Good | ✅ Good | ✅ Good | ✅ Excellent | ✅ Excellent |

## Key Differentiators

### TrustWeave

**Strengths:**
- True Agnosticism**: Domain-agnostic, chain-agnostic, DID-method-agnostic, KMS-agnostic
- Blockchain Anchoring**: Comprehensive blockchain support with smart contract capabilities
- Type Safety**: Leverages Kotlin's strong type system
- Coroutines**: Modern async/await patterns
- Extensibility**: SPI-based plugin system
- Smart Contracts**: Built-in contract drafting, binding, and execution
- CAIP-2 Compliance**: Chain-agnostic blockchain identifiers

**Best For:**
- Applications requiring blockchain anchoring and smart contracts
- Multi-chain deployments
- Enterprise applications needing pluggable KMS
- Kotlin/JVM ecosystems
- Applications requiring domain-agnostic trust infrastructure

### WaltId

**Strengths:**
- Wallet-First**: Strong wallet and credential management
- SSI Infrastructure**: Comprehensive SSI tooling
- DIDComm Support**: Messaging protocol support
- Kotlin/Java**: Strong type safety

**Best For:**
- Wallet applications
- SSI infrastructure projects
- Applications requiring DIDComm messaging
- Kotlin/Java ecosystems

### Veramo

**Strengths:**
- Modular Architecture**: Plugin-based framework
- TypeScript/JavaScript**: Web-first development
- DIDComm Support**: Messaging protocol support
- Active Community**: Large ecosystem

**Best For:**
- Web applications
- Node.js/TypeScript projects
- Applications requiring DIDComm
- JavaScript/TypeScript ecosystems

### didkit

**Strengths:**
- Rust Performance**: Excellent performance and memory safety
- Standards Compliance**: Strong W3C compliance
- Multi-Language Bindings**: Python, JavaScript, etc.
- Security**: Memory-safe implementation

**Best For:**
- Performance-critical applications
- Rust ecosystems
- Security-sensitive applications
- Cross-language integrations

### did-jwt

**Strengths:**
- JWT Focus**: Specialized for JWT-based credentials
- Lightweight**: Minimal dependencies
- Web-Friendly**: JavaScript/TypeScript

**Best For:**
- JWT-based credential systems
- Lightweight applications
- Web applications
- Simple use cases

## Summary Table: Quick Reference

| Criteria | TrustWeave | WaltId | Veramo | didkit | did-jwt |
|---------|------------|--------|--------|--------|---------|
| **Language** | Kotlin | Kotlin/Java | TypeScript/JS | Rust | TypeScript/JS |
| **License** | Dual (AGPL/Commercial) | Apache 2.0 | Apache 2.0 | Apache 2.0 | Apache 2.0 |
| **Blockchain Anchoring** | ✅ Excellent | ⚠️ Limited | ⚠️ Limited | ❌ | ❌ |
| **Smart Contracts** | ✅ | ❌ | ❌ | ❌ | ❌ |
| **DID Methods** | ✅ 10+ | ✅ 5+ | ✅ 8+ | ✅ 5+ | ⚠️ 3+ |
| **KMS Plugins** | ✅ 5+ | ⚠️ Limited | ✅ 3+ | ❌ | ❌ |
| **Wallet Support** | ✅ | ✅ | ✅ | ❌ | ❌ |
| **Type Safety** | ✅ Excellent | ✅ Excellent | ✅ Excellent | ✅ Excellent | ✅ Good |
| **Plugin System** | ✅ SPI-based | ✅ Service-based | ✅ Plugin-based | ❌ | ❌ |
| **Best For** | Enterprise, Multi-chain, Smart Contracts | Wallet, SSI Infrastructure | Web, DIDComm | Performance, Security | JWT, Lightweight |

## Conclusion

**Choose TrustWeave if:**
- You need blockchain anchoring and smart contract capabilities
- You require true agnosticism (chain, DID method, KMS)
- You're building enterprise applications in Kotlin/JVM
- You need domain-agnostic trust infrastructure
- You require comprehensive plugin extensibility

**Choose WaltId if:**
- You're building wallet applications
- You need SSI infrastructure tooling
- You require DIDComm messaging
- You're in the Kotlin/Java ecosystem

**Choose Veramo if:**
- You're building web applications
- You need DIDComm messaging
- You prefer TypeScript/JavaScript
- You want a modular, plugin-based framework

**Choose didkit if:**
- You need maximum performance
- You're building in Rust
- You require memory safety
- You need cross-language bindings

**Choose did-jwt if:**
- You're focused on JWT-based credentials
- You need a lightweight solution
- You're building simple web applications

---

*Last Updated: 2025-01-XX*
*TrustWeave Version: 0.6.0*

