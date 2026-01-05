---
title: Core Concepts
nav_order: 20
keywords:
  - core concepts
  - dids
  - verifiable credentials
  - wallets
  - blockchain
  - key management
---

# Core Concepts

Welcome to TrustWeave's core concepts! This section introduces the fundamental building blocks of decentralized identity and trust systems.

> **New to TrustWeave?** Start with the [Mental Model](../introduction/mental-model.md) guide to understand how TrustWeave works at a conceptual level before diving into specific concepts.

## What You'll Learn

- **Identifiers and Types** - Type-safe identifiers and domain types for better code safety
- **Decentralized Identifiers (DIDs)** - How to create and manage decentralized identities
- **Verifiable Credentials (VCs)** - How to issue, store, and verify credentials
- **Wallets** - How to manage credentials and identities
- **Blockchain Anchoring** - How to anchor data to blockchains
- **Smart Contracts** - How to create and execute executable agreements
- **Key Management** - How to manage cryptographic keys securely

## Table of Contents

1. [Identifiers and Types](identifiers-and-types.md) - Type-safe identifiers and domain types in TrustWeave
2. [Decentralized Identifiers (DIDs)](dids.md) - Understanding DIDs and DID Documents
3. [Verifiable Credentials](verifiable-credentials.md) - Understanding VCs and their lifecycle
4. [Wallets](wallets.md) - Understanding credential and identity wallets
5. [Blockchain Anchoring](blockchain-anchoring.md) - Understanding data anchoring
6. [Smart Contracts](smart-contracts.md) - Understanding executable agreements with verifiable credentials
7. [Blockchain-Anchored Revocation](blockchain-anchored-revocation.md) - Understanding revocation with blockchain anchoring
8. [Key Management](key-management.md) - Understanding key management systems
9. [Algorithm Compatibility Table](algorithm-compatibility-table.md) - Algorithm support in DIDs, VCs, AWS KMS, and Azure Key Vault
10. [JSON Canonicalization](json-canonicalization.md) - Understanding data integrity
11. [Credential Exchange Protocols](credential-exchange-protocols.md) - Protocol abstraction layer for credential exchange (DIDComm, OIDC4VCI, CHAPI)

## Quick Overview

### Decentralized Identifiers (DIDs)

A **DID** is a self-sovereign identifier that you control. Unlike traditional identifiers (like email addresses), DIDs are:

- **Decentralized**: No central authority controls them
- **Persistent**: They don't change when you switch providers
- **Cryptographically verifiable**: You can prove ownership with cryptographic keys

Example: `did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK`

### Verifiable Credentials (VCs)

A **Verifiable Credential** is a tamper-evident credential that follows the W3C VC Data Model. VCs contain:

- **Claims**: The actual data (e.g., "name: Alice", "age: 30")
- **Proof**: Cryptographic proof of who issued it
- **Metadata**: Issuer, issuance date, expiration, etc.

Example: A university diploma, driver's license, or professional certification.

### Wallets

A **Wallet** is a secure container for managing your credentials and identities. TrustWeave wallets support:

- **Credential Storage**: Store and organize verifiable credentials
- **Organization**: Collections, tags, and metadata
- **Lifecycle Management**: Archive and refresh credentials
- **Presentation Creation**: Create verifiable presentations
- **Identity Management**: Manage DIDs and keys (optional)

### Blockchain Anchoring

**Blockchain Anchoring** provides tamper-proof timestamps and integrity verification by storing data references on blockchains. This enables:

- **Provenance**: Prove when data was created
- **Integrity**: Detect if data has been tampered with
- **Immutability**: Create permanent records

### Smart Contracts

**Smart Contracts** are executable agreements between parties that combine:

- **Verifiable Identity**: Parties identified by DIDs
- **Cryptographic Proof**: Contract terms wrapped in Verifiable Credentials
- **Immutable Audit Trail**: Blockchain anchoring for tamper-proof records
- **Pluggable Execution**: Parametric, conditional, scheduled, event-driven, or manual execution

Example: Parametric insurance contracts that automatically pay out based on EO data triggers.

### Key Management

**Key Management** involves securely generating, storing, and using cryptographic keys for:

- **Signing**: Creating proofs for credentials
- **Verification**: Verifying proofs from others
- **Encryption**: Protecting sensitive data

See the [Algorithm Compatibility Table](algorithm-compatibility-table.md) for a comprehensive comparison of algorithm support across DIDs, VCs, AWS KMS, and Azure Key Vault.

## Next Steps

**New to TrustWeave?**
- [Introduction](../introduction/README.md) - Start here to understand what TrustWeave is
- [Mental Model](../introduction/mental-model.md) - Understand how concepts fit together
- [Getting Started](../getting-started/README.md) - Installation and quick start

**Ready to learn concepts?**
- Start with [Identifiers and Types](identifiers-and-types.md) to understand the type system
- Then learn about [Decentralized Identifiers (DIDs)](dids.md) to understand identity
- Learn about [Verifiable Credentials](verifiable-credentials.md) for credentials
- Explore [Wallets](wallets.md) for credential management
- Check out [Blockchain Anchoring](blockchain-anchoring.md) for data integrity

**Want hands-on practice?**
- [Tutorials](../tutorials/README.md) - Step-by-step guides
- [Wallet API Tutorial](../tutorials/wallet-api-tutorial.md) - Hands-on wallet examples
- [Use Case Scenarios](../scenarios/README.md) - Real-world examples

