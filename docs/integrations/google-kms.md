---
title: Google Cloud KMS Integration
parent: Integration Modules
---

# Google Cloud KMS Integration

> This guide covers the Google Cloud Key Management Service (KMS) integration for TrustWeave. The Google Cloud KMS plugin provides production-ready key management with support for all Google Cloud KMS-compatible algorithms.

## Overview

The `kms/plugins/google` module provides a complete implementation of TrustWeave's `KeyManagementService` interface using Google Cloud Key Management Service. This integration enables you to:

- Use Google Cloud KMS for secure key generation and storage
- Leverage Google Cloud KMS's key versioning and rotation capabilities
- Support all Google Cloud KMS-compatible algorithms (secp256k1, P-256/P-384, RSA)
- Integrate with existing Google Cloud infrastructure and IAM policies

## Installation

Add the Google Cloud KMS module to your dependencies:

```kotlin
dependencies {
    // Only need to add the Google Cloud KMS plugin - core dependencies are included transitively
    implementation("org.trustweave:kms-plugins-google:0.6.0")
}
```

**Note:** The Google Cloud KMS plugin automatically includes `trustweave-kms` and `trustweave-common` as transitive dependencies, so you don't need to declare them explicitly.

## Configuration

### Basic Configuration

The Google Cloud KMS provider can be configured via options map or environment variables:

```kotlin
import org.trustweave.kms.*

// Simple factory API - no ServiceLoader needed!
val kms = KeyManagementServices.create("google-cloud-kms", mapOf(
    "projectId" to "my-project",
    "location" to "us-east1",
    "keyRing" to "my-key-ring"
))
```

### Authentication

The plugin supports multiple authentication methods:

#### 1. Application Default Credentials (Recommended for Production)

When running on Google Cloud infrastructure (Compute Engine, Cloud Run, GKE), use Application Default Credentials:

```kotlin
// No credentials needed - uses ADC automatically
val kms = googleProvider?.create(mapOf(
    "projectId" to "my-project",
    "location" to "us-east1",
    "keyRing" to "my-key-ring"
))
```

Set up ADC:

```bash
gcloud auth application-default login
```

#### 2. Service Account JSON File

For local development or non-Google Cloud environments:

```kotlin
val kms = googleProvider?.create(mapOf(
    "projectId" to "my-project",
    "location" to "us-east1",
    "keyRing" to "my-key-ring",
    "credentialsPath" to "/path/to/service-account.json"
))
```

#### 3. Service Account JSON String

For programmatic configuration:

```kotlin
val kms = googleProvider?.create(mapOf(
    "projectId" to "my-project",
    "location" to "us-east1",
    "keyRing" to "my-key-ring",
    "credentialsJson" to serviceAccountJsonString
))
```

#### 4. Environment Variables

The plugin automatically reads from environment variables:

```bash
export GOOGLE_CLOUD_PROJECT=my-project
export GOOGLE_CLOUD_LOCATION=us-east1
export GOOGLE_CLOUD_KEY_RING=my-key-ring
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json
```

```kotlin
// Configuration loaded from environment automatically
val config = GoogleKmsConfig.fromEnvironment()
val kms = GoogleCloudKeyManagementService(config ?: throw IllegalStateException("Google Cloud config not found"))
```

### Direct Configuration

You can also configure directly using the builder:

```kotlin
import org.trustweave.googlekms.*

val config = GoogleKmsConfig.builder()
    .projectId("my-project")
    .location("us-east1")
    .keyRing("my-key-ring")
    .credentialsPath("/path/to/service-account.json")
    .build()

val kms = GoogleCloudKeyManagementService(config)
```

## Algorithm Support

The Google Cloud KMS plugin supports the following algorithms:

| Algorithm | Google Cloud KMS Algorithm | Notes |
|-----------|---------------------------|-------|
| Ed25519 | (varies)                  | Advertised by the plugin; availability depends on the GCP KMS region/version |
| secp256k1 | `EC_SIGN_SECP256K1_SHA256` | Blockchain-compatible |
| P-256 | `EC_SIGN_P256_SHA256` | FIPS 140-2 compliant |
| P-384 | `EC_SIGN_P384_SHA384` | FIPS 140-2 compliant |
| P-521 | (varies)                  | Advertised by the plugin; availability depends on the GCP KMS region/version |
| RSA-2048 | `RSA_SIGN_PKCS1_2048_SHA256` | Legacy support |
| RSA-3072 | `RSA_SIGN_PKCS1_3072_SHA256` | Higher security |
| RSA-4096 | `RSA_SIGN_PKCS1_4096_SHA256` | Maximum security |

**Note:** Ed25519 and P-521 are advertised by `GoogleCloudKeyManagementService.SUPPORTED_ALGORITHMS`, but actual availability depends on your Google Cloud KMS deployment. Server-side errors will surface as `GenerateKeyResult.Failure.Error`.

### Checking Algorithm Support

```kotlin
val kms = googleProvider?.create(mapOf(
    "projectId" to "my-project",
    "location" to "us-east1",
    "keyRing" to "my-key-ring"
))

// Get all supported algorithms
val supported = kms?.getSupportedAlgorithms()
println("Supported algorithms: ${supported?.joinToString { it.name }}")

// Check specific algorithm
if (kms?.supportsAlgorithm(Algorithm.Secp256k1) == true) {
    println("secp256k1 is supported")
}
```

## Usage Examples

### Generating Keys

```kotlin
import org.trustweave.kms.*
import org.trustweave.kms.KmsOptionKeys
import org.trustweave.kms.results.*
import org.trustweave.googlekms.GcpKmsOptionKeys

// Generate secp256k1 key
val result = kms.generateKey(Algorithm.Secp256k1)
when (result) {
    is GenerateKeyResult.Success -> {
        val keyHandle = result.keyHandle
        println("Key created: ${keyHandle.id}")
    }
    is GenerateKeyResult.Failure.UnsupportedAlgorithm -> println("Unsupported: ${result.algorithm.name}")
    is GenerateKeyResult.Failure.InvalidOptions -> println("Invalid options: ${result.reason}")
    is GenerateKeyResult.Failure.Error -> println("Error: ${result.reason}")
}

// Generate key with custom ID and labels.
// Google-specific options (KEY_RING, LABELS) live in GcpKmsOptionKeys;
// cross-provider KEY_ID lives in KmsOptionKeys.
val keyWithLabelsResult = kms.generateKey(
    algorithm = Algorithm.P256,
    options = mapOf(
        KmsOptionKeys.KEY_ID to "issuer-key-2025",
        GcpKmsOptionKeys.LABELS to mapOf(
            "environment" to "production",
            "purpose" to "issuance"
        )
    )
)

// Generate P-256 key in a specific key ring (overrides config default)
val fipsKeyResult = kms.generateKey(
    algorithm = Algorithm.P256,
    options = mapOf(
        GcpKmsOptionKeys.KEY_RING to "fips-keys"
    )
)
```

### Signing Data

```kotlin
import org.trustweave.core.identifiers.KeyId

// Sign with key resource name (KeyId value class)
when (val sign = kms.sign(KeyId(keyId), data.toByteArray())) {
    is SignResult.Success -> {
        val signature = sign.signature
        // Use signature
    }
    is SignResult.Failure.KeyNotFound -> println("Key not found: ${sign.keyId}")
    is SignResult.Failure.UnsupportedAlgorithm ->
        println("Algorithm '${sign.requestedAlgorithm?.name}' incompatible with key '${sign.keyAlgorithm.name}'")
    is SignResult.Failure.Error -> println("Error: ${sign.reason}")
}

// Sign with full resource name
val sign2 = kms.sign(
    KeyId("projects/my-project/locations/us-east1/keyRings/my-key-ring/cryptoKeys/my-key"),
    data.toByteArray()
)

// Sign with algorithm override
val sign3 = kms.sign(
    keyId = KeyId(keyId),
    data = data.toByteArray(),
    algorithm = Algorithm.Secp256k1
)
```

### Retrieving Public Keys

```kotlin
// Get public key by key ID (uses config defaults, KeyId value class)
when (val publicKeyResult = kms.getPublicKey(KeyId("my-key"))) {
    is GetPublicKeyResult.Success -> {
        val publicKey = publicKeyResult.keyHandle
        println("Public key JWK: ${publicKey.publicKeyJwk}")
    }
    is GetPublicKeyResult.Failure.KeyNotFound -> println("Not found: ${publicKeyResult.keyId}")
    is GetPublicKeyResult.Failure.Error -> println("Error: ${publicKeyResult.reason}")
}

// Get public key by full resource name
val publicKey2 = kms.getPublicKey(
    KeyId("projects/my-project/locations/us-east1/keyRings/my-key-ring/cryptoKeys/my-key")
)
```

### Key Deletion

```kotlin
// Destroy key version (makes key unusable)
val deleteResult = kms.deleteKey(KeyId(keyId))
when (deleteResult) {
    is DeleteKeyResult.Deleted -> println("Key deleted")
    is DeleteKeyResult.NotFound -> println("Key not found (already deleted)")
    is DeleteKeyResult.Failure.Error -> println("Error: ${deleteResult.reason}")
}

// Note: Google Cloud KMS schedules key version destruction
// The key version enters a "DESTROYED" state and cannot be used
```

## Key Resource Names

Google Cloud KMS uses full resource names for keys:

```
projects/{project}/locations/{location}/keyRings/{keyRing}/cryptoKeys/{key}
```

The plugin supports both:
- **Full resource names**: `projects/my-project/locations/us-east1/keyRings/my-key-ring/cryptoKeys/my-key`
- **Short names**: `my-key` (uses defaults from config)

## Key Versioning

Google Cloud KMS uses key versions. When you create a key, a primary version is automatically created. The plugin:

- Uses the primary version for all operations
- Returns the full resource name as the key ID
- Handles version management automatically

## Error Handling

The plugin maps Google Cloud exceptions to TrustWeave exceptions:

```kotlin
val result = kms.generateKey(Algorithm.Secp256k1)
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
            result.reason.contains("Permission denied", ignoreCase = true) -> {
                println("Check IAM permissions")
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
| `PermissionDeniedException` | Insufficient IAM permissions | Grant `cloudkms.cryptoKeys.create`, `cloudkms.cryptoKeys.useToSign`, `cloudkms.cryptoKeyVersions.useToSign` permissions |
| `NotFoundException` | Key doesn't exist | Verify key resource name is correct |
| `UnsupportedAlgorithmException` | Algorithm not supported | Check algorithm compatibility table |
| `IllegalArgumentException` | Missing required config | Ensure projectId, location, and keyRing are provided |

## IAM Permissions

Your Google Cloud service account needs the following IAM roles:

### Minimum Required Permissions

```json
{
  "bindings": [
    {
      "role": "roles/cloudkms.cryptoKeyEncrypterDecrypter",
      "members": [
        "serviceAccount:my-service-account@my-project.iam.gserviceaccount.com"
      ]
    }
  ]
}
```

### Recommended Roles

For full key management capabilities:

- `roles/cloudkms.admin` - Full key management access
- `roles/cloudkms.cryptoKeyEncrypterDecrypter` - Signing and encryption
- `roles/cloudkms.viewer` - Read-only access to keys

### Key Ring Permissions

Ensure the service account has access to the key ring:

```bash
gcloud kms keyrings add-iam-policy-binding my-key-ring \
    --location=us-east1 \
    --member="serviceAccount:my-service-account@my-project.iam.gserviceaccount.com" \
    --role="roles/cloudkms.cryptoKeyEncrypterDecrypter"
```

## Algorithm Compatibility

See the [Algorithm Compatibility Table](../core-concepts/algorithm-compatibility-table.md) for detailed comparison of algorithm support across DIDs, VCs, AWS KMS, Azure Key Vault, and Google Cloud KMS.

**Key Points:**
- secp256k1 supported for blockchain integration
- All NIST curves (P-256/P-384) supported
- RSA keys supported for legacy compatibility
- Ed25519 support varies by Google Cloud KMS version
- P-521 support varies by Google Cloud KMS version
- BLS12-381 not supported (requires specialized KMS)

## Best Practices

1. **Use Application Default Credentials**: Prefer ADC over service account files for production
2. **Key Rings**: Organize keys by environment or purpose using key rings
3. **Key Labels**: Use labels to tag keys with metadata (environment, purpose, etc.)
4. **Resource Names**: Use full resource names for clarity in production
5. **Error Handling**: Implement proper error handling for Google Cloud exceptions
6. **Key Lifecycle**: Plan key rotation and destruction schedules
7. **Access Control**: Use IAM policies to restrict key access
8. **Key Versioning**: Understand that Google Cloud KMS uses key versions internally

## Testing

### Unit Tests

The module includes unit tests that can be run without Google Cloud credentials:

```bash
./gradlew :kms/plugins/google:test
```

### Integration Tests

For integration testing, use a test Google Cloud project:

```kotlin
// Configure for test project
val kms = googleProvider?.create(mapOf(
    "projectId" to "test-project",
    "location" to "us-east1",
    "keyRing" to "test-key-ring",
    "credentialsPath" to "/path/to/test-service-account.json"
))
```

## Using with TrustWeave

### Basic Setup

```kotlin
import org.trustweave.*
import org.trustweave.googlekms.*

val TrustWeave = TrustWeave.build {
    customKms(
        GoogleCloudKeyManagementService(
            GoogleKmsConfig.builder()
                .projectId("my-project")
                .location("us-east1")
                .keyRing("TrustWeave-keys")
                .build()
        )
    )
}
```

### With SPI Auto-Discovery

```kotlin
val TrustWeave = TrustWeave.build {
    // Google Cloud KMS will be discovered automatically if on classpath
    // Configure via environment variables or system properties
}
```

## Related Documentation

- Key Management Guide](../core-concepts/key-management.md) - Core KMS concepts
- Algorithm Compatibility Table](../core-concepts/algorithm-compatibility-table.md) - Algorithm support comparison
- Key Rotation Guide](../advanced/key-rotation.md) - Key rotation strategies
- Creating Plugins Guide](../contributing/creating-plugins.md) - Custom KMS implementations

## See Also

- Google Cloud KMS Documentation](https://cloud.google.com/kms/docs)
- Google Cloud KMS Java Client](https://github.com/googleapis/java-kms)
- Google Cloud KMS API Reference](https://cloud.google.com/kms/docs/reference/rest)

