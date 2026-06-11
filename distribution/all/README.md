# TrustWeave Distribution Module

This module provides all essential TrustWeave dependencies in a single dependency declaration.

## Usage

Instead of adding multiple dependencies individually, use the distribution module:

```kotlin
dependencies {
    implementation("org.trustweave:distribution-all:0.6.0")
}
```

## What's Included

The all-in-one module aggregates the following core modules as `api` dependencies:

- ✅ `common` — shared utilities, exceptions, plugin infrastructure
- ✅ `trust` — main facade (`TrustWeave.kt`)
- ✅ `contract` — smart contract interfaces
- ✅ `kms:kms-core` — key management abstraction
- ✅ `did:did-core` — DID management
- ✅ `wallet:wallet-core` — wallet abstraction
- ✅ `anchors:anchor-core` — blockchain anchoring abstraction
- ✅ `credentials:credential-api` — VC issuance/verification (built-in VC-LD and SD-JWT-VC engines)
- ✅ `testkit` — in-memory test doubles (handy during development)

## What's NOT Included

Optional plugin modules must be added explicitly if needed. These include:

- ❌ DID method plugins (`did:plugins:key`, `did:plugins:web`, `did:plugins:ethr`, `did:plugins:jwk`, `did:plugins:ebsi`, …)
- ❌ KMS plugins (`kms:plugins:aws`, `kms:plugins:azure`, `kms:plugins:inmemory`, …)
- ❌ Blockchain anchor plugins (`anchors:plugins:algorand`, `anchors:plugins:polygon`, `anchors:plugins:ethereum`, `anchors:plugins:ganache`, `anchors:plugins:indy`, …)
- ❌ Wallet storage plugins (`wallet:plugins:database`, `wallet:plugins:file`, `wallet:plugins:cloud`)
- ❌ Credential protocol/format plugins (`credentials:plugins:oidc4vci`, `credentials:plugins:oidc4vp`, `credentials:plugins:didcomm`, `credentials:plugins:chapi`, `credentials:plugins:presentation-exchange`, `credentials:plugins:siop`, `credentials:plugins:mdl`, `credentials:plugins:bbs`, `credentials:plugins:verifiable-intent`, `credentials:plugins:openid-federation`, `credentials:plugins:eudiw`, `credentials:plugins:status-list:*`, `credentials:plugins:anchor`, `credentials:plugins:platforms:*`)

### Adding Optional Modules

```kotlin
dependencies {
    implementation("org.trustweave:distribution-all:0.6.0")

    // Add blockchain adapters as needed
    implementation("org.trustweave:anchors-plugins-algorand:0.6.0")
}
```

## Quick Start

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.getOrThrowDid
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Quick start: in-memory KMS + did:key, ready to go
    val trustWeave = TrustWeave.quickStart()

    val did = trustWeave.createDid().getOrThrowDid()
    println("Created DID: ${did.value}")
}
```

Or with explicit configuration via the DSL:

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.getOrThrowDid
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys { provider("inMemory"); algorithm("Ed25519") }
        did { method("key") { algorithm("Ed25519") } }
    }
    val did = trustWeave.createDid().getOrThrowDid()
    println("Created DID: ${did.value}")
}
```

See the main [README](../../README.md) for more examples and documentation.

