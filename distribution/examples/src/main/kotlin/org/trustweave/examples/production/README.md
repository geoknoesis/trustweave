# Production Wiring Example

Demonstrates production-shaped `TrustWeave.build { ... }` wiring using:

- Type-safe provider constants (`KmsProviders`, `KeyAlgorithms`, `DidMethods`, `TrustProviders`, `RevocationProviders`)
- Explicit factory injection (`factories(trustRegistryFactory = ..., walletFactory = ..., statusListRegistryFactory = ...)`)
- Sealed result handling (`IssuanceResult`, `VerificationResult`, `RevocationResult`, `TrustOperationResult`)
- Non-null KMS access (`trustWeave.requireKms()`) and coroutine-safe shutdown (`trustWeave.closeAsync()`)

The runnable sample uses in-memory testkit factories so it executes standalone without hosted infrastructure. In production, swap `KmsProviders.IN_MEMORY` for a cloud/HSM KMS provider (`KmsProviders.AWS`, `KmsProviders.AZURE`, `KmsProviders.GOOGLE`, or `KmsProviders.HASHICORP`) and supply database- or cloud-backed factories.

## Run

```bash
./gradlew :distribution:examples:compileKotlin
```

See [ProductionWiringExample.kt](ProductionWiringExample.kt) for the full source.
