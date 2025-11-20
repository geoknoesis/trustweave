# Core Concepts

Welcome to VeriCore's core concepts! This section introduces the fundamental building blocks of decentralized identity and trust systems.

## What You'll Learn

- **Decentralized Identifiers (DIDs)** - How to create and manage decentralized identities
- **Verifiable Credentials (VCs)** - How to issue, store, and verify credentials
- **Wallets** - How to manage credentials and identities
- **Blockchain Anchoring** - How to anchor data to blockchains
- **Key Management** - How to manage cryptographic keys securely

## Table of Contents

1. [Decentralized Identifiers (DIDs)](dids.md) - Understanding DIDs and DID Documents
2. [Verifiable Credentials](verifiable-credentials.md) - Understanding VCs and their lifecycle
3. [Wallets](wallets.md) - Understanding credential and identity wallets
4. [Blockchain Anchoring](blockchain-anchoring.md) - Understanding data anchoring
5. [Blockchain-Anchored Revocation](blockchain-anchored-revocation.md) - Understanding revocation with blockchain anchoring
6. [Key Management](key-management.md) - Understanding key management systems
7. [Algorithm Compatibility Table](algorithm-compatibility-table.md) - Algorithm support in DIDs, VCs, AWS KMS, and Azure Key Vault
8. [JSON Canonicalization](json-canonicalization.md) - Understanding data integrity

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

A **Wallet** is a secure container for managing your credentials and identities. VeriCore wallets support:

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

### Key Management

**Key Management** involves securely generating, storing, and using cryptographic keys for:

- **Signing**: Creating proofs for credentials
- **Verification**: Verifying proofs from others
- **Encryption**: Protecting sensitive data

See the [Algorithm Compatibility Table](algorithm-compatibility-table.md) for a comprehensive comparison of algorithm support across DIDs, VCs, AWS KMS, and Azure Key Vault.

## Next Steps

- Start with [Decentralized Identifiers (DIDs)](dids.md) to understand identity
- Then learn about [Verifiable Credentials](verifiable-credentials.md) for credentials
- Explore [Wallets](wallets.md) for credential management
- Check out the [Wallet API Tutorial](../tutorials/wallet-api-tutorial.md) for hands-on examples

