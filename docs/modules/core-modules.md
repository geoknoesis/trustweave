# Core Modules

VeriCore is organized into several core modules, each providing specific functionality.

## Module Overview

- **[vericore-core](vericore-core.md)** - Shared types, exceptions, and common utilities
- **[vericore-json](vericore-json.md)** - JSON canonicalization and digest computation utilities
- **[vericore-kms](vericore-kms.md)** - Key management service abstraction
- **[vericore-did](vericore-did.md)** - DID and DID Document management with pluggable DID methods
- **[vericore-anchor](vericore-anchor.md)** - Blockchain anchoring abstraction with chain-agnostic interfaces
- **[vericore-testkit](vericore-testkit.md)** - In-memory test implementations for all interfaces

## Module Dependencies

```
vericore-core (no dependencies)
    ↓
vericore-json → vericore-core
vericore-kms → vericore-core
    ↓
vericore-did → vericore-core, vericore-kms
vericore-anchor → vericore-core, vericore-json
    ↓
vericore-testkit → all above modules
```

## Next Steps

- Explore individual module documentation
- Check out [Integration Modules](../integrations/README.md)
- See [Examples](../examples/README.md) for usage patterns

