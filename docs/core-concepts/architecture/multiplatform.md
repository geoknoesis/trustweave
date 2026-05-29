---
redirect_from:
  - /architecture/multiplatform/
parent: Architecture
grand_parent: Core Concepts
---

# Multiplatform Module Organization

TrustWeave is migrating from a JVM-only codebase toward Kotlin Multiplatform
(KMP) so the portable parts of the SDK — identifiers, data models, validators,
and protocol envelopes — can run on JVM, Android, iOS, and JS without
duplication. This document explains how the multiplatform modules are laid out,
why they are split the way they are, and how they interact with the existing
JVM modules.

The migration is incremental ("Strategy A"). New portable modules are added
side-by-side with a `-mp` suffix; the existing JVM modules become thin wrappers
that re-export the portable types. **Consumers do not change their imports** —
the package paths are preserved across the move.

## Naming Convention

| Suffix | Target | Purpose |
|---|---|---|
| `-mp` | KMP (JVM today, iOS/JS/native tomorrow) | Portable types and pure logic |
| (none) | JVM only | JVM glue, framework code, integrations |

Examples:

| Multiplatform module | JVM wrapper |
|---|---|
| `:common-mp` | `:common` |
| `:did:did-identifiers-mp` | `:did:did-core` |
| `:credentials:credential-models-mp` | `:credentials:credential-api` |
| `:wallet:wallet-core-mp` | `:wallet:wallet-core` |

The JVM wrapper depends on the `-mp` module with `api(...)`, so a downstream
module that pulls in `:common` transitively gets every portable type exposed by
`:common-mp`. From the caller's point of view, nothing changed.

## Source Set Layout

Every `-mp` module follows the same skeleton:

```
common-mp/
├── build.gradle.kts             # kotlin("multiplatform")
└── src/
    ├── commonMain/kotlin/       # Portable code (the bulk)
    │   └── org/trustweave/core/
    │       ├── identifiers/
    │       ├── serialization/
    │       └── util/
    ├── commonTest/kotlin/       # Tests that run on every target
    └── jvmMain/kotlin/          # JVM-only convenience aliases (often empty)
```

The build script applies `applyDefaultHierarchyTemplate()` so adding `ios()`,
`js()`, or native targets later is a one-line change in `kotlin { ... }` and
does not require restructuring the source sets.

```kotlin
kotlin {
    applyDefaultHierarchyTemplate()
    jvm { /* JVM 21, -Xjsr305=strict */ }
    // Future: iosArm64(), iosX64(), iosSimulatorArm64(), js(IR), ...

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
            }
        }
    }
}
```

## Module Dependency Graph

```
                       ┌──────────────────────┐
                       │      common-mp       │  ← Iri, KeyId, ValidationResult,
                       │  (no module deps)    │    InstantSerializer
                       └──────────┬───────────┘
                                  │ api
                       ┌──────────▼───────────┐
                       │ did-identifiers-mp   │  ← Did, DidValidator,
                       │                      │    VerificationMethodId
                       └──────────┬───────────┘
                                  │ api
                       ┌──────────▼───────────┐
                       │ credential-models-mp │  ← VerifiableCredential,
                       │                      │    CredentialSubject, Issuer,
                       │                      │    ExchangeRequest/Response,
                       │                      │    ProofSuiteId, …
                       └──────────────────────┘

       wallet-core-mp ── (standalone scaffold; no MP deps yet)

JVM layer (api-re-exports the corresponding -mp module):

  :common              api(:common-mp)
  :did:did-core        api(:did:did-identifiers-mp)
  :credentials:
    credential-api     api(:credentials:credential-models-mp)
  :wallet:wallet-core  api(:wallet:wallet-core-mp)
```

`-mp` modules form their own DAG and never depend on a JVM-only module. JVM
modules depend on `-mp` modules and on each other, and they may depend on
JVM-only libraries (Spring, Ktor, BouncyCastle, …) that have no KMP equivalent.

## What Lives in Each `-mp` Module

### `common-mp` — primitives the whole framework needs

The smallest module. It holds types every domain references:

- `org.trustweave.core.identifiers` — `Iri`, `KeyId`, `VerificationMethodId`,
  and the foundation for all opaque identifiers.
- `org.trustweave.core.serialization` — `InstantSerializer`,
  `SerializationModule` (kotlinx.serialization registration helpers).
- `org.trustweave.core.util` — `ValidationResult`, generic input-validation
  infrastructure.

It deliberately does **not** contain anything that depends on
`java.util.ServiceLoader`, `java.time.*`, JVM exceptions, or
`kotlinx.serialization.json.JsonObject`-bound DSLs. Those stay in `:common`.

### `did-identifiers-mp` — DID as a portable identifier

DIDs are identifiers, not services — they parse, validate, compare, and
serialize without touching any I/O. Putting them in their own module lets a
mobile wallet hold and verify a DID without depending on the resolver
infrastructure.

- `org.trustweave.did.identifiers` — `Did`, `DidUrl`, `DidFragment` (extends
  `Iri` from `common-mp`).
- `org.trustweave.did.validation` — `DidValidator` (format checks per W3C DID
  Core).

DID *resolution*, registrars, and method plugins stay in `:did:did-core` and
the JVM-side plugin modules because they need HTTP clients, blockchain RPCs,
and JVM-only crypto libraries.

### `credential-models-mp` — VC data model and exchange protocol envelopes

The largest `-mp` module, because the W3C Verifiable Credentials data model is
intrinsically portable and is the type system that flows through every
client — issuer, holder/wallet, verifier.

- `org.trustweave.credential.model` / `model.vc` — `VerifiableCredential`,
  `CredentialSubject`, `Issuer`, `CredentialProof`, `CredentialStatus`,
  `Evidence`, `TermsOfUse`.
- `org.trustweave.credential.exchange` — protocol-neutral
  `ExchangeRequest` / `ExchangeResponse` / `ExchangeResult` /
  `CredentialExchangeProtocol` interface, registry, options, capabilities.
- `org.trustweave.credential.identifiers` — `CredentialId`,
  `ProtocolIdentifiers`.
- `org.trustweave.credential.format` — `ProofSuiteId`.

The JVM module `:credentials:credential-api` still owns the orchestration
services (`DefaultCredentialService`), the JSON-bound builders
(`CredentialSubjectBuilder`), and any code that depends on `kotlinx-json`
DSL or BouncyCastle.

### `wallet-core-mp` — portable wallet contract

Currently a scaffold containing the capability/options surface:

- `org.trustweave.wallet` — `WalletCapabilities`, `WalletType`,
  `WalletCreationOptions`.

The intent is to move the `Wallet` interface and credential collection types
into `commonMain` so a Kotlin/Native or Kotlin/JS host can implement a
wallet without depending on the JVM `:wallet:wallet-core` module. Storage
backends (`database`, `file`, `cloud`) remain JVM plugins.

## Rationale — Why This Breakdown?

### 1. Separate "portable types" from "platform glue"

The classic mistake in adopting KMP late in a project's life is to convert an
existing JVM module to multiplatform and then discover that 30% of its files
need `expect`/`actual` declarations or have to be deleted. We avoid that by
moving only files that are *already* portable into the `-mp` module on day one
and leaving everything else where it is.

A file is portable when it depends only on:

- Kotlin stdlib
- `kotlinx-serialization`
- `kotlinx-coroutines`
- `kotlinx-datetime`
- Other `-mp` modules in the TrustWeave tree

If it touches `java.time`, `java.util.ServiceLoader`, BouncyCastle, Ktor,
Spring, JDBC, file I/O, or any provider-specific SDK, it stays in the JVM
module. No `expect`/`actual` is required for v1.

### 2. Preserve package paths across the move

Every type moved into a `-mp` module keeps its original package name. `Did`
stays at `org.trustweave.did.identifiers.Did`. `VerifiableCredential` stays at
`org.trustweave.credential.model.vc.VerifiableCredential`. Combined with the
`api(":...-mp")` re-export in the JVM wrapper, consumer code does not change
at all — neither the imports nor the `build.gradle.kts` dependency
coordinates. This is the central reason the migration can ship incrementally
without coordinating with downstream apps.

### 3. Five small `-mp` modules instead of one giant one

A single `commons-multiplatform` would force every consumer to drag in
identifiers, VC models, exchange protocols, and wallet types together. The
five-module split lets a verifier-only client depend on
`credential-models-mp` (+ its transitive `did-identifiers-mp` and
`common-mp`) without pulling in wallet types it does not use. It also lets
the modules adopt new targets independently: `common-mp` can ship to
iOS/Native before `credential-models-mp` is ready.

The split mirrors the **domain boundaries** already used on the JVM side
(common / did / credentials / wallet), so the mental map a developer already
has for the JVM tree applies unchanged.

### 4. Dependencies form a DAG, not a web

`common-mp` ⟵ `did-identifiers-mp` ⟵ `credential-models-mp`. There are no
cycles and no diamonds. `wallet-core-mp` is currently isolated (no `-mp`
dependencies) so it can evolve without coupling to the credential layer; if a
wallet ever needs to hold typed VC objects in `commonMain`, the dependency
will be added explicitly, not pulled in by accident.

### 5. JVM modules as thin adapters

`:common`, `:did:did-core`, `:credentials:credential-api`,
`:wallet:wallet-core` retain their existing identities and continue to host:

- DSL builders bound to `kotlinx-json` (e.g.
  [CredentialSubjectBuilder](../../../credentials/credential-api/src/main/kotlin/org/trustweave/credential/model/vc/CredentialSubjectBuilder.kt))
- JVM-only exception hierarchy (`TrustWeaveException` and friends)
- `ServiceLoader`-based plugin discovery
  ([PluginRegistry](../../../common/src/main/kotlin/org/trustweave/core/plugin/PluginRegistry.kt))
- JSON DSL helpers like
  [`jsonData { }`](../../../common/src/main/kotlin/org/trustweave/core/json/JsonData.kt)
  (depends on `kotlinx.serialization.json.JsonObject`, which *is* portable —
  this helper could move to `common-mp` in a future step)
- HTTP clients, blockchain SDKs, persistence layers

The wrapper modules' role is to add JVM affordances on top of portable types,
not to redefine them.

### 6. `jvmMain` is a deliberate placeholder

Every `-mp` module ships an empty `jvmMain/kotlin/` directory (marked with
`.gitkeep`). It exists to host JVM-only convenience aliases — for example,
extension functions that convert a `kotlinx.datetime.Instant` from
`commonMain` into a `java.time.Instant` — without forcing those aliases into
`commonMain`. Today most `jvmMain` directories are empty; that is the correct
state until a real JVM-specific affordance is needed.

## Adding a New Type — Where Does It Go?

Use this decision table:

| Question | If yes → | If no → |
|---|---|---|
| Does the type only depend on Kotlin stdlib + `kotlinx-*`? | Continue. | Goes in the JVM module. |
| Is it a data model, identifier, validator, or protocol envelope? | Goes in `commonMain` of the matching `-mp` module. | Continue. |
| Is it a service that orchestrates I/O, crypto, or plugin loading? | Goes in the JVM module. | |
| Is it a DSL builder bound to `JsonObjectBuilder`, `Map<String, Any?>`, or JVM types? | Goes in the JVM module. | |
| Does it use `java.util.ServiceLoader`, `java.time.*`, BouncyCastle, Ktor, or Spring? | Goes in the JVM module. | |

When in doubt, **start in the JVM module**. Promoting a class to `-mp` later
is a one-commit move because the package path stays the same; demoting a
class that turned out to need JVM APIs is more expensive.

## Adding a New Target (iOS / JS / Native)

Because `applyDefaultHierarchyTemplate()` is already in place, adding a new
target is mostly mechanical:

1. Add the target to the `kotlin { }` block in the `-mp` module's
   `build.gradle.kts` (e.g. `iosArm64()`, `iosSimulatorArm64()`, `js(IR)`).
2. Run `./gradlew :common-mp:check` and fix any reference that turns out to
   need an `expect`/`actual` split (typically nothing in v1, since the code
   was screened for portability up front).
3. Add target-specific tests under `src/iosTest/kotlin/`, etc.

No source rearrangement is needed; no consumer of the JVM module is affected.

## Current Status

The four `-mp` modules listed above exist, contain real (non-scaffold) code,
and are referenced via `api(...)` from their JVM wrappers. The next
candidates for promotion to a `-mp` module are:

- **`anchor-core`** — `AnchorRef`, `ChainId`, anchor metadata types are
  portable; the blockchain plugins stay JVM.
- **`contract` models** — `SmartContract`, `ContractStatus`,
  `ExecutionContext`, and the `jsonData { }` / `executionContext { }` /
  `contractDraft { }` DSL builders are pure data and could move to
  `contract-models-mp`. Evaluation engines (which load classes for hash
  computation) stay JVM.
- **`kms-core`** — interfaces like `KeyManagementService`, `KeyId`,
  `KeyAlgorithm` are portable; the provider plugins (AWS, Azure, …) stay JVM.

Each promotion follows the same recipe: create the `-mp` module, move portable
files preserving package paths, change the JVM module to `api(":...-mp")`,
verify with `./gradlew check`.

## See Also

- [Clean Architecture in TrustWeave](CLEAN_ARCHITECTURE.md) — layering rules
  that the `-mp` / JVM split also respects (entities and use-case interfaces
  belong in `-mp`; framework adapters belong in JVM wrappers).
- [`settings.gradle.kts`](../../../settings.gradle.kts) — the full module list
  and the canonical home of the "Strategy A" comment.
