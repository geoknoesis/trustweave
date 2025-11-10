# Core Modules

VeriCore is organized into several core modules, each providing specific functionality.

## Module Overview

- **vericore-spi** - Shared plugin/service abstractions and adapter loader utilities
- **vericore-trust** - Trust registry interfaces and trust-layer runtime helpers
- **[vericore-core](vericore-core.md)** - Credential domain APIs, DSLs, and wallet utilities
- **[vericore-json](vericore-json.md)** - JSON canonicalization and digest computation utilities
- **[vericore-kms](vericore-kms.md)** - Key management service abstraction
- **[vericore-did](vericore-did.md)** - DID and DID Document management with pluggable DID methods
- **[vericore-anchor](vericore-anchor.md)** - Blockchain anchoring abstraction with chain-agnostic interfaces
- **[vericore-testkit](vericore-testkit.md)** - In-memory test implementations for all interfaces

## Module Dependencies

```
vericore-spi (no dependencies)
vericore-trust → vericore-spi
vericore-core → vericore-spi, vericore-trust
vericore-json → vericore-core
vericore-kms → vericore-core
vericore-did → vericore-core, vericore-spi, vericore-kms
vericore-anchor → vericore-core, vericore-spi, vericore-json
vericore-testkit → vericore-core, vericore-spi, vericore-trust, vericore-did, vericore-kms, vericore-anchor
```

## Next Steps

- Explore individual module documentation
- Check out [Integration Modules](../integrations/README.md)
- See [Examples](../examples/README.md) for usage patterns

