# Credential API Architecture

## Overview

The `credential-api` module provides a clean, extensible API for working with W3C Verifiable Credentials. It implements a Service Provider Interface (SPI) pattern for proof engines, allowing different proof suite implementations (VC-LD, VC-JWT, SD-JWT-VC) to be plugged in seamlessly.

## Architecture Decisions

### ADR-001: SPI Pattern for Proof Engines

**Status:** Accepted  
**Date:** 2025-12-28

**Context:**
The module needs to support multiple proof suite formats (VC-LD, VC-JWT, SD-JWT-VC) with the ability to add new formats in the future without modifying core code.

**Decision:**
We use a Service Provider Interface (SPI) pattern with a `ProofEngine` interface that encapsulates format-specific operations.

**Consequences:**
- ✅ Clean separation between format-agnostic service logic and format-specific proof operations
- ✅ Easy to add new proof suite implementations
- ✅ Testability: Each proof engine can be tested independently
- ⚠️ SPI interfaces may evolve (documented in interface KDoc)

**Implementation:**
- `ProofEngine` interface defines contract for issuance, verification, and presentation operations
- `DefaultCredentialService` delegates format-specific operations to registered proof engines
- Built-in engines (VC-LD, SD-JWT-VC) are provided out-of-the-box

---

### ADR-002: Centralized Error Handling

**Status:** Accepted  
**Date:** 2025-12-28

**Context:**
Error handling logic was duplicated across methods, making it hard to maintain consistent error reporting.

**Decision:**
Extract error handling into a centralized `ErrorHandling` utility object that provides:
- Exception-to-result type conversion
- Consistent error message formatting
- Proper cancellation handling for coroutines

**Consequences:**
- ✅ Reduced code duplication
- ✅ Consistent error handling across all operations
- ✅ Easier to maintain and update error handling logic
- ✅ Better testability

**Implementation:**
- `ErrorHandling.handleIssuanceErrors()` converts exceptions to `IssuanceResult.Failure` types
- `ErrorHandling.validateEngineAvailability()` validates proof engine availability
- Used by `DefaultCredentialService.issue()` and other operations

---

### ADR-003: Sealed Classes for Result Types

**Status:** Accepted  
**Date:** 2025-12-28

**Context:**
We need type-safe result types that exhaustively represent all possible outcomes of credential operations.

**Decision:**
Use Kotlin sealed classes for `IssuanceResult` and `VerificationResult` to provide:
- Type-safe exhaustive pattern matching
- Clear error hierarchy
- Compile-time safety

**Consequences:**
- ✅ Compile-time exhaustiveness checking
- ✅ Clear error types with specific fields
- ✅ IDE support for pattern matching
- ✅ Better API documentation through type hierarchy

**Implementation:**
- `IssuanceResult` sealed class with `Success` and `Failure` subtypes
- `VerificationResult` sealed class with `Valid` and `Invalid` subtypes
- Each failure type includes relevant context (format, reason, field, etc.)

---

### ADR-004: Security Constants for Input Validation

**Status:** Accepted  
**Date:** 2025-12-28

**Context:**
We need to prevent denial-of-service attacks by limiting input sizes and resource usage.

**Decision:**
Define security constants in `SecurityConstants` object with documented rationale for each limit.

**Consequences:**
- ✅ Centralized security configuration
- ✅ Documented rationale for each limit
- ✅ Easy to adjust limits if needed
- ✅ Prevents resource exhaustion attacks

**Implementation:**
- `SecurityConstants` object with limits for credential size, claims count, DID length, etc.
- `InputValidation` utility uses these constants
- All limits are conservative and based on typical use cases

---

### ADR-005: Direct Library Usage Over Reflection

**Status:** Accepted  
**Date:** 2025-12-28

**Context:**
`CredentialTransformer` was using reflection to access nimbus-jose-jwt library classes, even though the library is a required dependency.

**Decision:**
Replace reflection with direct imports and method calls since nimbus-jose-jwt is a required dependency.

**Consequences:**
- ✅ Better compile-time type checking
- ✅ Improved IDE support and refactoring
- ✅ Better performance (no reflection overhead)
- ✅ Clearer code intent

**Implementation:**
- Direct imports: `import com.nimbusds.jwt.JWTClaimsSet`
- Direct method calls: `JWTClaimsSet.Builder().issuer(...)`
- Maintains backward compatibility with same public API

---

### ADR-006: Elegant DSL API for Credential Transformations

**Status:** Accepted  
**Date:** 2025-01-XX

**Context:**
The `CredentialTransformer` API required creating instances and calling methods imperatively, which was inconsistent with TrustWeave's DSL patterns and less elegant than it could be.

**Decision:**
Introduce extension functions directly on `VerifiableCredential` and related types to provide a fluent, DSL-like API for format transformations.

**Consequences:**
- ✅ More elegant and idiomatic Kotlin API
- ✅ Consistent with TrustWeave DSL patterns
- ✅ Enables fluent chaining of transformations
- ✅ Better discoverability through IDE autocomplete
- ✅ Maintains backward compatibility with direct API

**Implementation:**
- Extension functions: `credential.toJwt()`, `credential.toJsonLd()`, `credential.toCbor()`
- Reverse transformations: `jwtString.fromJwt()`, `jsonLdObject.toCredential()`, `cborBytes.fromCbor()`
- Round-trip helpers: `credential.roundTripJwt()`, `credential.roundTripCbor()`
- TrustWeave integration: `trustWeave.toJwt(credential)`, `trustWeave.toJsonLd(credential)`
- DSL builder: `credential.transform { toJwt() }` for complex scenarios

**Example Usage:**
```kotlin
// Elegant extension function API (recommended)
val jwt = credential.toJwt()
val jsonLd = credential.toJsonLd()
val cbor = credential.toCbor()

// Fluent chaining
val roundTrip = credential
    .toJwt()
    .fromJwt()
    .toCbor()
    .fromCbor()

// With TrustWeave integration
val jwt = trustWeave.toJwt(credential)
```

---

## Design Patterns

### Strategy Pattern
- **Usage:** Proof engines implement different strategies for proof generation/verification
- **Location:** `ProofEngine` interface and implementations (`VcLdProofEngine`, `SdJwtProofEngine`)

### Factory Pattern
- **Usage:** `CredentialServices` provides factory methods for creating `CredentialService` instances
- **Location:** `CredentialServices.kt`

### Builder Pattern
- **Usage:** Extension functions provide fluent builders for requests and options
- **Location:** `IssuanceRequestBuilder`, `CredentialSubjectBuilder`, extension functions

### Utility Pattern
- **Usage:** Internal utilities grouped by functionality (`ErrorHandling`, `InputValidation`, `JsonLdUtils`)
- **Location:** `org.trustweave.credential.internal` package

## Module Structure

```
credential-api/
├── CredentialService.kt          # Main service interface
├── CredentialServices.kt         # Factory methods
├── internal/
│   ├── DefaultCredentialService.kt  # Service implementation
│   ├── ErrorHandling.kt          # Centralized error handling
│   ├── InputValidation.kt        # Security validation
│   ├── CredentialValidation.kt   # VC-specific validation
│   ├── SecurityConstants.kt      # Security limits
│   └── ...
├── proof/
│   └── internal/engines/         # Proof engine implementations
├── model/vc/                     # VC data model types
├── requests/                     # Request types
├── results/                      # Result types (sealed classes)
└── spi/proof/                    # SPI interfaces
```

## Dependencies

### Core Dependencies
- `kotlinx-serialization-json`: JSON serialization
- `kotlinx-coroutines-core`: Async operations
- `kotlinx-datetime`: Date/time handling

### Proof Engine Dependencies
- `jsonld-java`: JSON-LD canonicalization for VC-LD
- `bouncycastle-prov`: Cryptographic operations
- `nimbus-jose-jwt`: JWT operations for SD-JWT-VC

### Internal Dependencies
- `did-core`: DID resolution
- `kms-core`: Key management operations
- `common`: Shared utilities

## Extension Points

### Adding a New Proof Suite

1. Implement `ProofEngine` interface
2. Register engine in `BuiltInEngines.kt` (for built-in engines)
3. Or provide via SPI for external plugins

### Custom Validation

1. Extend `CredentialValidation` or create new utility
2. Use in `DefaultCredentialService.verify()`

### Custom Error Handling

1. Extend `ErrorHandling` utility
2. Add new `IssuanceResult.Failure` or `VerificationResult.Invalid` subtypes if needed



