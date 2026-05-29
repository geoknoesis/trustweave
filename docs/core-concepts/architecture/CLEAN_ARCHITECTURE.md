---
redirect_from:
  - /architecture/CLEAN_ARCHITECTURE/
---

# Clean Architecture in TrustWeave

TrustWeave applies [Uncle Bob's Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html) principles to ensure maintainability, testability, and independence from frameworks.

## Dependency Rule

> *Source code dependencies point only inward. Inner layers know nothing of outer layers.*

```
┌─────────────────────────────────────────────────────────────┐
│  Frameworks & Drivers (Ktor, Testcontainers, DB drivers)    │  ← Outermost
├─────────────────────────────────────────────────────────────┤
│  Interface Adapters (DSL builders, presenters, gateways)    │
├─────────────────────────────────────────────────────────────┤
│  Use Cases (CredentialIssuanceService, DidManagementService)│
├─────────────────────────────────────────────────────────────┤
│  Entities (Did, VerifiableCredential, TrustPath)            │  ← Innermost
└─────────────────────────────────────────────────────────────┘
```

## Layer Mapping

| Clean Architecture | TrustWeave | Location |
|--------------------|------------|----------|
| **Entities** | `Did`, `VerifiableCredential`, `CredentialType`, `ProofType`, `AnchorRef` | `did-core`, `credential-api`, `anchor-core` |
| **Use Cases** | `CredentialIssuanceService`, `CredentialVerificationService`, `DidManagementService`, `WalletManagementService`, `TrustManagementService` | `trust/services/` |
| **Interface Adapters** | DSL builders (`IssuanceBuilder`, `DidBuilder`), `TrustWeave` facade | `trust/dsl/` |
| **Frameworks** | Ktor, Spring Boot, Testcontainers | Plugins, examples |

## Dependency Inversion Principle

High-level modules depend on abstractions (ports), not concrete implementations:

- **TrustWeave** receives `CredentialService`, `DidResolver`, `KeyManagementService` via config
- **CredentialIssuanceService** depends on `CredentialService` (interface), not `DefaultCredentialService`
- **SmartContractService** can be injected—`TrustWeave` uses `config.smartContractService ?: DefaultSmartContractService(...)`

### Ports (Interfaces)

| Port | Implemented By | Module |
|------|----------------|--------|
| `CredentialService` | `DefaultCredentialService` | credential-api |
| `DidMethod` | KeyDidMethod, WebDidMethod, etc. | did-plugins |
| `KeyManagementService` | InMemoryKms, AwsKms, etc. | kms-plugins |
| `BlockchainAnchorClient` | AlgorandAnchorClient, etc. | anchors-plugins |
| `WalletFactory` | InMemoryWalletFactory, etc. | wallet-plugins |
| `SmartContractService` | `DefaultSmartContractService` | contract |

## Single Responsibility Principle

Each service has one reason to change:

- `CredentialIssuanceService` — issuance orchestration only
- `CredentialVerificationService` — verification only
- `DidManagementService` — DID creation and resolution
- `TrustWeaveFactory` — wiring and SPI resolution

## Kotlin Idioms

- **Sealed classes** for result types (`IssuanceResult`, `DidCreationResult`, `VerificationResult`)
- **Extension functions** for domain mappings (`ProofType.toProofSuiteId()`)
- **Coroutines** for async operations (`suspend fun`)
- **Data classes** for value objects (`Did`, `CredentialConfig`)
- **DSL builders** for fluent configuration (`TrustWeave.build { ... }`)
- **Scope functions** (`let`, `apply`, `also`) for concise flows

## Composition Root

`TrustWeave.build { }` and `TrustWeaveFactory` form the composition root—all dependencies are wired there. Application code receives a fully configured `TrustWeave` instance.

## Testing

- Use `testkit` for in-memory implementations of ports
- Inject mocks via `TrustWeave.build { credentialService(myMock) }`
- Use `@Disabled` for placeholder tests with clear reasons
