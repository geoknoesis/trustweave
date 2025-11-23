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
    implementation("com.trustweave.kms:google:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-kms:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-common:1.0.0-SNAPSHOT")
}
```

## Configuration

### Basic Configuration

The Google Cloud KMS provider can be configured via options map or environment variables:

```kotlin
import com.trustweave.kms.*
import com.trustweave.googlekms.*
import java.util.ServiceLoader

// Discover Google Cloud KMS provider
val providers = ServiceLoader.load(KeyManagementServiceProvider::class.java)
val googleProvider = providers.find { it.name == "google-cloud-kms" }

// Create KMS with explicit configuration
val kms = googleProvider?.create(mapOf(
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
import com.trustweave.googlekms.*

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
| secp256k1 | `EC_SIGN_SECP256K1_SHA256` | Blockchain-compatible |
| P-256 | `EC_SIGN_P256_SHA256` | FIPS 140-2 compliant |
| P-384 | `EC_SIGN_P384_SHA384` | FIPS 140-2 compliant |
| RSA-2048 | `RSA_SIGN_PKCS1_2048_SHA256` | Legacy support |
| RSA-3072 | `RSA_SIGN_PKCS1_3072_SHA256` | Higher security |
| RSA-4096 | `RSA_SIGN_PKCS1_4096_SHA256` | Maximum security |

**Note:** Ed25519 and P-521 support may vary by Google Cloud KMS version. The plugin will throw a clear error message if these algorithms are not available, suggesting alternatives.

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
import com.trustweave.kms.*

// Generate secp256k1 key
val key = kms.generateKey(Algorithm.Secp256k1)

// Generate key with custom ID and labels
val keyWithLabels = kms.generateKey(
    algorithm = Algorithm.P256,
    options = mapOf(
        "keyId" to "issuer-key-2025",
        "labels" to mapOf(
            "environment" to "production",
            "purpose" to "issuance"
        )
    )
)

// Generate P-256 key for FIPS compliance
val fipsKey = kms.generateKey(
    algorithm = Algorithm.P256,
    options = mapOf(
        "keyRing" to "fips-keys" // Override default key ring
    )
)
```

### Signing Data

```kotlin
// Sign with key resource name
val signature = kms.sign(keyId, data.toByteArray())

// Sign with full resource name
val signature = kms.sign(
    "projects/my-project/locations/us-east1/keyRings/my-key-ring/cryptoKeys/my-key",
    data.toByteArray()
)

// Sign with algorithm override
val signature = kms.sign(
    keyId = keyId,
    data = data.toByteArray(),
    algorithm = Algorithm.Secp256k1
)
```

### Retrieving Public Keys

```kotlin
// Get public key by key ID (uses config defaults)
val publicKey = kms.getPublicKey("my-key")

// Get public key by full resource name
val publicKey = kms.getPublicKey(
    "projects/my-project/locations/us-east1/keyRings/my-key-ring/cryptoKeys/my-key"
)

// Access JWK format
val jwk = publicKey.publicKeyJwk
println("Public key JWK: $jwk")
```

### Key Deletion

```kotlin
// Destroy key version (makes key unusable)
val deleted = kms.deleteKey(keyId)

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
try {
    val key = kms.generateKey(Algorithm.Secp256k1)
} catch (e: UnsupportedAlgorithmException) {
    println("Algorithm not supported: ${e.message}")
} catch (e: KeyNotFoundException) {
    println("Key not found: ${e.message}")
} catch (e: TrustWeaveException) {
    when {
        e.message?.contains("Permission denied") == true -> {
            println("Check IAM permissions")
        }
        e.message?.contains("Key not found") == true -> {
            println("Key does not exist")
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
- ✅ secp256k1 supported for blockchain integration
- ✅ All NIST curves (P-256/P-384) supported
- ✅ RSA keys supported for legacy compatibility
- ⚠️ Ed25519 support varies by Google Cloud KMS version
- ⚠️ P-521 support varies by Google Cloud KMS version
- ❌ BLS12-381 not supported (requires specialized KMS)

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
import com.trustweave.*
import com.trustweave.googlekms.*

val TrustWeave = TrustWeave.create {
    kms = GoogleCloudKeyManagementService(
        GoogleKmsConfig.builder()
            .projectId("my-project")
            .location("us-east1")
            .keyRing("TrustWeave-keys")
            .build()
    )
}
```

### With SPI Auto-Discovery

```kotlin
val TrustWeave = TrustWeave.create {
    // Google Cloud KMS will be discovered automatically if on classpath
    // Configure via environment variables or system properties
}
```

## Related Documentation

- [Key Management Guide](../core-concepts/key-management.md) - Core KMS concepts
- [Algorithm Compatibility Table](../core-concepts/algorithm-compatibility-table.md) - Algorithm support comparison
- [Key Rotation Guide](../advanced/key-rotation.md) - Key rotation strategies
- [Creating Plugins Guide](../contributing/creating-plugins.md) - Custom KMS implementations

## See Also

- [Google Cloud KMS Documentation](https://cloud.google.com/kms/docs)
- [Google Cloud KMS Java Client](https://github.com/googleapis/java-kms)
- [Google Cloud KMS API Reference](https://cloud.google.com/kms/docs/reference/rest)

