---
title: Quick Reference
nav_order: 10
parent: API Reference
keywords:
  - quick reference
  - api methods
  - methods
  - lookup
  - cheat sheet
---

# API Quick Reference

Quick lookup for common **TrustWeave** (`trustWeave`) operations. For full signatures and edge cases, see **[Core API](core-api.md)**.

## DIDs

| Method | Description | Link |
|--------|-------------|------|
| `createDid { }` | Create a new DID (returns `DidCreationResult`) | [createDid()](core-api.md#create) |
| `resolveDid(did)` | Resolve a DID (`String` or `Did`) → `DidResolutionResult` | [resolveDid()](core-api.md#resolve) |
| `updateDid { }` | Update a DID document → `DidDocument` | [updateDid()](core-api.md#update) |
| `createDidWithKey { }` | Create DID and expose key id → `DidCreationWithKeyResult` | [core-api.md](core-api.md) |

## Credentials

| Method | Description | Link |
|--------|-------------|------|
| `issue { }` | Issue a VC → `IssuanceResult` | [issue()](core-api.md#issue) |
| `verify(credential)` | Verify with defaults → `VerificationResult` | [verify()](core-api.md#verify) |
| `verify { }` | Verify with DSL (revocation, schema, trust policy, …) | [verify()](core-api.md#verify) |
| `presentationResult { }` | Build a VP → `PresentationResult` | [create-presentations.md](../how-to/create-presentations.md) |

## Wallets

| Method | Description | Link |
|--------|-------------|------|
| `wallet { }` | Create a wallet → `WalletCreationResult` | [wallet()](core-api.md#wallet) |

Wallet storage/query live on the **`Wallet`** instance after a successful result (see [Wallet API](wallet-api.md)).

## Blockchain

Use **`trustWeave.blockchains`** (e.g. `anchor`, `read`) — not a top-level `anchor { }` on the facade. See [Core API — Blockchain](core-api.md) and [quick start](../tutorials/getting-started/quick-start.md).

## Trust registry

| Method | Description |
|--------|-------------|
| `trust { }` | Trust DSL: `addAnchor`, `isTrusted`, `findTrustPath`, `getTrustedIssuers`, … |

Requires `trust { provider(...) }` (or an explicit trust registry factory) in **`TrustWeave.build { }`**.

## Delegation & keys

| Method | Description |
|--------|-------------|
| `delegate { }` | Delegation chain check → `DelegationChainResult` |
| `rotateKey { }` | Key rotation → `DidDocument` |
| `revoke(timeout) { }` | Revocation DSL |

---

## Snippets (current API)

### Create DID

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.getOrThrowDid

val trustWeave: TrustWeave = // TrustWeave.build { ... } or quickStart()
val issuerDid = trustWeave.createDid {
    method("key")
    algorithm("Ed25519")
}.getOrThrowDid()
```

### Resolve DID

```kotlin
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.errorMessage

when (val res = trustWeave.resolveDid(Did("did:key:example"))) {
    is DidResolutionResult.Success -> println(res.document.id)
    is DidResolutionResult.Failure -> println(res.errorMessage)
}
```

### Update DID

```kotlin
import org.trustweave.trust.dsl.credential.DidMethods

trustWeave.updateDid {
    did("did:key:example")
    method(DidMethods.KEY)
    addService { /* ... */ }
}
```

### Issue credential

```kotlin
import org.trustweave.trust.types.getOrThrow
import org.trustweave.credential.results.getOrThrow
import kotlinx.datetime.Clock

val credential = trustWeave.issue {
    credential {
        type("PersonCredential")
        issuer(issuerDid)
        subject { id("did:key:holder"); "name" to "Alice" }
        issued(Clock.System.now())
    }
    signedBy(issuerDid) // key id derived when configured
}.getOrThrow()
```

### Verify credential

```kotlin
import org.trustweave.credential.results.VerificationResult

val result = trustWeave.verify(credential)

when (result) {
    is VerificationResult.Valid -> println("Valid")
    is VerificationResult.Invalid -> println(result.allErrors.joinToString())
}
```

Optional: **`import org.trustweave.trust.types.*`** for ergonomic helpers (**`result.valid`**, **`result.trustRegistryValid`**, **`result.errors`** as an alias of **`allErrors`**, etc.) on **`VerificationResult`**.

### Wallet

```kotlin
import org.trustweave.trust.types.getOrThrow
import org.trustweave.credential.results.getOrThrow

val wallet = trustWeave.wallet {
    holder("did:key:holder")
    enableOrganization()
    enablePresentation()
}.getOrThrow()
```

Requires a **`WalletFactory`** on the **`TrustWeave`** configuration (e.g. testkit in tests).

### Trust anchors

```kotlin
trustWeave.trust {
    addAnchor(issuerDid.value) {
        credentialTypes("EducationCredential")
        description("Trusted university")
    }
    val ok = isTrusted(issuerDid.value, "EducationCredential")
}
```

### Delegation

```kotlin
val chain = trustWeave.delegate {
    from(delegatorDid.value)
    to(delegateDid.value)
}
```

### Key rotation

```kotlin
import org.trustweave.trust.dsl.credential.KeyAlgorithms

trustWeave.rotateKey {
    did("did:key:example")
    oldKeyId("did:key:example#key-1")
    newAlgorithm(KeyAlgorithms.ED25519)
}
```

## Related documentation

- **[Core API](core-api.md)**
- **[Wallet API](wallet-api.md)**
- **[Result types](result-types-guide.md)**
- **[Error handling](advanced/error-handling.md)**
