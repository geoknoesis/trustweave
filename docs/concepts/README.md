---
title: Concepts
nav_exclude: true
keywords:
  - concepts
  - understanding
  - fundamentals
  - dids
  - verifiable credentials
  - wallets
  - architecture
---

# Concepts

Welcome to TrustWeave's Concepts section! This section explains **what** TrustWeave concepts are and **why** they exist, helping you understand the fundamental building blocks of decentralized identity and trust systems.

## What are Concepts?

Concepts explain the fundamental ideas, principles, and mental models behind TrustWeave. They help you understand:

- **What** each component is
- **Why** it exists and what problem it solves
- **How** components relate to each other
- **When** to use different approaches

## How Concepts Relate to Other Documentation

- **Concepts** (this section) - Understanding the "why" and "what"
- **How-To Guides** - Practical "how-to" instructions for tasks
- **Tutorials** - Step-by-step learning experiences
- **API Reference** - Complete method and parameter documentation

## Learning Path Through Concepts

### Start Here: Core Concepts

Begin with these fundamental concepts that form the foundation of TrustWeave:

1. **[Decentralized Identifiers (DIDs)](../core-concepts/dids.md)** - Understanding decentralized identity
2. **[Verifiable Credentials](../core-concepts/verifiable-credentials.md)** - Understanding tamper-evident credentials
3. **[Wallets](../core-concepts/wallets.md)** - Understanding credential storage and management
4. **[Key Management](../core-concepts/key-management.md)** - Understanding cryptographic key management
5. **[Trust Registry](../core-concepts/trust-registry.md)** - Understanding trust relationships

### Next: Advanced Concepts

Once you understand the basics, explore these advanced concepts:

- **[Blockchain Anchoring](../core-concepts/blockchain-anchoring.md)** - Understanding data integrity and provenance
- **[Blockchain-Anchored Revocation](../core-concepts/blockchain-anchored-revocation.md)** - Understanding credential revocation
- **[Smart Contracts](../core-concepts/smart-contracts.md)** - Understanding executable agreements
- **[Delegation](../core-concepts/delegation.md)** - Understanding authority delegation
- **[Credential Exchange Protocols](../core-concepts/credential-exchange-protocols.md)** - Understanding protocol abstractions

### Deep Dive: Technical Details

For implementation details and technical specifications:

- **[JSON Canonicalization](../core-concepts/json-canonicalization.md)** - Understanding data integrity
- **[Proof Purpose Validation](../core-concepts/proof-purpose-validation.md)** - Understanding proof validation
- **[Evaluation Engines](../core-concepts/evaluation-engines.md)** - Understanding credential evaluation
- **[Algorithm Compatibility](../core-concepts/algorithm-compatibility-table.md)** - Understanding algorithm support

### Architecture: System Design

Understand how TrustWeave is designed and organized:

- **[Architecture Overview](../introduction/architecture-overview.md)** - High-level system architecture
- **[Mental Model](../introduction/mental-model.md)** - How components fit together
- **[Web of Trust](../introduction/web-of-trust.md)** - Understanding trust relationships

## Concept Relationships

Understanding how concepts relate to each other helps you use TrustWeave effectively:

```
DIDs
  └─> Key Management (DIDs need keys)
  └─> Verifiable Credentials (DIDs identify issuers/holders)
      └─> Wallets (Credentials are stored in wallets)
      └─> Blockchain Anchoring (Credentials can be anchored)
          └─> Blockchain-Anchored Revocation (Anchoring enables revocation)
      └─> Smart Contracts (Credentials bind contracts)
```

## Quick Reference

### Core Building Blocks

- **DIDs** - Decentralized identifiers for entities
- **Verifiable Credentials** - Tamper-evident attestations
- **Wallets** - Secure credential storage
- **Keys** - Cryptographic keys for signing and verification

### Trust Mechanisms

- **Trust Registry** - Managing trust relationships
- **Delegation** - Delegating authority between DIDs
- **Web of Trust** - Distributed trust networks

### Data Integrity

- **Blockchain Anchoring** - Immutable data references
- **JSON Canonicalization** - Deterministic data representation
- **Proof Purpose Validation** - Verifying proof intent

## When to Read Concepts

**Read concepts when you want to:**
- Understand why something works the way it does
- Learn about design decisions and trade-offs
- Explore different approaches to solving problems
- Deepen your understanding of decentralized identity

**Use How-To Guides when you want to:**
- Complete a specific task quickly
- See practical examples
- Get step-by-step instructions

## Related Documentation

- **[How-To Guides](../how-to/README.md)** - Practical task-oriented guides
- **[Tutorials](../tutorials/README.md)** - Step-by-step learning experiences
- **[API Reference](../api-reference/README.md)** - Complete API documentation
- **[Use Case Scenarios](../scenarios/README.md)** - Real-world examples

## Next Steps

**New to TrustWeave?**
- Start with [Decentralized Identifiers (DIDs)](../core-concepts/dids.md)
- Then read [Verifiable Credentials](../core-concepts/verifiable-credentials.md)
- Explore [Architecture Overview](../introduction/architecture-overview.md)

**Ready to build?**
- [How-To Guides](../how-to/README.md) - Practical guides for tasks
- [Tutorials](../tutorials/README.md) - Hands-on learning

