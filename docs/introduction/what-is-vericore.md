# What is VeriCore?

> VeriCore is created and supported by [Geoknoesis LLC](https://www.geoknoesis.com). The project reflects Geoknoesis’ reference architecture for decentralized trust.

VeriCore is a **neutral, reusable trust and identity core** library for Kotlin, designed to be:

- **Domain-agnostic**: No application-specific logic in core modules
- **Chain-agnostic**: Pluggable blockchain adapters via interfaces
- **DID-method-agnostic**: Pluggable DID methods via interfaces
- **KMS-agnostic**: Pluggable key management backends

## Purpose

VeriCore provides a modular architecture for building trust and identity systems that can be used across multiple contexts:

- **Earth Observation (EO) catalogues** - Verifying data provenance and integrity
- **Spatial Web Nodes** - Decentralized spatial data management
- **Agentic / LLM-based platforms** - Identity and trust for AI agents
- **Any application requiring decentralized identity and trust**

## Design Philosophy

VeriCore follows these core principles:

1. **Neutrality**: Core modules contain no domain-specific or chain-specific logic
2. **Pluggability**: All external dependencies (blockchains, DID methods, KMS) are pluggable via interfaces
3. **Coroutines**: All I/O operations use Kotlin coroutines for async/await patterns
4. **Type Safety**: Leverages Kotlinx Serialization for type-safe JSON handling
5. **Testability**: Provides test implementations for all interfaces

## Why VeriCore?

Traditional identity and trust systems are often tightly coupled to specific technologies, making them difficult to reuse across different domains and platforms. VeriCore solves this by:

- Providing **abstractions** that work across different blockchain networks
- Supporting **multiple DID methods** through a unified interface
- Enabling **flexible key management** strategies
- Maintaining **domain neutrality** so you can build your own domain logic on top

## What VeriCore Is Not

- **Not a full identity solution**: VeriCore provides building blocks, not a complete identity system
- **Not a blockchain**: VeriCore works with existing blockchains, it doesn't create new ones
- **Not a wallet**: VeriCore manages keys through pluggable KMS, but doesn't provide wallet UI/UX
- **Not domain-specific**: VeriCore is intentionally domain-agnostic to maximize reusability

## Next Steps

- Learn about [Key Features](key-features.md)
- Explore [Use Cases](use-cases.md)
- Understand the [Architecture Overview](architecture-overview.md)

