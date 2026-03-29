---
title: Default Configuration
---

# Default Configuration

This document explains the **default configuration** you get from **`TrustWeave.quickStart()`** / **`TrustWeave.build { }`** and how to customize it.

## Default Configuration Overview

**`TrustWeave.quickStart()`** wires an in-memory KMS, **`did:key`**, and credential services suitable for demos. **`TrustWeave.build { }`** uses the same style of defaults for any block you omit (see factory behavior in source / [Installation](../getting-started/installation.md)).

```kotlin
import org.trustweave.trust.TrustWeave

val trustWeave = TrustWeave.quickStart() // in-memory did:key — see quick start
```

### Default Components

| Component | Default Implementation | Purpose | Production Ready? |
|-----------|----------------------|---------|-------------------|
| **KMS** | `InMemoryKeyManagementService` | In-memory key storage | ❌ Testing only |
| **DID Method** | `DidKeyMockMethod` (did:key) | DID creation and resolution | ❌ Testing only |
| **Wallet Factory** | `TestkitWalletFactory` | Wallet creation | ❌ Testing only |
| **Blockchain Clients** | `BlockchainAnchorRegistry()` (empty) | Blockchain anchoring | ⚠️ Must be configured |
| **Credential service** | `CredentialServices.createCredentialService(...)` / `credentialService(...)` (wired by `TrustWeave` factory) | Issuance / verification | ✅ Default in-memory chain for demos |
| **Proof engines** | Built-in VC-LD / SD-JWT (see `credential-api`) | Cryptographic proofs | ✅ Configure via `IssuanceRequest` / trust DSL |

## What Gets Configured Automatically

### Included by Default

1. **In-Memory KMS**
   - Generates Ed25519 keys
   - Stores keys in memory (lost on restart)
   - Suitable for testing only

2. **did:key Method**
   - Registered automatically
   - Creates DIDs with format: `did:key:z6Mk...`
   - No external dependencies
   - Suitable for testing only

3. **In-Memory Wallet Factory**
   - Creates in-memory wallets
   - Credentials stored in memory (lost on restart)
   - Suitable for testing only

4. **Ed25519 Proof Generator**
   - Generates Ed25519Signature2020 proofs
   - Production-ready algorithm
   - Suitable for production use

### Not Included by Default

1. **Blockchain Clients**
   - No blockchain clients registered until you add them
   - Register clients inside **`TrustWeave.build { anchor { chain("algorand:testnet") { provider(ALGORAND) { ... } } } }`**

2. **Additional DID Methods**
   - Only `did:key` unless you register more
   - Add methods in **`did { method(WEB) { ... } }`** (or SPI-discovered plugins)

3. **Production KMS**
   - In-memory KMS is for testing only
   - Configure **`keys { provider("awsKms") { ... } }`** (or **`customKms(...)`**) for production

## Default Behavior Details

### DID Creation Defaults

```kotlin
import org.trustweave.testkit.services.*
// Default behavior (configured `did:key` + Ed25519 from TrustWeave.build)
val did = trustWeave.createDid { }.getOrThrowDid()

// Equivalent options are expressed in the `createDid { }` builder, e.g. method(KEY); algorithm(ED25519)
```

**Defaults:**
- Method: `"key"` (did:key)
- Algorithm: `ED25519`
- Key purposes: Empty (defaults to authentication and assertion)

### Credential Issuance Defaults

```kotlin
// Default behavior (DSL adds VerifiableCredential type where applicable)
val credential = trustWeave.issue {
    credential {
        issuer(issuerDid)
        subject { /* claims */ }
    }
    signedBy(issuerDid, issuerKeyId)
}.getOrThrow()
```

**Defaults:**
- Types: `["VerifiableCredential"]` (must include at least this)
- Expiration: `null` (no expiration)
- Proof type: `Ed25519Signature2020`

### Wallet Creation Defaults

```kotlin
// Default behavior
val wallet = trustWeave.wallet { holder("did:key:holder") }.getOrThrow()
```

**Defaults:**
- Wallet ID: Auto-generated UUID
- Provider: `InMemory`
- Organization: Disabled
- Presentation: Disabled
- Storage path: None (in-memory)

## Customizing Defaults

### Option 1: Builder DSL (Recommended)

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.AnchorProviders

// TrustWeave.build is suspend — call from runBlocking { } or another suspend entry point
val trustWeave = TrustWeave.build {
    customKms(awsKms) // or keys { provider("awsKms") { region("us-east-1"); ... } }

    did {
        method("web") { domain("example.com") }
    }

    anchor {
        chain("algorand:testnet") {
            provider(AnchorProviders.ALGORAND)
            options { /* chain-specific options */ }
        }
        chain("ethereum:mainnet") {
            provider(AnchorProviders.ETHEREUM)
            options { /* chain-specific options */ }
        }
    }

    factories(walletFactory = DatabaseWalletFactory(dataSource))
}
```

### Option 2: Advanced / tests only

`TrustWeaveConfig` is built internally by **`TrustWeave.build`** / **`TrustWeaveFactory`**. Application code should use the **`TrustWeave.build { }`** DSL; avoid constructing **`TrustWeaveConfig`** directly unless you are extending TrustWeave internals.

## Production Configuration

**Important:** Defaults are for **development and testing only**. For production:

1. **Replace In-Memory KMS**
   ```kotlin
   keys { provider("awsKms") { /* … */ } } // or customKms(yourKms)
   ```

2. **Use Production DID Methods**
   ```kotlin
   did {
       method("web") { domain("yourcompany.com") }
   }
   ```

3. **Use Persistent Wallet Storage**
   ```kotlin
   factories(walletFactory = DatabaseWalletFactory(dataSource))
   ```

4. **Configure Blockchain Clients**
   ```kotlin
   anchor {
       chain("algorand:mainnet") {
           provider(AnchorProviders.ALGORAND)
           options { /* … */ }
       }
   }
   ```

## Checking Current Configuration

### Inspect Registered Components

```kotlin
// Check registered DID methods
val methods = trustWeave.configuration.didRegistry.getAllMethodNames()
println("Available DID methods: $methods")

// Check registered blockchain chains
val chains = trustWeave.configuration.blockchainRegistry.getAllChainIds()
println("Available chains: $chains")

// Check wallet capabilities
val wallet = trustWeave.wallet { holder("did:key:holder") }.getOrThrow()
println("Wallet capabilities: ${wallet.capabilities}")
```

## Migration from Defaults to Production

See [Production Deployment Guide](../deployment/production-checklist.md) for step-by-step migration from defaults to production configuration.

## Related Documentation

- [Configuration reference](README.md) — Complete configuration options
- [Architecture overview](../introduction/architecture-overview.md) — Component architecture
- [Installation](../getting-started/installation.md) — Setup instructions
- [Production deployment](../deployment/production-checklist.md) — Production configuration

