---
title: Azure Key Vault Integration
parent: Integration Modules
---

# Azure Key Vault Integration

> This guide covers the Azure Key Vault integration for TrustWeave. The Azure Key Vault plugin provides production-ready key management with support for Azure Key Vault-compatible algorithms.

## Overview

The `kms/plugins/azure` module provides a complete implementation of TrustWeave's `KeyManagementService` interface using Azure Key Vault. This integration enables you to:

- Use Azure Key Vault for secure key generation and storage
- Leverage Azure Key Vault's key versioning and soft-delete capabilities
- Support Azure Key Vault-compatible algorithms (secp256k1, P-256/P-384/P-521, RSA)
- Integrate with existing Azure infrastructure and Managed Identity
- Meet regulatory compliance requirements with Azure's security features

## Installation

Add the Azure Key Vault module to your dependencies:

```kotlin
dependencies {
    // Only need to add the Azure KMS plugin - core dependencies are included transitively
    implementation("org.trustweave.kms:azure:1.0.0-SNAPSHOT")
}
```

**Note:** The Azure KMS plugin automatically includes `trustweave-kms` and `trustweave-common` as transitive dependencies, so you don't need to declare them explicitly.

## Configuration

### Basic Configuration

The Azure Key Vault provider can be configured via options map or environment variables:

```kotlin
import org.trustweave.kms.*

// Simple factory API - no ServiceLoader needed!
val kms = KeyManagementServices.create("azure", mapOf(
    "vaultUrl" to "https://myvault.vault.azure.net"
))
```

### Authentication

The plugin supports multiple authentication methods:

#### 1. Managed Identity (Recommended for Production)

When running on Azure infrastructure (Azure VMs, App Service, Azure Functions, AKS), use Managed Identity:

```kotlin
// No credentials needed - uses Managed Identity automatically
val kms = azureProvider?.create(mapOf(
    "vaultUrl" to "https://myvault.vault.azure.net"
))
```

Enable Managed Identity on your Azure resource and grant it access to the Key Vault:

```bash
# Enable system-assigned managed identity
az vm identity assign --name my-vm --resource-group my-rg

# Grant access to Key Vault
az keyvault set-policy --name myvault \
    --object-id <managed-identity-object-id> \
    --key-permissions get list create sign verify
```

#### 2. Service Principal

For local development or non-Azure environments:

```kotlin
val kms = azureProvider?.create(mapOf(
    "vaultUrl" to "https://myvault.vault.azure.net",
    "clientId" to "client-id",
    "clientSecret" to "client-secret",
    "tenantId" to "tenant-id"
))
```

Create a service principal:

```bash
az ad sp create-for-rbac --name TrustWeave-kms \
    --role "Key Vault Crypto Officer" \
    --scopes /subscriptions/{subscription-id}/resourceGroups/{resource-group}/providers/Microsoft.KeyVault/vaults/{vault-name}
```

#### 3. Environment Variables

The plugin automatically reads from environment variables:

```bash
export AZURE_VAULT_URL=https://myvault.vault.azure.net
export AZURE_CLIENT_ID=client-id
export AZURE_CLIENT_SECRET=client-secret
export AZURE_TENANT_ID=tenant-id
```

```kotlin
// Configuration loaded from environment automatically
val config = AzureKmsConfig.fromEnvironment()
val kms = AzureKeyManagementService(config ?: throw IllegalStateException("Azure config not found"))
```

### Direct Configuration

You can also configure directly using the builder:

```kotlin
import org.trustweave.azurekms.*

val config = AzureKmsConfig.builder()
    .vaultUrl("https://myvault.vault.azure.net")
    .clientId("client-id")
    .clientSecret("client-secret")
    .tenantId("tenant-id")
    .build()

val kms = AzureKeyManagementService(config)
```

## Algorithm Support

The Azure Key Vault plugin supports the following algorithms:

| Algorithm | Azure Key Vault Key Type | Signing Algorithm | Notes |
|-----------|--------------------------|-------------------|-------|
| secp256k1 | `EC` with `P-256K` curve | `ES256K` | Blockchain-compatible |
| P-256 | `EC` with `P-256` curve | `ES256` | FIPS 140-2 compliant |
| P-384 | `EC` with `P-384` curve | `ES384` | FIPS 140-2 compliant |
| P-521 | `EC` with `P-521` curve | `ES512` | FIPS 140-2 compliant |
| RSA-2048 | `RSA` | `RS256` | Legacy support |
| RSA-3072 | `RSA` | `RS256` | Higher security |
| RSA-4096 | `RSA` | `RS256` | Maximum security |

**Note:** Ed25519 is not directly supported by Azure Key Vault. Use P-256, P-384, or P-521 as alternatives.

### Checking Algorithm Support

```kotlin
val kms = azureProvider?.create(mapOf(
    "vaultUrl" to "https://myvault.vault.azure.net"
))

// Get all supported algorithms
val supported = kms?.getSupportedAlgorithms()
println("Supported algorithms: ${supported?.joinToString { it.name }}")

// Check specific algorithm
if (kms?.supportsAlgorithm(Algorithm.P256) == true) {
    println("P-256 is supported")
}
```

## Usage Examples

### Generating Keys

```kotlin
import org.trustweave.kms.*
import org.trustweave.kms.KmsOptionKeys
import org.trustweave.kms.results.*

// Generate P-256 key
val result = kms.generateKey(Algorithm.P256)
when (result) {
    is GenerateKeyResult.Success -> {
        val keyHandle = result.keyHandle
        println("Key created: ${keyHandle.id}")
    }
    is GenerateKeyResult.Failure -> {
        println("Error: ${result.reason}")
    }
}

// Generate key with custom name and tags
val keyWithTagsResult = kms.generateKey(
    algorithm = Algorithm.P256,
    options = mapOf(
        KmsOptionKeys.KEY_ID to "issuer-key-2025",
        KmsOptionKeys.DESCRIPTION to "Issuer key for 2025",
        KmsOptionKeys.TAGS to mapOf(
            "environment" to "production",
            "purpose" to "issuance"
        )
    )
)

// Generate P-256 key for FIPS compliance
val fipsKeyResult = kms.generateKey(
    algorithm = Algorithm.P256,
    options = mapOf(
        KmsOptionKeys.KEY_ID to "fips-compliant-key"
    )
)
```

### Signing Data

```kotlin
// Sign with key name (KeyId value class)
val sign = kms.sign(KeyId(keyId), data.toByteArray())
when (sign) {
    is SignResult.Success -> {
        val signature = sign.signature
        // Use signature
    }
    is SignResult.Failure -> {
        println("Error: ${sign.reason}")
    }
}

// Sign with full key URL
val sign = kms.sign(
    KeyId("https://myvault.vault.azure.net/keys/mykey"),
    data.toByteArray()
)

// Sign with key name and version
val sign = kms.sign(
    KeyId("mykey/abc123def456"),
    data.toByteArray()
)

// Sign with algorithm override
val sign = kms.sign(
    keyId = KeyId(keyId),
    data = data.toByteArray(),
    algorithm = Algorithm.P256
)
```

### Retrieving Public Keys

```kotlin
// Get public key by key name (KeyId value class)
val publicKeyResult = kms.getPublicKey(KeyId("mykey"))
when (publicKeyResult) {
    is GetPublicKeyResult.Success -> {
        val publicKey = publicKeyResult.keyHandle
        // Access JWK format
        val jwk = publicKey.publicKeyJwk
        println("Public key JWK: $jwk")
    }
    is GetPublicKeyResult.Failure -> {
        println("Error: ${publicKeyResult.reason}")
    }
}

// Get public key by full URL
val publicKeyResult = kms.getPublicKey(
    KeyId("https://myvault.vault.azure.net/keys/mykey")
)

// Get public key by name and version
val publicKeyResult = kms.getPublicKey(KeyId("mykey/abc123def456"))
```

### Key Deletion

```kotlin
// Schedule key deletion (soft delete)
val deleteResult = kms.deleteKey(KeyId(keyId))
when (deleteResult) {
    is DeleteKeyResult.Deleted -> println("Key deleted")
    is DeleteKeyResult.NotFound -> println("Key not found (already deleted)")
    is DeleteKeyResult.Failure.Error -> println("Error: ${deleteResult.reason}")
}

// Note: Azure Key Vault uses soft delete by default
// Keys are recoverable for a retention period (default 90 days)
// To permanently delete, use Azure CLI or portal
```

## Key Identifiers

Azure Key Vault supports multiple key identifier formats:

- **Key name**: `mykey` (uses latest version)
- **Key name with version**: `mykey/abc123def456`
- **Full URL**: `https://myvault.vault.azure.net/keys/mykey`
- **Full URL with version**: `https://myvault.vault.azure.net/keys/mykey/abc123def456`

The plugin automatically resolves all these formats.

## Key Versioning

Azure Key Vault uses key versions. When you create a key, a version is automatically created. The plugin:

- Uses the latest version by default when only key name is provided
- Supports explicit version specification
- Returns the full key URL (including version) as the key ID

## Error Handling

The plugin maps Azure Key Vault exceptions to TrustWeave exceptions:

```kotlin
val result = kms.generateKey(Algorithm.P256)
when (result) {
    is GenerateKeyResult.Success -> {
        val keyHandle = result.keyHandle
        // Use key handle
    }
    is GenerateKeyResult.Failure.UnsupportedAlgorithm -> {
        println("Algorithm not supported: ${result.algorithm.name}")
    }
    is GenerateKeyResult.Failure.InvalidOptions -> {
        println("Invalid options: ${result.reason}")
    }
    is GenerateKeyResult.Failure.Error -> {
        when {
            result.reason.contains("Access denied", ignoreCase = true) -> {
                println("Check Azure RBAC permissions")
            }
            result.reason.contains("Key not found", ignoreCase = true) -> {
                println("Key does not exist")
            }
            else -> {
                println("Error: ${result.reason}")
            }
        }
    }
}
```

### Common Errors

| Error | Cause | Solution |
|-------|-------|----------|
| `HttpResponseException` (403) | Insufficient RBAC permissions | Grant `Key Vault Crypto Officer` or `Key Vault Crypto User` role |
| `ResourceNotFoundException` (404) | Key doesn't exist | Verify key name/URL is correct |
| `UnsupportedAlgorithmException` | Algorithm not supported | Check algorithm compatibility table |
| `IllegalArgumentException` | Missing required config | Ensure vaultUrl is provided |

## Azure RBAC Permissions

Your Azure identity (Managed Identity or Service Principal) needs the following permissions:

### Minimum Required Permissions

```bash
# Grant Key Vault Crypto User role (read and sign)
az role assignment create \
    --role "Key Vault Crypto User" \
    --assignee <managed-identity-object-id> \
    --scope /subscriptions/{subscription-id}/resourceGroups/{resource-group}/providers/Microsoft.KeyVault/vaults/{vault-name}
```

### Recommended Roles

For full key management capabilities:

- **Key Vault Crypto Officer** - Full key management access (create, delete, manage)
- **Key Vault Crypto User** - Sign and verify operations
- **Key Vault Secrets User** - If using secrets (not applicable for keys)

### Access Policy (Classic)

If using access policies instead of RBAC:

```bash
az keyvault set-policy --name myvault \
    --object-id <managed-identity-object-id> \
    --key-permissions get list create sign verify delete recover backup restore
```

## Algorithm Compatibility

See the [Algorithm Compatibility Table](../core-concepts/algorithm-compatibility-table.md) for detailed comparison of algorithm support across DIDs, VCs, AWS KMS, Azure Key Vault, and Google Cloud KMS.

**Key Points:**
- ✅ secp256k1 supported for blockchain integration
- ✅ All NIST curves (P-256/P-384/P-521) supported
- ✅ RSA keys supported for legacy compatibility
- ❌ Ed25519 not supported (use P-256, P-384, or P-521 as alternatives)
- ❌ BLS12-381 not supported (requires specialized KMS)

## Best Practices

1. **Use Managed Identity**: Prefer Managed Identity over Service Principal for production
2. **Key Naming**: Use descriptive key names with versioning strategy
3. **Key Tags**: Use tags to organize keys by environment, purpose, etc.
4. **Soft Delete**: Enable soft delete on Key Vault for key recovery
5. **Error Handling**: Implement proper error handling for Azure exceptions
6. **Key Lifecycle**: Plan key rotation and deletion schedules
7. **Access Control**: Use Azure RBAC to restrict key access
8. **Key Versioning**: Understand that Azure Key Vault uses key versions internally
9. **Network Security**: Use private endpoints for Key Vault access in production
10. **Monitoring**: Enable Azure Monitor and Key Vault logging

## Testing

### Unit Tests

The module includes unit tests that can be run without Azure credentials:

```bash
./gradlew :kms/plugins/azure:test
```

### Integration Tests

For integration testing, use a test Azure Key Vault:

```kotlin
// Configure for test Key Vault
val kms = azureProvider?.create(mapOf(
    "vaultUrl" to "https://testvault.vault.azure.net",
    "clientId" to "test-client-id",
    "clientSecret" to "test-client-secret",
    "tenantId" to "test-tenant-id"
))
```

### Local Testing with Azure Key Vault Emulator

For local development, you can use the Azure Key Vault emulator (if available):

```kotlin
val kms = azureProvider?.create(mapOf(
    "vaultUrl" to "https://localhost:8443",
    "endpointOverride" to "https://localhost:8443"
))
```

## Using with TrustWeave

### Basic Setup

```kotlin
import org.trustweave.*
import org.trustweave.azurekms.*

val TrustWeave = TrustWeave.create {
    kms = AzureKeyManagementService(
        AzureKmsConfig.builder()
            .vaultUrl("https://myvault.vault.azure.net")
            .build()
    )
}
```

### With SPI Auto-Discovery

```kotlin
val TrustWeave = TrustWeave.create {
    // Azure Key Vault will be discovered automatically if on classpath
    // Configure via environment variables or system properties
}
```

### With Managed Identity

```kotlin
// When running on Azure infrastructure, Managed Identity is used automatically
val TrustWeave = TrustWeave.create {
    kms = AzureKeyManagementService(
        AzureKmsConfig.builder()
            .vaultUrl("https://myvault.vault.azure.net")
            // No credentials needed - uses Managed Identity
            .build()
    )
}
```

## Key Rotation Strategies

Azure Key Vault supports key versioning, which can be used for rotation:

### 1. Manual Rotation with New Versions

Create a new key version when rotating:

```kotlin
// Step 1: Create new key version (Azure Key Vault doesn't support this directly via API)
// Instead, create a new key with a new name
val newKeyResult = kms.generateKey(
    algorithm = Algorithm.P256,
    options = mapOf(
        KmsOptionKeys.KEY_ID to "issuer-key-v2",
        KmsOptionKeys.TAGS to mapOf(
            "version" to "2",
            "rotated" to "2025-01-15"
        )
    )
)
when (newKeyResult) {
    is GenerateKeyResult.Success -> {
        val newKeyHandle = newKeyResult.keyHandle
        // Continue with rotation steps...
    }
    is GenerateKeyResult.Failure -> {
        // Handle error
    }
}

// Step 2: Update DID document (see key-rotation.md)
// Step 3: Switch issuance to new key
// Step 4: Maintain old key for historical verification
// Step 5: Eventually delete old key after credentials expire
```

### 2. Using Key Names for Rotation

Use different key names for each rotation:

```kotlin
// Create initial key
val key1Result = kms.generateKey(
    algorithm = Algorithm.P256,
    options = mapOf(KmsOptionKeys.KEY_ID to "issuer-key-v1")
)

// Later, create rotated key
val key2Result = kms.generateKey(
    algorithm = Algorithm.P256,
    options = mapOf(KmsOptionKeys.KEY_ID to "issuer-key-v2")
)

// Update your application to use key2
// Keep key1 for historical verification
```

## Security Considerations

1. **Network Security**: Use private endpoints for Key Vault access in production
2. **Access Control**: Implement least-privilege access using Azure RBAC
3. **Key Vault Firewall**: Configure firewall rules to restrict access
4. **Soft Delete**: Enable soft delete for key recovery capabilities
5. **Monitoring**: Enable Azure Monitor and Key Vault logging
6. **Key Backup**: Consider backing up keys before deletion
7. **Compliance**: Azure Key Vault meets various compliance standards (SOC 2, ISO 27001, etc.)

## Related Documentation

- [Key Management Guide](../core-concepts/key-management.md) - Core KMS concepts
- [Algorithm Compatibility Table](../core-concepts/algorithm-compatibility-table.md) - Algorithm support comparison
- [Key Rotation Guide](../advanced/key-rotation.md) - Key rotation strategies
- [Creating Plugins Guide](../contributing/creating-plugins.md) - Custom KMS implementations

## See Also

- [Azure Key Vault Documentation](https://learn.microsoft.com/azure/key-vault/)
- [Azure Key Vault Java SDK](https://github.com/Azure/azure-sdk-for-java/tree/main/sdk/keyvault)
- [Azure Key Vault REST API Reference](https://learn.microsoft.com/rest/api/keyvault/)
- [Azure Managed Identity Documentation](https://learn.microsoft.com/azure/active-directory/managed-identities-azure-resources/)

