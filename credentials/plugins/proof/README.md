# Proof Engines

**Note**: All proof engines are now built-in and located in `credential-api`. This directory is kept for reference only.

## Built-in Proof Engines

All proof format implementations are now built into `credential-api` and are always available:

| Format | Location | Capabilities |
|--------|----------|--------------|
| **VC-LD** | `credential-api/src/main/kotlin/org.trustweave/credential/proof/internal/engines/` | Selective disclosure, Predicates |
| **SD-JWT-VC** | `credential-api/src/main/kotlin/org.trustweave/credential/proof/internal/engines/` | Selective disclosure |
| **AnonCreds** | `credential-api/src/main/kotlin/org.trustweave/credential/proof/internal/engines/` | ZK-proofs, Selective disclosure |

## Usage

All proof formats are automatically available when you create a credential service:

```kotlin
import org.trustweave.credential.*

val service = credentialService(didResolver)

// All formats are built-in and ready to use
val vcLdCredential = service.issue(
    IssuanceRequest(
        format = CredentialFormatId("vc-ld"),
        // ... other fields
    )
)
```

No registration or discovery needed - all formats are built-in!

## Architecture

Proof engines are located in:
```
credential-api/src/main/kotlin/org.trustweave/credential/proof/
├── ProofOptions.kt                    # Public API
└── internal/
    ├── ProofEngineRegistry.kt
    ├── DefaultProofEngineRegistry.kt
    ├── ProofEngines.kt
    └── engines/                        # All built-in engines
        ├── VcLdProofEngine.kt
        ├── SdJwtProofEngine.kt
        └── AnonCredsProofEngine.kt
```

All engines are directly instantiated - no ServiceLoader or plugin discovery needed.
