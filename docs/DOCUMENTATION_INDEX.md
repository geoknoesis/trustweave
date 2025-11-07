# VeriCore Documentation Index

## Main Documentation

- **[README.md](../README.md)**: Main project documentation with quick start guide
- **[CHANGELOG.md](../CHANGELOG.md)**: Detailed changelog of all optimizations and improvements
- **[OPTIMIZATION_COMPLETE.md](../OPTIMIZATION_COMPLETE.md)**: Summary of completed optimization phases

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
import io.geoknoesis.vericore.anchor.options.AlgorandOptions
import io.geoknoesis.vericore.anchor.ChainId

val chainId = ChainId.Algorand.Testnet
val options = AlgorandOptions(algodUrl = "...", privateKey = "...")
val client = AlgorandBlockchainAnchorClient(chainId.toString(), options)
```

### Error Handling
```kotlin
import io.geoknoesis.vericore.anchor.exceptions.*

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

