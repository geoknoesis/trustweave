---
title: Default Configuration
redirect_from:
  - /configuration/defaults/

---

# Default Configuration

This document explains the **default configuration** you get from **`TrustWeave.quickStart()`** / **`TrustWeave.build { }`** and how to customize it.

## Default Configuration Overview

**`TrustWeave.quickStart()`** wires an in-memory KMS, **`did:key`**, and credential services suitable for demos. **`TrustWeave.build { }`** uses the same style of defaults for any block you omit (see factory behavior in source / [Installation](../../tutorials/getting-started/installation.md)).

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

The string constants below (`AnchorProviders.ALGORAND`, `DidMethods.WEB`, `KmsProviders.AWS`, etc.) live in **`org.trustweave.trust.dsl.credential`** — import them, or pass the underlying string literal.

1. **Blockchain Clients**
   - No blockchain clients registered until you add them
   - Register clients inside **`anchor { chain("algorand:testnet") { provider(AnchorProviders.ALGORAND); options { /* … */ } } }`** (note: `provider(name)` takes no block — supply chain options via the sibling `options { }` block)

2. **Additional DID Methods**
   - Only `did:key` unless you register more
   - Add methods in **`did { method(DidMethods.WEB) { domain("example.com") } }`** (or SPI-discovered plugins)

3. **Production KMS**
   - In-memory KMS is for testing only
   - Pass a configured instance via **`customKms(awsKms)`**, or declare a registered provider name with **`keys { provider(KmsProviders.AWS); algorithm(KeyAlgorithms.ED25519) }`** (no provider-options block — configure the KMS instance separately)

## Default Behavior Details

### DID Creation Defaults

```kotlin
// Default behavior (configured `did:key` + Ed25519 from TrustWeave.build)
val did = trustWeave.createDid().getOrThrowDid()

// Equivalent options are expressed in the `createDid { }` builder, e.g.
//   method(DidMethods.KEY); algorithm(KeyAlgorithms.ED25519)
// (constants from org.trustweave.trust.dsl.credential)
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
    customKms(awsKms) // or keys { provider("awsKms"); algorithm("Ed25519") }
                      // (provider(name) takes no block — configure the KMS instance
                      //  separately via KeyManagementServices.create("aws", awsKmsOptions { … }))

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
   // Build the KMS instance separately (provider-specific options go here)
   val awsKms = KeyManagementServices.create("aws", awsKmsOptions { region = "us-east-1" })

   TrustWeave.build {
       customKms(awsKms)
       // or, for already-registered providers: keys { provider("awsKms"); algorithm("Ed25519") }
   }
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

See [Production Integration Checklist](../../tutorials/getting-started/production-integration-checklist.md) for step-by-step migration from defaults to production configuration.

## Related Documentation

- [Configuration reference](README.md) — Complete configuration options
- [Architecture overview](../../core-concepts/introduction/architecture-overview.md) — Component architecture
- [Installation](../../tutorials/getting-started/installation.md) — Setup instructions
- [Production integration checklist](../../tutorials/getting-started/production-integration-checklist.md) — Production configuration

