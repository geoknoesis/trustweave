# VeriCore Documentation

Welcome to the VeriCore developer documentation!

> VeriCore is a Geoknoesis LLC product. Learn more at [www.geoknoesis.com](https://www.geoknoesis.com).

## Quick Start

- [Installation](getting-started/installation.md) - Get started with VeriCore
- [Quick Start Guide](getting-started/quick-start.md) - Your first VeriCore application
- [Use Case Scenarios](scenarios/README.md) - Real-world examples
- [Architecture Overview](introduction/architecture-overview.md) - Visual flow of DID ➜ credential ➜ proof ➜ anchoring
- [Advanced Topics](advanced/key-rotation.md) - Key rotation, verification policies, and more
- [FAQ](faq.md) - Quick answers about samples, licensing, and integrations

## What is VeriCore?

VeriCore is a **neutral, reusable trust and identity core** library for Kotlin, designed to be:
- **Domain-agnostic**: Works for any use case
- **Chain-agnostic**: Supports any blockchain
- **DID-method-agnostic**: Works with any DID method
- **KMS-agnostic**: Supports any key management system

## Key Features

- ✅ Decentralized Identifiers (DIDs)
- ✅ Verifiable Credentials (VCs)
- ✅ Blockchain Anchoring
- ✅ Key Management
- ✅ Wallet Management
- ✅ JSON Canonicalization

## Documentation Structure

This documentation is organized into sections:

- **Getting Started**: Installation, quick start, and use case scenarios
- **Core Concepts**: DIDs, VCs, wallets, blockchain anchoring
- **API Reference**: Complete API documentation
- **Tutorials**: Step-by-step guides
- **Advanced Topics**: SPI, custom adapters, testing

## Use Case Scenarios

Explore real-world use cases:

- **[View All Scenarios](scenarios/README.md)** - Complete list of all available scenarios

**Popular Scenarios:**
- 🌍 [Earth Observation](scenarios/earth-observation-scenario.md) - Data integrity verification
- 🎓 [Academic Credentials](scenarios/academic-credentials-scenario.md) - University credential system
- 🏛️ [National Education (AlgeroPass)](scenarios/national-education-credentials-algeria-scenario.md) - National credential system
- 💼 [Professional Identity](scenarios/professional-identity-scenario.md) - Professional credential wallet
- 📍 [Proof of Location](scenarios/proof-of-location-scenario.md) - Geospatial location proofs
- 🌐 [Spatial Web Authorization](scenarios/spatial-web-authorization-scenario.md) - DID-based spatial authorization
- 🔄 [Digital Workflow & Provenance](scenarios/digital-workflow-provenance-scenario.md) - PROV-O workflow tracking
- 📰 [News Industry](scenarios/news-industry-scenario.md) - Content provenance
- 📊 [Data Catalog & DCAT](scenarios/data-catalog-dcat-scenario.md) - Verifiable data catalog
- 💰 [Financial Services & KYC](scenarios/financial-services-kyc-scenario.md) - KYC credential system
- 🏥 [Healthcare & Medical Records](scenarios/healthcare-medical-records-scenario.md) - Medical credential system
- 🏭 [IoT Device Identity](scenarios/iot-device-identity-scenario.md) - Device identity management
- 📦 [Supply Chain & Traceability](scenarios/supply-chain-traceability-scenario.md) - Supply chain tracking
- 🏛️ [Government Digital Identity](scenarios/government-digital-identity-scenario.md) - Government credential system

## Contributing

Found an error or want to improve the documentation? See our [Contributing Guide](contributing/README.md).

## Licensing

VeriCore is available under a dual license model: open source for non-commercial and educational use, and a commercial license from Geoknoesis LLC for production deployments. See the [Licensing Overview](licensing/README.md) for details.

## License

See the main project LICENSE file.
