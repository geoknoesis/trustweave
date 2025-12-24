---
title: trustweave-trust
---

# trustweave-trust

The `trustweave-trust` module provides the trust-layer building blocks that sit
between credential workflows and registry/provider integrations.

```kotlin
dependencies {
    implementation("org.trustweave:trustweave-trust:1.0.0-SNAPSHOT")
}
```

**Result:** Your project gains access to trust registries, trust path models, and helper DSLs used by the higher-level credential flows.

## Responsibilities

- Trust registry interfaces (`TrustRegistry`, `TrustAnchorMetadata`,
  `TrustPathResult`).
- Support code for trust-layer DSLs (`trustweave-common` delegates to this module).
- Shared contract for integration modules to expose trust anchors and trust
  resolution logic.

## Usage

```kotlin
import org.trustweave.trust.TrustRegistry
import org.trustweave.trust.TrustAnchorMetadata

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
`TrustRegistryServiceAdapter` (included in `trustweave-common`).

## Dependencies

- Depends on [`
