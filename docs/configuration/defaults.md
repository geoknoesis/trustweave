---
title: Default Configuration
---

# Default Configuration

This document explains the default configuration used by `TrustWeave.create()` and how to customize it.

## Default Configuration Overview

When you call `TrustWeave.create()` without any configuration, TrustWeave uses the following defaults:

```kotlin
val TrustWeave = TrustWeave.create()  // Uses defaults below
```

### Default Components

| Component | Default Implementation | Purpose | Production Ready? |
|-----------|----------------------|---------|-------------------|
| **KMS** | `InMemoryKeyManagementService` | In-memory key storage | ❌ Testing only |
| **DID Method** | `DidKeyMockMethod` (did:key) | DID creation and resolution | ❌ Testing only |
| **Wallet Factory** | `TestkitWalletFactory` | Wallet creation | ❌ Testing only |
| **Blockchain Clients** | `BlockchainAnchorRegistry()` (empty) | Blockchain anchoring | ⚠️ Must be configured |
| **Credential Services** | `CredentialServiceRegistry.create()` | Credential issuance/verification | ✅ Default service |
| **Proof Generator** | `Ed25519ProofGenerator` | Cryptographic proofs | ✅ Production ready |

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
   - No blockchain clients registered
   - Must be added manually for anchoring
   - Example: `TrustWeave.create { blockchains { "algorand:testnet" to client } }`

2. **Additional DID Methods**
   - Only `did:key` is registered
   - Other methods must be added manually
   - Example: `TrustWeave.create { didMethods { + DidWebMethod() } }`

3. **Production KMS**
   - In-memory KMS is for testing only
   - Production KMS must be configured
   - Example: `TrustWeave.create { kms = AwsKeyManagementService(...) }`

## Default Behavior Details

### DID Creation Defaults

```kotlin
// Default behavior
val did = TrustWeave.dids.create()

// Equivalent to:
val did = TrustWeave.dids.create(
    method = "key",  // Default method
    options = DidCreationOptions(
        algorithm = KeyAlgorithm.ED25519,  // Default algorithm
        purposes = emptyList()  // Default purposes
    )
)
```

**Defaults:**
- Method: `"key"` (did:key)
- Algorithm: `ED25519`
- Key purposes: Empty (defaults to authentication and assertion)

### Credential Issuance Defaults

```kotlin
// Default behavior
val credential = TrustWeave.issueCredential(
    issuerDid = issuerDid,
    issuerKeyId = issuerKeyId,
    credentialSubject = subject,
    types = listOf("VerifiableCredential")  // Default type
).getOrThrow()
```

**Defaults:**
- Types: `["VerifiableCredential"]` (must include at least this)
- Expiration: `null` (no expiration)
- Proof type: `Ed25519Signature2020`

### Wallet Creation Defaults

```kotlin
// Default behavior
val wallet = TrustWeave.createWallet(holderDid = "did:key:holder").getOrThrow()

// Equivalent to:
val wallet = TrustWeave.createWallet(
    holderDid = "did:key:holder",
    walletId = UUID.randomUUID().toString(),  // Auto-generated
    provider = WalletProvider.InMemory,  // Default provider
    options = WalletCreationOptions()  // Default options
).getOrThrow()
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
val TrustWeave = TrustWeave.create {
    // Override KMS
    kms = AwsKeyManagementService(
        region = "us-east-1",
        credentials = awsCredentials
    )

    // Add DID methods
    didMethods {
        + DidKeyMethod(kms)
        + DidWebMethod(kms) { domain = "example.com" }
    }

    // Add blockchain clients
    blockchains {
        "algorand:testnet" to algorandClient
        "ethereum:mainnet" to ethereumClient
    }

    // Override wallet factory
    walletFactory = DatabaseWalletFactory(dataSource)
}
```

### Option 2: Configuration Object

```kotlin
val config = TrustWeaveConfig(
    kms = AwsKeyManagementService(...),
    walletFactory = DatabaseWalletFactory(...),
    didRegistry = DidMethodRegistry().apply {
        register(DidKeyMethod(kms))
        register(DidWebMethod(kms))
    },
    blockchainRegistry = BlockchainAnchorRegistry().apply {
        register("algorand:testnet", algorandClient)
    }
)

val TrustWeave = TrustWeave.create(config)
```

## Production Configuration

**Important:** Defaults are for **development and testing only**. For production:

1. **Replace In-Memory KMS**
   ```kotlin
   kms = AwsKeyManagementService(...)  // or Azure, Google Cloud, etc.
   ```

2. **Use Production DID Methods**
   ```kotlin
   didMethods {
       + DidWebMethod(kms) { domain = "yourcompany.com" }
       + DidIonMethod(kms)  // For production use
   }
   ```

3. **Use Persistent Wallet Storage**
   ```kotlin
   walletFactory = DatabaseWalletFactory(dataSource)
   ```

4. **Configure Blockchain Clients**
   ```kotlin
   blockchain {
       "algorand:mainnet" to algorandClient
   }
   ```

## Checking Current Configuration

### Inspect Registered Components

```kotlin
// Check registered DID methods
val methods = TrustWeave.getAvailableDidMethods()
println("Available DID methods: $methods")

// Check registered blockchain chains
val chains = TrustWeave.getAvailableChains()
println("Available chains: $chains")

// Check wallet capabilities
val wallet = TrustWeave.createWallet("did:key:holder").getOrThrow()
println("Wallet capabilities: ${wallet.capabilities}")
```

## Migration from Defaults to Production

See [Production Deployment Guide](../deployment/production-checklist.md) for step-by-step migration from defaults to production configuration.

## Related Documentation

- [Configuration Reference](README.md) - Complete configuration options
- [Architecture Overview](../introduction/architecture-overview.md) - Component architecture
- [Installation](../getting-started/installation.md) - Setup instructions
- [Production Deployment](../deployment/production-checklist.md) - Production configuration

