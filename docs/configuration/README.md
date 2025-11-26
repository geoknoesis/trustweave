---
title: Configuration Reference
nav_exclude: true
---

# Configuration Reference

Complete reference for configuring TrustWeave.

## Configuration Methods

TrustWeave supports three configuration methods:

1. **Defaults**: `TrustWeave.create()` - Uses testkit defaults
2. **Builder DSL**: `TrustWeave.create { }` - Recommended for most cases
3. **Config Object**: `TrustWeave.create(config)` - For programmatic configuration

## Configuration Components

### Key Management Service (KMS)

**Purpose:** Manages cryptographic keys for signing and verification.

**Default:** `InMemoryKeyManagementService` (testing only)

**Production Options:**
- AWS KMS: `AwsKeyManagementService`
- Azure Key Vault: `AzureKeyManagementService`
- Google Cloud KMS: `GoogleKeyManagementService`
- HashiCorp Vault: `HashiCorpKeyManagementService`

**Configuration:**
```kotlin
val TrustWeave = TrustWeave.create {
    kms = AwsKeyManagementService(
        region = "us-east-1",
        credentials = awsCredentials
    )
}
```

**See Also:**
- [KMS Integration Guides](../integrations/README.md#key-management-service-kms-integrations)
- [Key Management](../core-concepts/key-management.md)

### DID Methods

**Purpose:** DID creation and resolution methods.

**Default:** `did:key` (DidKeyMockMethod) - testing only

**Available Methods:**
- `did:key` - Native implementation
- `did:web` - Web-based resolution
- `did:ion` - Microsoft ION
- `did:ethr` - Ethereum-based
- And more... (see [DID Methods](../plugins.md#did-method-plugins))

**Configuration:**
```kotlin
val TrustWeave = TrustWeave.create {
    didMethods {
        + DidKeyMethod(kms)
        + DidWebMethod(kms) { domain = "example.com" }
        + DidIonMethod(kms)
    }
}
```

**See Also:**
- [DID Integration Guides](../integrations/README.md#did-method-integrations)
- [DIDs](../core-concepts/dids.md)

### Blockchain Clients

**Purpose:** Blockchain anchoring for tamper-proof timestamps.

**Default:** None registered (must be added)

**Available Clients:**
- Algorand: `AlgorandBlockchainAnchorClient`
- Ethereum: `EthereumBlockchainAnchorClient`
- Polygon: `PolygonBlockchainAnchorClient`
- Base: `BaseBlockchainAnchorClient`
- Arbitrum: `ArbitrumBlockchainAnchorClient`
- And more... (see [Blockchain Plugins](../plugins.md#blockchain-anchor-plugins))

**Configuration:**
```kotlin
val TrustWeave = TrustWeave.create {
    blockchains {
        "algorand:testnet" to AlgorandBlockchainAnchorClient(
            chainId = "algorand:testnet",
            options = AlgorandOptions(...)
        )
        "ethereum:mainnet" to EthereumBlockchainAnchorClient(...)
    }
}
```

**See Also:**
- [Blockchain Integration Guides](../integrations/README.md#blockchain-anchor-integrations)
- [Blockchain Anchoring](../core-concepts/blockchain-anchoring.md)

### Wallet Factory

**Purpose:** Creates wallet instances for credential storage.

**Default:** `TestkitWalletFactory` (in-memory, testing only)

**Available Factories:**
- In-Memory: `TestkitWalletFactory` (testing)
- Database: `DatabaseWalletFactory` (production)
- File: `FileWalletFactory` (production)
- Cloud: `CloudWalletFactory` (production)

**Configuration:**
```kotlin
val TrustWeave = TrustWeave.create {
    walletFactory = DatabaseWalletFactory(
        dataSource = dataSource,
        enableOrganization = true,
        enablePresentation = true
    )
}
```

**See Also:**
- [Wallet API](../api-reference/wallet-api.md)
- [Wallets](../core-concepts/wallets.md)

### Credential Services

**Purpose:** Credential issuance and verification services.

**Default:** `CredentialServiceRegistry.create()` (default service)

**Configuration:**
```kotlin
val TrustWeave = TrustWeave.create {
    credentialServices {
        + MyCustomCredentialService()
        + HttpCredentialService(endpoint = "https://issuer.example.com")
    }
}
```

**See Also:**
- [Credential Service API](../api-reference/credential-service-api.md)

### Proof Generators

**Purpose:** Generate cryptographic proofs for credentials.

**Default:** `Ed25519ProofGenerator`

**Available Generators:**
- Ed25519: `Ed25519ProofGenerator` (default)
- JWT: `JwtProofGenerator`
- BBS+: `BbsProofGenerator`
- LD-Proof: `LdProofGenerator`

**Configuration:**
```kotlin
val TrustWeave = TrustWeave.create {
    // Proof generators configured via CredentialServiceRegistry
    // Default Ed25519ProofGenerator is registered automatically
}
```

**See Also:**
- [Verifiable Credentials](../core-concepts/verifiable-credentials.md)

## Configuration Validation

TrustWeave validates configuration during creation:

### Validation Rules

1. **KMS Required**: KMS must be provided (defaults to in-memory)
2. **DID Method Required**: At least one DID method must be registered
3. **Wallet Factory Required**: Wallet factory must be provided (defaults to testkit)
4. **Chain ID Format**: Blockchain chain IDs must match CAIP-2 format
5. **DID Method Format**: DID method names must be valid identifiers

### Validation Errors

Configuration validation errors are returned as `TrustWeaveError.ValidationFailed`:

```kotlin
val result = runCatching {
    TrustWeave.create {
        // Invalid configuration
    }
}

result.fold(
    onSuccess = { TrustWeave -> /* success */ },
    onFailure = { error ->
        when (error) {
            is TrustWeaveError.ValidationFailed -> {
                println("Configuration invalid: ${error.reason}")
                println("Field: ${error.field}")
                println("Value: ${error.value}")
            }
            else -> println("Error: ${error.message}")
        }
    }
)
```

## Environment-Specific Configuration

### Development Configuration

```kotlin
val devVericore = TrustWeave.create {
    // Use testkit defaults
    // No additional configuration needed
}
```

### Testing Configuration

```kotlin
val testVericore = TrustWeave.create {
    kms = InMemoryKeyManagementService()
    didMethods {
        + DidKeyMockMethod(kms)
    }
    blockchains {
        "inmemory:test" to InMemoryBlockchainAnchorClient("inmemory:test")
    }
}
```

### Production Configuration

```kotlin
val prodVericore = TrustWeave.create {
    kms = AwsKeyManagementService(
        region = System.getenv("AWS_REGION"),
        credentials = awsCredentials
    )
    
    didMethods {
        + DidWebMethod(kms) { domain = "yourcompany.com" }
        + DidIonMethod(kms)
    }
    
    blockchains {
        "algorand:mainnet" to AlgorandBlockchainAnchorClient(
            chainId = "algorand:mainnet",
            options = AlgorandOptions(
                algodUrl = System.getenv("ALGOD_URL"),
                privateKey = System.getenv("ALGORAND_PRIVATE_KEY")
            )
        )
    }
    
    walletFactory = DatabaseWalletFactory(
        dataSource = dataSource,
        enableOrganization = true,
        enablePresentation = true
    )
}
```

## Configuration Best Practices

1. **Use Environment Variables**: Store sensitive configuration in environment variables
2. **Validate Early**: Validate configuration at startup, not at runtime
3. **Use Type-Safe Options**: Prefer typed options over maps
4. **Document Configuration**: Document your configuration choices
5. **Test Configuration**: Test configuration in staging before production

## Related Documentation

- [Default Configuration](defaults.md) - What defaults are used
- [Architecture Overview](../introduction/architecture-overview.md) - Component architecture
- [Production Deployment](../deployment/production-checklist.md) - Production setup
- [Installation](../getting-started/installation.md) - Initial setup

