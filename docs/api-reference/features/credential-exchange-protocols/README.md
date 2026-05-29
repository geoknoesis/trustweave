---
title: Credential Exchange Protocols
nav_exclude: true
redirect_from:
  - /features/credential-exchange-protocols/README/

---

# Credential Exchange Protocols

Complete implementation of credential exchange protocols for TrustWeave, providing a unified interface for DIDComm V2, OIDC4VCI, OIDC4VP, SIOPv2, and CHAPI.

## Quick Start

Get started in 5 minutes! See the [Complete Quick Start Guide](QUICK_START.md) for a full working example.

```kotlin
import org.trustweave.credential.exchange.*
import org.trustweave.credential.exchange.registry.ExchangeProtocolRegistries
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.result.ExchangeResult

// Create registry and exchange service
val registry = ExchangeProtocolRegistries.default()
val didCommService = DidCommFactory.createInMemoryServiceWithPlaceholderCrypto(kms, resolveDid)
registry.register(DidCommExchangeProtocol(didCommService))

val exchangeService = ExchangeServices.createExchangeService(
    protocolRegistry = registry,
    credentialService = credentialService,
    didResolver = didResolver
)

// Use any protocol with the same API
val offerResult = exchangeService.offer(
    ExchangeRequest.Offer(
        protocolName = "didcomm".requireExchangeProtocolName(),
        issuerDid = Did("did:key:issuer"),
        holderDid = Did("did:key:holder"),
        credentialPreview = credentialPreview,
        options = ExchangeOptions.Empty
    )
)
val offer = when (offerResult) {
    is ExchangeResult.Success -> offerResult.value
    else -> throw IllegalStateException("Offer failed: $offerResult")
}
```

**For complete examples, see:**
- **[Quick Start Guide](QUICK_START.md)** - Complete working example with error handling
- **[API Reference](API_REFERENCE.md)** - Complete API documentation
- **[Examples](EXAMPLES.md)** - More code examples

## Protocols

### ✅ DIDComm V2

**Status**: Production Ready
**Documentation**: [DIDComm Protocol](didcomm.md)

- Full protocol support (offer, request, issue, proof request, proof presentation)
- Production crypto via `didcomm-java` library
- End-to-end encryption (ECDH-1PU)
- Message threading
- JWS signing

### ✅ OIDC4VCI

**Status**: Production Ready
**Documentation**: [OIDC4VCI Protocol](oidc4vci.md)

- Credential issuance flow
- HTTP integration with credential issuer endpoints
- Token exchange
- Proof of possession (JWT)
- Metadata discovery

### ✅ OIDC4VP

**Status**: Production Ready
**Documentation**: [OIDC4VP Protocol](oidc4vp.md)

- Verifier-initiated proof requests via `vp_token`
- DIF Presentation Exchange v2 integration
- HAIP (High Assurance Interoperability Profile) validator
- Cross-device and same-device direct-post flows
- Pairs with SIOPv2 for combined authentication + presentation

### ✅ SIOPv2

**Status**: Production Ready
**Documentation**: [SIOPv2 Protocol](siopv2.md)

- Wallet-as-OP (Self-Issued OpenID Provider v2) authentication
- DID-based `id_token` issuance
- Cross-device QR-code login
- Pairs with OIDC4VP for combined sign-in + credential presentation

### ✅ CHAPI

**Status**: Production Ready
**Documentation**: [CHAPI Protocol](chapi.md)

- Browser-compatible message generation
- Credential offer/issue
- Proof request/presentation
- W3C Credential Handler API compliance

## Documentation

### Getting Started
- **[Quick Start](QUICK_START.md)** - Complete working example (5 minutes)
- **[API Reference](API_REFERENCE.md)** - Complete API documentation
- **[Examples](EXAMPLES.md)** - More code examples
- **[Workflows](WORKFLOWS.md)** - Step-by-step workflow guides
- **[Error Handling](ERROR_HANDLING.md)** - Complete error reference
- **[Troubleshooting](TROUBLESHOOTING.md)** - Common issues and solutions
- **[Glossary](GLOSSARY.md)** - Terms and concepts
- **[Best Practices](BEST_PRACTICES.md)** - Security, performance, and design patterns
- **[Versioning](VERSIONING.md)** - Version info and migration guides

### Core Concepts
- **[Protocol Abstraction](../../../core-concepts/credential-exchange-protocols.md)** - Core concepts
- **[Implementation Guide](implementation-guide.md)** - How to implement new protocols

### Advanced Topics
- **[Storage & Secret Resolver](STORAGE_AND_SECRET_RESOLVER.md)** - Persistent storage and SecretResolver
- **[Reusable Components](REUSABLE_COMPONENTS.md)** - Components reusable across protocols

## Protocol Comparison

| Feature | DIDComm | OIDC4VCI | OIDC4VP | SIOPv2 | CHAPI |
|---------|---------|----------|---------|--------|-------|
| Credential Offer | ✅ | ✅ | ❌ | ❌ | ✅ |
| Credential Request | ✅ | ✅ | ❌ | ❌ | ❌ |
| Credential Issue | ✅ | ✅ | ❌ | ❌ | ✅ |
| Proof Request | ✅ | ❌ | ✅ | ✅ | ✅ |
| Proof Presentation | ✅ | ❌ | ✅ | ✅ | ✅ |
| Authentication (`id_token`) | ❌ | ❌ | ❌ | ✅ | ❌ |
| Encryption | ✅ | Via HTTPS | Via HTTPS | Via HTTPS | Browser |
| Production Ready | ✅ | ✅ | ✅ | ✅ | ✅ |

## Production Status

**All plugins are production-ready** with:
- Production-grade implementations
- No placeholder/mock code in production paths
- Integration with mature libraries
- Proper error handling
- Complete documentation

## Getting Started

1. **Choose a Protocol**: Based on your use case
2. **Read Protocol Documentation**: See individual protocol docs
3. **Register Protocol**: Add to the registry
4. **Use Protocol**: Call registry methods

## Examples

See [Implementation Guide](implementation-guide.md) for detailed examples and [Protocol Abstraction](../../../core-concepts/credential-exchange-protocols.md) for core concepts.
