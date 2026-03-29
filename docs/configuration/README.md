---
title: Configuration Reference
nav_exclude: true
---

# Configuration Reference

How to configure TrustWeave for development, tests, and production. For what you get out of the box, see [Default configuration](defaults.md).

## Configuration entry points

1. **`TrustWeave.quickStart()`** (suspend) — In-memory KMS, `did:key`, test-friendly defaults. Same as **`TrustWeave.inMemory()`**; use whichever reads better.
2. **`TrustWeave.build { }`** (suspend) — Primary way to set KMS, DID methods, anchors, factories, and credential defaults.
3. **`TrustWeave.from(config)`** — Wrap an existing **`TrustWeaveConfig`**. The config type has an **`internal`** constructor; application code should obtain it only via **`TrustWeave.build`** (or test utilities), not by calling the constructor directly.

All **`build`** / **`quickStart`** examples assume a **`suspend`** context or **`runBlocking { ... }`**.

```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.trust.TrustWeave

fun main() = runBlocking {
    val trustWeave = TrustWeave.quickStart()
    // or: val trustWeave = TrustWeave.build { /* ... */ }
}
```

## Configuration components

### Key management (KMS)

**Purpose:** Signing keys for DIDs and credentials.

**Default:** In-memory KMS (testing only).

**Production:** Prefer a KMS provider your **`keys { provider("…") { … } }`** block can resolve, or pass an instance with **`customKms(kms)`**.

```kotlin
TrustWeave.build {
    customKms(awsKms) // or keys { provider("awsKms") { /* provider-specific options */ } }
    did { method("key") { algorithm("Ed25519") } }
}
```

**See also:** [KMS integrations](../integrations/README.md#key-management-service-kms-integrations), [Key management](../core-concepts/key-management.md).

### DID methods

**Purpose:** Create and resolve DIDs.

**Default:** `did:key` when you do not declare other methods.

**Configuration:** Declare methods in the **`did { }`** block. Additional methods are also picked up from classpath **SPI** when registered by plugins.

```kotlin
TrustWeave.build {
    did {
        method("web") { domain("example.com") }
        method("key") { algorithm("Ed25519") }
    }
}
```

**See also:** [DID integrations](../integrations/README.md#did-method-integrations), [DIDs](../core-concepts/dids.md), [DID method plugins](../plugins.md#did-method-plugins).

### Blockchain anchoring

**Purpose:** Anchor payloads to chains (CAIP-2 chain IDs).

**Default:** No chains until you add them.

```kotlin
import org.trustweave.trust.dsl.credential.AnchorProviders

TrustWeave.build {
    anchor {
        chain("algorand:testnet") {
            provider(AnchorProviders.ALGORAND)
            options { /* e.g. "algodUrl" to "…", "privateKey" to "…" */ }
        }
        chain("ethereum:mainnet") {
            provider(AnchorProviders.ETHEREUM)
            options { /* RPC URL, credentials, etc. */ }
        }
    }
}
```

For local tests, **`inMemory()`** inside **`chain("…") { }`** is available (see [Default configuration](defaults.md)).

**See also:** [Blockchain integrations](../integrations/README.md#blockchain-anchor-integrations), [Blockchain anchoring](../core-concepts/blockchain-anchoring.md), [Blockchain plugins](../plugins.md#blockchain-anchor-plugins).

### Wallet factory

**Purpose:** Create holder wallets.

**Default:** In-memory / testkit-oriented factory unless you override.

```kotlin
TrustWeave.build {
    factories(walletFactory = databaseWalletFactory)
    did { method("key") { algorithm("Ed25519") } }
}
```

**See also:** [Wallet API](../api-reference/wallet-api.md), [Wallets](../core-concepts/wallets.md).

### Credential service and proof defaults

**Purpose:** Issuance/verification pipeline and defaults (proof type, auto-anchor, default chain).

The builder wires a default credential service from KMS + DID resolution unless you set **`credentialService(...)`**.

```kotlin
TrustWeave.build {
    credentialService(myCredentialService) // optional override
    credentials {
        defaultProofType("Ed25519Signature2020")
        autoAnchor(true)
        defaultChain("algorand:testnet")
    }
    did { method("key") { algorithm("Ed25519") } }
}
```

**See also:** [Credential service API](../api-reference/credential-service-api.md), [Verifiable credentials](../core-concepts/verifiable-credentials.md).

## Validation

Configuration is resolved when **`TrustWeave.build`** runs. Failures surface as **`IllegalStateException`** or other domain exceptions (e.g. unknown provider). Handle them at application startup:

```kotlin
import org.trustweave.trust.TrustWeave
import kotlinx.coroutines.runBlocking

val result = runCatching {
    runBlocking {
        TrustWeave.build {
            anchor {
                chain("unknown:chain") { provider("no-such-provider") }
            }
        }
    }
}

result.onFailure { error ->
    println("Configuration failed: ${error.message}")
}
```

For **`TrustWeaveException.ValidationFailed`** and other error types, see [Error handling](../advanced/error-handling.md).

## Environment-oriented examples

### Development

```kotlin
val trustWeave = TrustWeave.quickStart()
```

### Integration tests (in-memory anchor chains)

```kotlin
TrustWeave.build {
    anchor {
        chain("inmemory:test-a") { inMemory() }
        chain("inmemory:test-b") { inMemory() }
    }
    did { method("key") { algorithm("Ed25519") } }
}
```

### Production-shaped sketch

```kotlin
TrustWeave.build {
    customKms(awsKms)
    did {
        method("web") { domain("yourcompany.com") }
    }
    anchor {
        chain("algorand:mainnet") {
            provider("algorand")
            options {
                "algodUrl" to System.getenv("ALGOD_URL")
                "privateKey" to System.getenv("ALGORAND_PRIVATE_KEY")
            }
        }
    }
    factories(walletFactory = DatabaseWalletFactory(dataSource))
}
```

## Best practices

1. **Secrets:** Load URLs and keys from the environment or a secret manager, not source control.
2. **Fail fast:** Build **`TrustWeave`** at startup so misconfiguration is obvious.
3. **Close resources:** Call **`trustWeave.close()`** when shutting down (KMS and clients may hold connections). For plugin-specific lifecycle, see [Plugin lifecycle](../advanced/plugin-lifecycle.md).

## Related documentation

- [Default configuration](defaults.md)
- [Architecture overview](../introduction/architecture-overview.md)
- [Production checklist](../deployment/production-checklist.md)
- [Installation](../getting-started/installation.md)
