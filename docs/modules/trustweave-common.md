# TrustWeave-common

The `TrustWeave-common` module now focuses on credential-domain functionality:

- Credential issuance and verification services
- Credential lifecycle DSLs (`credential.dsl.*`)
- Wallet abstractions and in-memory helpers
- Proof, schema, and revocation utilities used by higher layers

Infrastructure concerns (service adapters, plugin registry, trust runtime) have
been extracted into the new `TrustWeave-trust` module. SPI interfaces are included in `TrustWeave-common`.

## Key Components

- **Credential DSLs** – builders for issuing, storing, and verifying credentials.
- **Credential Models** – canonical data models for credentials, presentations, and proofs.
- **Wallet APIs** – wallet interfaces plus helper DSLs for credential organization.
- **Schema/Proof Utilities** – schema validation helpers and proof-purpose validator.
- **Core Exceptions & Constants** – `TrustWeaveException`, `TrustWeaveConstants`, and related domain errors.
- **Error Handling** – structured error types (`TrustWeaveError`) with context and Result utilities.
- **Input Validation** – validation utilities for DIDs, credentials, and chain IDs.

Add the module alongside any DID/KMS components you require:

```kotlin
dependencies {
    implementation("com.trustweave:trustweave-common:1.0.0-SNAPSHOT")
}
```

**Result:** Gradle exposes the credential domain APIs so you can build flows like issuing and storing credentials:

```kotlin
import com.trustweave.core.*

// Issue credential with error handling
val result = TrustWeave.issueCredential(
    issuerDid = issuerDid,
    issuerKeyId = keyId,
    credentialSubject = subjectJson
)

result.fold(
    onSuccess = { credential -> println("Issued: ${credential.id}") },
    onFailure = { error ->
        when (error) {
            is TrustWeaveError.CredentialInvalid -> {
                println("Credential invalid: ${error.reason}")
            }
            else -> println("Error: ${error.message}")
        }
    }
)

// Or use getOrThrow for simple cases
val credential = result.getOrThrow()
```

**Why it matters:** `TrustWeave-common` centralises the issuance and wallet DSLs; pulling it into your project gives direct access to the domain objects and helper functions used across the tutorials. All operations return `Result<T>` with structured error handling.

## Dependencies

- Includes SPI interfaces for adapter/service abstractions.
- Depends on [`TrustWeave-trust`](core-modules.md) for trust registry APIs.
- Upstream modules (`TrustWeave-did`, `TrustWeave-anchor`, etc.) layer additional functionality on top.

## Next Steps

- SPI interfaces are included in this module. See [SPI Documentation](../advanced/spi.md) to understand adapter/service expectations.
- Explore [`TrustWeave-trust`](core-modules.md) for trust registry runtime components.
- See [`TrustWeave-json`](trustweave-json.md) and [`TrustWeave-kms`](trustweave-kms.md) for supporting utilities.
