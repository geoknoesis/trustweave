# Proof Engines

**Note**: All proof engines are now built-in and located in `credential-api`. This directory is kept for reference only.

## Built-in Proof Engines

All proof format implementations are now built into `credential-api` and are always available:

| Format | Location | Capabilities |
|--------|----------|--------------|
| **VC-LD** | `credential-api/src/main/kotlin/org/trustweave/credential/proof/internal/engines/VcLdProofEngine.kt` | Selective disclosure, Predicates |
| **SD-JWT-VC** | `credential-api/src/main/kotlin/org/trustweave/credential/proof/internal/engines/SdJwtProofEngine.kt` | Selective disclosure |

> **TODO**: An AnonCreds engine was planned but is not yet present in `credential-api`. AnonCreds support is currently out of scope until a dedicated engine ships.

## Usage

All built-in proof formats are automatically available when you create a credential service:

```kotlin
import org.trustweave.credential.credentialService
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.format.ProofSuiteId

val service = credentialService(didResolver)

// Built-in formats are ready to use (no registration needed)
val request = IssuanceRequest(
    format = ProofSuiteId("vc-ld"),
    // ... other fields (issuer, credentialSubject, type, ...)
)
val result = service.issue(request)
```

No registration or discovery needed - all built-in formats are wired directly.

## Architecture

Proof engines live in:
```
credential-api/src/main/kotlin/org/trustweave/credential/proof/
├── ProofOptions.kt                    # Public API
└── internal/
    └── engines/                        # All built-in engines
        ├── VcLdProofEngine.kt
        ├── VcLdProofEngineProvider.kt
        ├── SdJwtProofEngine.kt
        ├── SdJwtProofEngineProvider.kt
        ├── DidVerificationMethodResolver.kt
        ├── JsonLdDocumentBuilder.kt
        ├── ProofEngineUtils.kt
        └── SelectiveDisclosureFilter.kt
```

Engines are directly instantiated by `DefaultCredentialService` - no ServiceLoader or plugin discovery needed.
