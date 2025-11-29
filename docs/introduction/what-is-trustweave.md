---
title: What is TrustWeave?
nav_order: 2
parent: Introduction
keywords:
  - what is trustweave
  - introduction
  - overview
  - purpose
  - design philosophy
---

# What is TrustWeave?

> **Version:** 1.0.0-SNAPSHOT
> TrustWeave is created and supported by [Geoknoesis LLC](https://www.geoknoesis.com). The project reflects Geoknoesis' reference architecture for decentralized trust.

TrustWeave is a **neutral, reusable trust and identity core** library for Kotlin, designed to be:

- **Domain-agnostic**: No application-specific logic in core modules
- **Chain-agnostic**: Pluggable blockchain adapters via interfaces
- **Decentralized Identifier (DID)-method-agnostic**: Pluggable DID methods via interfaces
- **Key Management Service (KMS)-agnostic**: Pluggable key management backends

## Purpose

TrustWeave provides a modular architecture for building trust and identity systems that can be used across multiple contexts:

- **Earth Observation (EO) catalogues** - Verifying data provenance and integrity
- **Spatial Web Nodes** - Decentralized spatial data management
- **Agentic / Large Language Model (LLM)-based platforms** - Identity and trust for AI agents
- **Any application requiring decentralized identity and trust**

## Design Philosophy

TrustWeave follows these core principles:

1. **Neutrality**: Core modules contain no domain-specific or chain-specific logic
2. **Pluggability**: All external dependencies (blockchains, Decentralized Identifier (DID) methods, Key Management Service (KMS)) are pluggable via interfaces
3. **Coroutines**: All I/O operations use Kotlin coroutines for async/await patterns
4. **Type Safety**: Leverages Kotlinx Serialization for type-safe JSON handling
5. **Testability**: Provides test implementations for all interfaces

## Why TrustWeave?

Traditional identity and trust systems are often tightly coupled to specific technologies, making them difficult to reuse across different domains and platforms. TrustWeave solves this by:

- Providing **abstractions** that work across different blockchain networks
- Supporting **multiple Decentralized Identifier (DID) methods** through a unified interface
- Enabling **flexible key management** strategies
- Maintaining **domain neutrality** so you can build your own domain logic on top

## What TrustWeave Is Not

- **Not a full identity solution**: TrustWeave provides building blocks, not a complete identity system
- **Not a blockchain**: TrustWeave works with existing blockchains, it doesn't create new ones
- **Not a wallet**: TrustWeave manages keys through pluggable Key Management Service (KMS), but doesn't provide wallet UI/UX
- **Not domain-specific**: TrustWeave is intentionally domain-agnostic to maximize reusability

## Next Steps

**New to TrustWeave?**
- [Key Features](key-features.md) - Explore the main capabilities
- [Use Cases](use-cases.md) - See how TrustWeave is used in practice
- [Architecture Overview](architecture-overview.md) - Understand the modular design
- [Mental Model](mental-model.md) - Core concepts and how they fit together

**Ready to get started?**
- [Getting Started](../getting-started/README.md) - Installation and quick start
- [Quick Start](../getting-started/quick-start.md) - Create your first credential in 5 minutes

