---
title: HashiCorp Vault KMS Integration
---

# HashiCorp Vault KMS Integration

> This guide covers the HashiCorp Vault Key Management Service (KMS) integration for TrustWeave. The Vault KMS plugin provides production-ready key management using Vault's Transit secrets engine with support for all Vault Transit-compatible algorithms.

## Overview

The `kms/plugins/hashicorp` module provides a complete implementation of TrustWeave's `KeyManagementService` interface using HashiCorp Vault's Transit secrets engine. This integration enables you to:

- Use HashiCorp Vault for secure key generation and storage
- Leverage Vault's centralized key management and policy-based access control
- Support all Vault Transit-compatible algorithms (Ed25519, secp256k1, P-256/P-384/P-521, RSA)
- Integrate with existing Vault infrastructure (on-premises or cloud)
- Use token-based or AppRole authentication

## Installation

Add the HashiCorp Vault KMS module to your dependencies:

```kotlin
dependencies {
    implementation("com.trustweave.kms:hashicorp:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-kms:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-common:1.0.0-SNAPSHOT")
}
```

## Prerequisites

Before using the Vault KMS plugin, ensure:

1. **Vault Server**: A running HashiCorp Vault instance (version 1.0+)
2. **Transit Engine**: The Transit secrets engine must be enabled
3. **Authentication**: Valid Vault token or AppRole credentials
4. **Policies**: Appropriate Vault policies for key operations

### Enabling the Transit Engine

Enable the Transit engine in Vault:

```bash
vault secrets enable transit
```

Or with a custom path:

```bash
vault secrets enable -path=custom-transit transit
```

## Configuration

### Basic Configuration

The Vault KMS provider can be configured via options map or environment variables:

```kotlin
import com.trustweave.kms.*
import java.util.ServiceLoader

// Discover Vault provider
val providers = ServiceLoader.load(KeyManagementServiceProvider::class.java)
val vaultProvider = providers.find { it.name == "vault" }

// Create KMS with explicit configuration
val kms = vaultProvider?.create(mapOf(
    "address" to "http://localhost:8200",
    "token" to "hvs.xxx"
))
```

### Authentication

The plugin supports multiple authentication methods:

#### 1. Token Authentication (Default)

Token-based authentication is the simplest method:

```kotlin
val kms = vaultProvider?.create(mapOf(
    "address" to "http://localhost:8200",
    "token" to "hvs.xxx"
))
```

#### 2. AppRole Authentication

For production environments, use AppRole authentication:

```kotlin
val kms = vaultProvider?.create(mapOf(
    "address" to "http://localhost:8200",
    "appRolePath" to "approle",
    "roleId" to "xxx",
    "secretId" to "yyy"
))
```

#### 3. Environment Variables

The plugin automatically reads from environment variables:

```bash
export VAULT_ADDR=http://localhost:8200
export VAULT_TOKEN=hvs.xxx
export VAULT_NAMESPACE=admin
export VAULT_TRANSIT_PATH=transit
```

```kotlin
// Configuration loaded from environment automatically
val config = VaultKmsConfig.fromEnvironment()
val kms = VaultKeyManagementService(config ?: throw IllegalStateException("Vault config not found"))
```

### Namespace Support (Vault Enterprise)

For Vault Enterprise with namespaces:

```kotlin
val kms = vaultProvider?.create(mapOf(
    "address" to "http://localhost:8200",
    "token" to "hvs.xxx",
    "namespace" to "admin"
))
```

### Custom Transit Path

If the Transit engine is mounted at a custom path:

```kotlin
val kms = vaultProvider?.create(mapOf(
    "address" to "http://localhost:8200",
    "token" to "hvs.xxx",
    "transitPath" to "custom-transit"
))
```

## Algorithm Support

The Vault KMS plugin supports all Vault Transit-compatible algorithms:

| Algorithm | Vault Transit Key Type | Hash Algorithm | Notes |
|-----------|----------------------|----------------|-------|
| Ed25519 | `ed25519` | `sha2-256` | Native Ed25519 support |
| secp256k1 | `ecdsa-p256k1` | `sha2-256` | Blockchain-compatible |
| P-256 | `ecdsa-p256` | `sha2-256` | FIPS-compliant |
| P-384 | `ecdsa-p384` | `sha2-384` | FIPS-compliant |
| P-521 | `ecdsa-p521` | `sha2-512` | FIPS-compliant |
| RSA-2048 | `rsa-2048` | `sha2-256` | Legacy support |
| RSA-3072 | `rsa-3072` | `sha2-256` | Higher security |
| RSA-4096 | `rsa-4096` | `sha2-256` | Maximum security |

**Note**: Vault Transit does NOT support BLS12-381.

### Checking Algorithm Support

```kotlin
val kms = vaultProvider?.create(mapOf(
    "address" to "http://localhost:8200",
    "token" to "hvs.xxx"
))

// Get all supported algorithms
val supported = kms?.getSupportedAlgorithms()
println("Supported algorithms: ${supported?.joinToString { it.name }}")

// Check specific algorithm
if (kms?.supportsAlgorithm(Algorithm.Ed25519) == true) {
    println("Ed25519 is supported")
}
```

## Usage Examples

### Generating Keys

```kotlin
import com.trustweave.kms.*

// Generate Ed25519 key with auto-generated name
val key = kms.generateKey(Algorithm.Ed25519)

// Generate key with custom name
val keyWithName = kms.generateKey(
    algorithm = Algorithm.Ed25519,
    options = mapOf(
        "keyName" to "did-issuer-key",
        "exportable" to false,
        "allowPlaintextBackup" to false
    )
)

// Generate P-256 key for FIPS compliance
val fipsKey = kms.generateKey(
    algorithm = Algorithm.P256,
    options = mapOf(
        "keyName" to "fips-compliant-key"
    )
)
```

### Signing Data

```kotlin
// Sign with key name
val signature = kms.sign("did-issuer-key", data.toByteArray())

// Sign with full key path
val signature = kms.sign("transit/keys/did-issuer-key", data.toByteArray())

// Sign with algorithm override
val signature = kms.sign(
    keyId = "did-issuer-key",
    data = data.toByteArray(),
    algorithm = Algorithm.Ed25519
)
```

### Retrieving Public Keys

```kotlin
// Get public key by key name
val publicKey = kms.getPublicKey("did-issuer-key")

// Get public key by full path
val publicKey = kms.getPublicKey("transit/keys/did-issuer-key")

// Access JWK format
val jwk = publicKey.publicKeyJwk
println("Public key JWK: $jwk")
```

### Key Deletion

```kotlin
// Delete key (requires deletion policy in Vault)
val deleted = kms.deleteKey("did-issuer-key")
```

**Note**: Key deletion in Vault Transit requires special policy configuration. By default, keys cannot be deleted for security reasons.

## Vault Setup

### Creating Vault Policies

Create a policy that allows key operations:

```hcl
# Policy for TrustWeave KMS operations
path "transit/keys/*" {
  capabilities = ["create", "read", "update", "delete"]
}

path "transit/keys/+/config" {
  capabilities = ["update"]
}

path "transit/sign/*" {
  capabilities = ["create", "update"]
}

path "transit/keys/+/+" {
  capabilities = ["read"]
}
```

Save this policy and apply it to a token or AppRole:

```bash
vault policy write TrustWeave-kms TrustWeave-kms.hcl
vault token create -policy=TrustWeave-kms
```

### Key Naming Conventions

Vault Transit uses key names (not IDs) to identify keys. Recommendations:

- Use descriptive names: `did-issuer-key`, `vc-signing-key`
- Include algorithm in name: `ed25519-main-key`
- Use hierarchical naming: `production/issuer/ed25519-key`
- Avoid special characters (use hyphens, underscores)
- Keep names URL-safe

### Key Options

When generating keys, you can specify:

- `keyName`: Custom key name (default: auto-generated)
- `exportable`: Allow key export (default: false)
- `allowPlaintextBackup`: Allow plaintext backup (default: false)

## Key Rotation

Vault Transit supports key versioning and rotation:

### Manual Rotation

```bash
# Rotate a key in Vault
vault write -f transit/keys/did-issuer-key/rotate
```

### Automatic Rotation

Configure automatic rotation in Vault:

```bash
vault write transit/keys/did-issuer-key/config \
    min_available_version=1 \
    min_decryption_version=1 \
    min_encryption_version=1 \
    deletion_allowed=false
```

The plugin automatically uses the latest key version for signing operations.

## Error Handling

The plugin maps Vault exceptions to TrustWeave exceptions:

```kotlin
try {
    val key = kms.generateKey(Algorithm.Ed25519)
} catch (e: UnsupportedAlgorithmException) {
    println("Algorithm not supported: ${e.message}")
} catch (e: KeyNotFoundException) {
    println("Key not found: ${e.message}")
} catch (e: TrustWeaveException) {
    when {
        e.message?.contains("Access denied") == true -> {
            println("Check Vault policies")
        }
        e.message?.contains("Authentication failed") == true -> {
            println("Check Vault token or credentials")
        }
        else -> {
            println("Error: ${e.message}")
        }
    }
}
```

### Common Errors

| Error | Cause | Solution |
|-------|-------|----------|
| `404` | Key doesn't exist | Verify key name is correct |
| `403` | Insufficient permissions | Grant appropriate Vault policies |
| `401` | Authentication failed | Check token or AppRole credentials |
| `400` | Invalid request | Verify algorithm and parameters |

## Best Practices

1. **Use AppRole Authentication**: Prefer AppRole over tokens for production
2. **Key Naming**: Use descriptive, hierarchical key names
3. **Policies**: Implement least-privilege Vault policies
4. **Key Rotation**: Enable automatic key rotation for long-lived keys
5. **Namespaces**: Use Vault Enterprise namespaces for multi-tenancy
6. **Monitoring**: Monitor Vault audit logs for key operations
7. **Backup**: Configure key backup if required by compliance

## Testing

### Unit Tests

The module includes unit tests that can be run without a Vault instance:

```bash
./gradlew :kms/plugins/hashicorp:test
```

### Integration Tests with Local Vault

For integration testing, use a local Vault instance:

```bash
# Start Vault in dev mode
vault server -dev

# Set environment variables
export VAULT_ADDR=http://localhost:8200
export VAULT_TOKEN=<dev-root-token>

# Enable Transit engine
vault secrets enable transit
```

Then run tests with the local Vault instance.

## Related Documentation

- [Key Management Guide](../core-concepts/key-management.md) - Core KMS concepts
- [Algorithm Compatibility Table](../core-concepts/algorithm-compatibility-table.md) - Algorithm support comparison
- [Key Rotation Guide](../advanced/key-rotation.md) - Key rotation strategies
- [Creating Plugins Guide](../contributing/creating-plugins.md) - Custom KMS implementations

## See Also

- [HashiCorp Vault Transit Engine Documentation](https://developer.hashicorp.com/vault/docs/secrets/transit)
- [Vault API Reference](https://developer.hashicorp.com/vault/api-docs)
- [Vault Policies Guide](https://developer.hashicorp.com/vault/docs/concepts/policies)

