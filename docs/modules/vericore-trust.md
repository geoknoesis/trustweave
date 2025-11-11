# vericore-trust

The `vericore-trust` module provides the trust-layer building blocks that sit
between credential workflows and registry/provider integrations.

```kotlin
dependencies {
    implementation("com.geoknoesis.vericore:vericore-trust:1.0.0-SNAPSHOT")
}
```

**Result:** Your project gains access to trust registries, trust path models, and helper DSLs used by the higher-level credential flows.

## Responsibilities

- Trust registry interfaces (`TrustRegistry`, `TrustAnchorMetadata`,
  `TrustPathResult`).
- Support code for trust-layer DSLs (`vericore-core` delegates to this module).
- Shared contract for integration modules to expose trust anchors and trust
  resolution logic.

## Usage

```kotlin
import com.geoknoesis.vericore.trust.TrustRegistry
import com.geoknoesis.vericore.trust.TrustAnchorMetadata

suspend fun seedTrust(registry: TrustRegistry) {
    registry.addTrustAnchor(
        anchorDid = "did:key:issuer",
        metadata = TrustAnchorMetadata(
            credentialTypes = listOf("EducationCredential")
        )
    )
}
```

**What this does:** Adds a trust anchor to the registry so future verifications can confirm whether `EducationCredential` issuers are trusted.

**Outcome:** Downstream calls to `trustRegistry.resolve()` or trust-aware verifiers succeed without requiring direct issuer configuration in each workflow.

Implementations can be provided by testkits (`InMemoryTrustRegistry`) or custom
modules; they can be wrapped as `TrustRegistryService` via
`vericore-spi`'s `TrustRegistryServiceAdapter`.

## Dependencies

- Depends on [`