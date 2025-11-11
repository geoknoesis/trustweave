# vericore-core

The `vericore-core` module now focuses on credential-domain functionality:

- Credential issuance and verification services
- Credential lifecycle DSLs (`credential.dsl.*`)
- Wallet abstractions and in-memory helpers
- Proof, schema, and revocation utilities used by higher layers

Infrastructure concerns (service adapters, plugin registry, trust runtime) have
been extracted into the new `vericore-spi` and `vericore-trust` modules.

## Key Components

- **Credential DSLs** – builders for issuing, storing, and verifying credentials.
- **Credential Models** – canonical data models for credentials, presentations, and proofs.
- **Wallet APIs** – wallet interfaces plus helper DSLs for credential organization.
- **Schema/Proof Utilities** – schema validation helpers and proof-purpose validator.
- **Core Exceptions & Constants** – `VeriCoreException`, `VeriCoreConstants`, and related domain errors.

Add the module alongside any DID/KMS components you require:

```kotlin
dependencies {
    implementation("com.geoknoesis.vericore:vericore-core:1.0.0-SNAPSHOT")
}
```

**Result:** Gradle exposes the credential domain APIs so you can build flows like issuing and storing credentials:

```kotlin
val credential = vericore.issueCredential(
    issuerDid = issuerDid,
    issuerKeyId = keyId,
    credentialSubject = subjectJson
).getOrThrow()
```

**Why it matters:** `vericore-core` centralises the issuance and wallet DSLs; pulling it into your project gives direct access to the domain objects and helper functions used across the tutorials.

## Dependencies

- Depends on [`vericore-spi`](core-modules.md) for adapter/service abstractions.
- Depends on [`vericore-trust`](core-modules.md) for trust registry APIs.
- Upstream modules (`vericore-did`, `vericore-anchor`, etc.) layer additional functionality on top.

## Next Steps

- Review [`vericore-spi`](core-modules.md) to understand adapter/service expectations.
- Explore [`vericore-trust`](core-modules.md) for trust registry runtime components.
- See [`vericore-json`](vericore-json.md) and [`vericore-kms`](vericore-kms.md) for supporting utilities.
