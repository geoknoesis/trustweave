# VeriCore Documentation Index

## Main Documentation

- **[README.md](../README.md)**: Main project documentation with quick start guide
- **[CHANGELOG.md](../CHANGELOG.md)**: Detailed changelog of all optimizations and improvements
- **[OPTIMIZATION_COMPLETE.md](../OPTIMIZATION_COMPLETE.md)**: Summary of completed optimization phases

## Core Concepts Documentation

- **[Core Concepts](../docs/core-concepts/README.md)**: Introduction to fundamental concepts
  - **[DIDs](../docs/core-concepts/dids.md)**: Decentralized Identifiers explained
  - **[Verifiable Credentials](../docs/core-concepts/verifiable-credentials.md)**: VC lifecycle and usage
  - **[Wallets](../docs/core-concepts/wallets.md)**: Wallet capabilities and patterns
  - **[Blockchain Anchoring](../docs/core-concepts/blockchain-anchoring.md)**: Data anchoring concepts
  - **[Key Management](../docs/core-concepts/key-management.md)**: Key management systems

## Tutorials

- **[Wallet API Tutorial](../docs/tutorials/wallet-api-tutorial.md)**: Complete guide to using wallets
  - Creating wallets
  - Storing and organizing credentials
  - Querying credentials
  - Creating presentations
  - Lifecycle management

## Use Case Scenarios

- **[Earth Observation Scenario](../docs/getting-started/earth-observation-scenario.md)**: Complete EO data integrity workflow
- **[Academic Credentials Scenario](../docs/getting-started/academic-credentials-scenario.md)**: University credential issuance and verification
- **[National Education Credentials Algeria Scenario](../docs/getting-started/national-education-credentials-algeria-scenario.md)**: AlgeroPass national-level education credential system
- **[Professional Identity Scenario](../docs/getting-started/professional-identity-scenario.md)**: Professional credential wallet management
- **[Proof of Location Scenario](../docs/getting-started/proof-of-location-scenario.md)**: Geospatial location proofs and verification
- **[Spatial Web Authorization Scenario](../docs/getting-started/spatial-web-authorization-scenario.md)**: DID-based authorization for spatial entities (agents, activities, things, features)
- **[Digital Workflow & Provenance Scenario](../docs/getting-started/digital-workflow-provenance-scenario.md)**: PROV-O workflow provenance tracking for digital information
- **[News Industry Scenario](../docs/getting-started/news-industry-scenario.md)**: Content provenance and authenticity verification for news media
- **[Data Catalog & DCAT Scenario](../docs/getting-started/data-catalog-dcat-scenario.md)**: Verifiable data catalog system using DCAT for government and enterprise

## API Reference

- **[Wallet API](../docs/api-reference/wallet-api.md)**: Complete wallet API reference
  - Core interfaces
  - Data models
  - Extension functions
  - Usage examples
- **[Credential Service API](../docs/api-reference/credential-service-api.md)**: SPI and typed options for issuers/verifiers

## Module Documentation

### Core Modules
- **[vericore-anchor/README.md](../vericore-anchor/src/main/kotlin/io/geoknoesis/vericore/anchor/README.md)**: Comprehensive anchor package documentation
  - Type-safe options and chain IDs
  - Exception hierarchy
  - Usage examples
  - Architecture benefits

### Adapter Modules
- **[vericore-ganache/README.md](../vericore-ganache/README.md)**: Ganache adapter with TestContainers
- **[vericore-indy/README.md](../vericore-indy/README.md)**: Hyperledger Indy adapter

### Test Utilities
- **[vericore-testkit/eo/README.md](../vericore-testkit/src/main/kotlin/io/geoknoesis/vericore/testkit/eo/README.md)**: EO test integration utilities

## Key Features Documented

### Type Safety
- ✅ Type-safe options (`AlgorandOptions`, `PolygonOptions`, `GanacheOptions`, `IndyOptions`)
- ✅ Type-safe chain IDs (`ChainId` sealed class)
- ✅ Compile-time validation

### Error Handling
- ✅ Exception hierarchy documentation
- ✅ Error context examples
- ✅ Migration guide

### Code Quality
- ✅ Gradle wrapper usage
- ✅ ktlint integration
- ✅ Build commands

### Performance
- ✅ Digest caching configuration
- ✅ JSON optimization details

### Architecture
- ✅ `AbstractBlockchainAnchorClient` benefits
- ✅ Code reuse patterns
- ✅ Resource management
- ✅ **Wallet management system** with ISP-based design
- ✅ **Type-safe capability checking** for wallets
- ✅ **Composable wallet interfaces** for extensibility

## Quick Reference

### Building
```bash
./gradlew build          # Unix/Linux/macOS
.\gradlew.bat build      # Windows
```

### Testing
```bash
./gradlew test
```

### Code Quality
```bash
./gradlew ktlintCheck    # Check formatting
./gradlew ktlintFormat   # Auto-format
```

### Type-Safe Configuration
```kotlin
import com.geoknoesis.vericore.anchor.options.AlgorandOptions
import com.geoknoesis.vericore.anchor.ChainId

val chainId = ChainId.Algorand.Testnet
val options = AlgorandOptions(algodUrl = "...", privateKey = "...")
val client = AlgorandBlockchainAnchorClient(chainId.toString(), options)
```

### Error Handling
```kotlin
import com.geoknoesis.vericore.anchor.exceptions.*

try {
    val result = client.writePayload(payload)
} catch (e: BlockchainTransactionException) {
    // Rich context: chainId, txHash, payloadSize, gasUsed
}
```

## Migration Guides

See [CHANGELOG.md](../CHANGELOG.md) for detailed migration guides:
- Migrating to type-safe options
- Migrating to type-safe chain IDs
- Updating error handling
- Resource management patterns

