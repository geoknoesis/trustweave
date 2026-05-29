---
title: trust
redirect_from:
  - /modules/trustweave-trust/

---

# trust

The `trust` module hosts the main `TrustWeave` facade and the trust-layer
building blocks that sit between credential workflows and registry/provider
integrations.

```kotlin
dependencies {
    implementation("org.trustweave:trust:0.6.0")
}
```

**Result:** Your project gains access to the `TrustWeave` facade, trust
registries, trust path models, and helper DSLs used by the higher-level
credential flows.

## Responsibilities

- Main facade (`org.trustweave.trust.TrustWeave`) wired together via
  `TrustWeave.build { ... }` / `TrustWeave.inMemory()` / `TrustWeave.quickStart()`.
- Trust registry interfaces (`TrustRegistry`, `TrustAnchorMetadata`,
  sealed `TrustPath` for path discovery; type-safe identity wrappers
  `IssuerIdentity`, `VerifierIdentity`, `HolderIdentity` in
  `org.trustweave.trust.types`).
- Trust-layer DSLs in `org.trustweave.trust.dsl` (credential builders,
  DID builders, wallet builders).
- Shared contract for integration modules to expose trust anchors and trust
  resolution logic.

## Usage

```kotlin
import org.trustweave.did.identifiers.Did
import org.trustweave.trust.TrustRegistry
import org.trustweave.trust.TrustAnchorMetadata
import org.trustweave.trust.types.IssuerIdentity
import org.trustweave.trust.types.VerifierIdentity

suspend fun seedTrust(registry: TrustRegistry) {
    registry.addTrustAnchor(
        anchorDid = "did:key:issuer",
        metadata = TrustAnchorMetadata(
            credentialTypes = listOf("EducationCredential")
        )
    )

    // Type-safe trust path discovery
    val path = registry.findTrustPath(
        VerifierIdentity(Did("did:key:verifier")),
        IssuerIdentity(Did("did:key:issuer"))
    )
}
```

**What this does:** Adds a trust anchor to the registry so future verifications
can confirm whether `EducationCredential` issuers are trusted, and discovers a
trust path between a verifier and an issuer using type-safe identity wrappers
(`IssuerIdentity` / `VerifierIdentity`, **not** raw `Did` arguments).

**Outcome:** Downstream calls to `trustRegistry.findTrustPath(...)` or
trust-aware verifiers succeed without requiring direct issuer configuration in
each workflow.

In-memory implementations live in the `testkit` module (`InMemoryTrustRegistry`).

## Dependencies

- Depends on [`common`](trustweave-common.md), [`did:did-core`](trustweave-did.md),
  [`kms:kms-core`](trustweave-kms.md), [`anchors:anchor-core`](trustweave-anchor.md),
  [`credentials:credential-api`](../README.md), and the wallet
  modules (`wallet:wallet-core`, `wallet:wallet-services`).

## Next Steps

- See [`testkit`](trustweave-testkit.md) for in-memory implementations
  (`InMemoryTrustRegistry`, `InMemoryKeyManagementService`, etc.).
- Browse the trust DSLs under `org.trustweave.trust.dsl` for issuance,
  verification, presentation, wallet, and DID builders.
