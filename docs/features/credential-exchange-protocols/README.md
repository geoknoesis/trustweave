---
title: Credential Exchange Protocols
---

# Credential Exchange Protocols

Complete implementation of credential exchange protocols for TrustWeave, providing a unified interface for DIDComm V2, OIDC4VCI, and CHAPI.

## Quick Start

Get started in 5 minutes! See the [Complete Quick Start Guide](./QUICK_START.md) for a full working example.

```kotlin
import com.trustweave.credential.exchange.*

// Create registry
val registry = CredentialExchangeProtocolRegistry()

// Register protocols
val didCommService = DidCommFactory.createInMemoryService(kms, resolveDid)
registry.register(DidCommExchangeProtocol(didCommService))

// Use any protocol with the same API
val offer = registry.offerCredential("didcomm", request)
```

**For complete examples, see:**
- **[Quick Start Guide](./QUICK_START.md)** - Complete working example with error handling
- **[API Reference](./API_REFERENCE.md)** - Complete API documentation
- **[Examples](./EXAMPLES.md)** - More code examples

## Protocols

### ✅ DIDComm V2

**Status**: Production Ready  
**Documentation**: [DIDComm Protocol](./didcomm.md)

- Full protocol support (offer, request, issue, proof request, proof presentation)
- Production crypto via `didcomm-java` library
- End-to-end encryption (ECDH-1PU)
- Message threading
- JWS signing

### ✅ OIDC4VCI

**Status**: Production Ready  
**Documentation**: [OIDC4VCI Protocol](./oidc4vci.md)

- Credential issuance flow
- HTTP integration with credential issuer endpoints
- Token exchange
- Proof of possession (JWT)
- Metadata discovery

### ✅ CHAPI

**Status**: Production Ready  
**Documentation**: [CHAPI Protocol](./chapi.md)

- Browser-compatible message generation
- Credential offer/issue
- Proof request/presentation
- W3C Credential Handler API compliance

## Documentation

### Getting Started
- **[Quick Start](./QUICK_START.md)** - Complete working example (5 minutes)
- **[API Reference](./API_REFERENCE.md)** - Complete API documentation
- **[Examples](./EXAMPLES.md)** - More code examples
- **[Workflows](./WORKFLOWS.md)** - Step-by-step workflow guides
- **[Error Handling](./ERROR_HANDLING.md)** - Complete error reference
- **[Troubleshooting](./TROUBLESHOOTING.md)** - Common issues and solutions
- **[Glossary](./GLOSSARY.md)** - Terms and concepts
- **[Best Practices](./BEST_PRACTICES.md)** - Security, performance, and design patterns
- **[Versioning](./VERSIONING.md)** - Version info and migration guides

### Core Concepts
- **[Protocol Abstraction](../../core-concepts/credential-exchange-protocols.md)** - Core concepts
- **[Implementation Guide](./implementation-guide.md)** - How to implement new protocols

### Advanced Topics
- **[Production Readiness](./PRODUCTION_READINESS.md)** - Production deployment guide
- **[Final Evaluation](./FINAL_EVALUATION.md)** - Complete production readiness assessment
- **[Storage & Secret Resolver](./STORAGE_AND_SECRET_RESOLVER.md)** - Persistent storage and SecretResolver
- **[Advanced Features Plan](./ADVANCED_FEATURES_IMPLEMENTATION_PLAN.md)** - Implementation plan for advanced features
- **[Reusable Components](./REUSABLE_COMPONENTS.md)** - Components reusable across protocols

## Protocol Comparison

| Feature | DIDComm | OIDC4VCI | CHAPI |
|---------|---------|----------|-------|
| Credential Offer | ✅ | ✅ | ✅ |
| Credential Request | ✅ | ✅ | ❌ |
| Credential Issue | ✅ | ✅ | ✅ |
| Proof Request | ✅ | ❌ | ✅ |
| Proof Presentation | ✅ | ❌ | ✅ |
| Encryption | ✅ | Via HTTPS | Browser |
| Production Ready | ✅ | ✅ | ✅ |

## Production Status

**All plugins are production-ready** with:
- ✅ Production-grade implementations
- ✅ No placeholder/mock code in production paths
- ✅ Integration with mature libraries
- ✅ Proper error handling
- ✅ Complete documentation

See [Production Readiness](./PRODUCTION_READINESS.md) and [Final Evaluation](./FINAL_EVALUATION.md) for detailed assessments.

## Getting Started

1. **Choose a Protocol**: Based on your use case
2. **Read Protocol Documentation**: See individual protocol docs
3. **Register Protocol**: Add to the registry
4. **Use Protocol**: Call registry methods

## Examples

See [Implementation Guide](./implementation-guide.md) for detailed examples and [Protocol Abstraction](../../core-concepts/credential-exchange-protocols.md) for core concepts.
