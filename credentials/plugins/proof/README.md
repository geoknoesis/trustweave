# Proof Engines

**Note**: Built-in proof engines live in `credentials:credential-api` (with shared models in `credentials:credential-models-mp`), and additional format-specific engines ship as separate SPI plugin modules (`credentials:plugins:jades`, `credentials:plugins:mdl`). This directory is kept as an index for reference.

## Available Proof Engines

### Built-in Proof Engines (`credentials:credential-api`)

Always available when you depend on `credentials:credential-api`:

| Format | Suite ID | Location | Capabilities |
|--------|----------|----------|--------------|
| **VC-LD** | `vc-ld` | `credentials/credential-api/src/main/kotlin/org/trustweave/credential/proof/internal/engines/VcLdProofEngine.kt` | Selective disclosure, Predicates |
| **SD-JWT-VC** | `sd-jwt-vc` | `credentials/credential-api/src/main/kotlin/org/trustweave/credential/proof/internal/engines/SdJwtProofEngine.kt` | Selective disclosure, Holder key binding |

### Plugin Proof Engines (SPI Auto-Discovery)

Additional formats are provided as optional plugin modules discovered via `ProofEngineProvider`:

| Format | Module | Implementation |
|--------|--------|----------------|
| **JAdES** | `credentials:plugins:jades` | `org.trustweave.credential.jades.JAdESProofEngine` |
| **mDoc / mDL** | `credentials:plugins:mdl` | `org.trustweave.credential.mdl.engine.MdocProofEngine` |

> **Note:** AnonCreds is not currently implemented in TrustWeave.

## Usage

All built-in proof formats are automatically available when you create a `CredentialService`:

```kotlin
import org.trustweave.credential.credentialService
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.format.ProofSuiteId

val service = credentialService(didResolver)

// Built-in formats are ready to use (no manual registration needed)
val request = IssuanceRequest(
    format = ProofSuiteId("vc-ld"),
    // ... other fields (issuer, credentialSubject, type, ...)
)
val result = service.issue(request)
```

## Architecture

Public SPI contracts and built-in engines are organized across:

```text
credentials/credential-models-mp/src/commonMain/kotlin/org/trustweave/credential/
├── proof/ProofOptions.kt
└── spi/proof/
    ├── JsonLdCanonicalizationPort.kt
    └── ProofEngineCapabilities.kt

credentials/credential-api/src/main/kotlin/org/trustweave/credential/
├── spi/proof/
│   ├── ProofEngine.kt
│   ├── ProofEngineProvider.kt
│   └── SignatureVerificationPort.kt
└── proof/
    ├── PresentationNonceStore.kt
    ├── ProofOptionsExtensions.kt
    └── internal/engines/
        ├── VcLdProofEngine.kt
        ├── VcLdProofEngineProvider.kt
        ├── SdJwtProofEngine.kt
        ├── SdJwtProofEngineProvider.kt
        ├── DidVerificationMethodResolver.kt
        ├── JsonLdDocumentBuilder.kt
        ├── ProofEngineUtils.kt
        └── SelectiveDisclosureFilter.kt
```

Built-in engines are wired automatically by `DefaultCredentialService`; plugin engines are discovered via `META-INF/services/org.trustweave.credential.spi.proof.ProofEngineProvider`. See [Proof Engine Implementation Guide](../../../docs/api-reference/advanced/proof-engine-implementation-guide.md) to implement a custom format.
