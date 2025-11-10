# vericore-trust

The `vericore-trust` module provides the trust-layer building blocks that sit
between credential workflows and registry/provider integrations.

## Responsibilities

- Trust registry interfaces (`TrustRegistry`, `TrustAnchorMetadata`,
  `TrustPathResult`).
- Support code for trust-layer DSLs (`vericore-core` delegates to this module).
- Shared contract for integration modules to expose trust anchors and trust
  resolution logic.

## Usage

```kotlin
import io.geoknoesis.vericore.trust.TrustRegistry
import io.geoknoesis.vericore.trust.TrustAnchorMetadata

suspend fun seedTrust(registry: TrustRegistry) {
    registry.addTrustAnchor(
        anchorDid = "did:key:issuer",
        metadata = TrustAnchorMetadata(
            credentialTypes = listOf("EducationCredential")
        )
    )
}
```

Implementations can be provided by testkits (`InMemoryTrustRegistry`) or custom
modules; they can be wrapped as `TrustRegistryService` via
`vericore-spi`'s `TrustRegistryServiceAdapter`.

## Dependencies

- Depends on [`vericore-spi`](vericore-spi.md) for adapter/service abstractions.
- Consumed by [`vericore-core`](vericore-core.md) trust DSLs and by test kits.

## Next Steps

- Review [`vericore-testkit`](vericore-testkit.md) for the in-memory trust
  registry implementation.
- Update custom integrations to target the new module when wiring trust
  registries.

