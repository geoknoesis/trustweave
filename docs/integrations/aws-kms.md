---
title: AWS KMS Integration
---

# AWS KMS Integration

> This guide covers the AWS Key Management Service (KMS) integration for TrustWeave. The AWS KMS plugin provides production-ready key management with FIPS 140-3 Level 3 compliance and support for all AWS KMS-compatible algorithms.

## Overview

The `kms/plugins/aws` module provides a complete implementation of TrustWeave's `KeyManagementService` interface using AWS Key Management Service. This integration enables you to:

- Use AWS KMS for secure key generation and storage with **FIPS 140-3 Level 3 validated HSMs**
- Leverage AWS KMS's automatic key rotation capabilities
- Support all AWS KMS-compatible algorithms (Ed25519, secp256k1, P-256/P-384/P-521, RSA)
- Integrate with existing AWS infrastructure and IAM policies
- Meet regulatory compliance requirements with FIPS-validated cryptographic operations

## Installation

Add the AWS KMS module to your dependencies:

```kotlin
dependencies {
    // Only need to add the AWS KMS plugin - core dependencies are included transitively
    implementation("com.trustweave.kms:aws:1.0.0-SNAPSHOT")
}
```

**Note:** The AWS KMS plugin automatically includes `trustweave-kms` and `trustweave-common` as transitive dependencies, so you don't need to declare them explicitly.

## Configuration

### Basic Configuration

The AWS KMS provider supports both **typed configuration** (recommended) and **Map-based configuration** (for backward compatibility).

#### Typed Configuration (Recommended)

Use the type-safe builder for compile-time safety and IDE autocomplete:

```kotlin
import com.trustweave.kms.*
import com.trustweave.awskms.awsKmsOptions

// Simple and elegant - no ServiceLoader needed!
val kms = KeyManagementServices.create("aws", awsKmsOptions {
    region = "us-east-1"
})
```

#### Map-Based Configuration (Legacy)

For backward compatibility, you can still use Map-based configuration:

```kotlin
import com.trustweave.kms.*

// Simple factory API
val kms = KeyManagementServices.create("aws", mapOf(
    "region" to "us-east-1"
))
```

**Benefits of Typed Configuration:**
- ✅ Compile-time type safety
- ✅ IDE autocomplete for available options
- ✅ Better documentation and discoverability
- ✅ Validation at construction time
- ✅ Easier refactoring

### Authentication

The plugin supports multiple authentication methods:

#### 1. IAM Roles (Recommended for Production)

When running on AWS infrastructure (EC2, ECS, Lambda), use IAM roles:

**Typed Configuration:**
```kotlin
import com.trustweave.kms.*
import com.trustweave.awskms.awsKmsOptions

// No credentials needed - uses IAM role automatically
val kms = KeyManagementServices.create("aws", awsKmsOptions {
    region = "us-east-1"
})
```

**Map Configuration:**
```kotlin
import com.trustweave.kms.*

// No credentials needed - uses IAM role automatically
val kms = KeyManagementServices.create("aws", mapOf(
    "region" to "us-east-1"
))
```

#### 2. Access Keys

For local development or non-AWS environments:

**Typed Configuration:**
```kotlin
import com.trustweave.kms.*
import com.trustweave.awskms.awsKmsOptions

val kms = KeyManagementServices.create("aws", awsKmsOptions {
    region = "us-east-1"
    accessKeyId = "AKIA..."
    secretAccessKey = "..."
})
```

**Map Configuration:**
```kotlin
import com.trustweave.kms.*

val kms = KeyManagementServices.create("aws", mapOf(
    "region" to "us-east-1",
    "accessKeyId" to "AKIA...",
    "secretAccessKey" to "..."
))
```

#### 3. Environment Variables

The plugin automatically reads from environment variables:

```bash
export AWS_REGION=us-east-1
export AWS_ACCESS_KEY_ID=AKIA...
export AWS_SECRET_ACCESS_KEY=...
```

```kotlin
// Configuration loaded from environment automatically
val config = AwsKmsConfig.fromEnvironment()
val kms = AwsKeyManagementService(config ?: throw IllegalStateException("AWS config not found"))
```

### LocalStack Configuration (Testing)

For local testing with LocalStack:

**Typed Configuration:**
```kotlin
import com.trustweave.kms.*
import com.trustweave.awskms.awsKmsOptions

val kms = KeyManagementServices.create("aws", awsKmsOptions {
    region = "us-east-1"
    endpointOverride = "http://localhost:4566"
})
```

**Map Configuration:**
```kotlin
import com.trustweave.kms.*

val kms = KeyManagementServices.create("aws", mapOf(
    "region" to "us-east-1",
    "endpointOverride" to "http://localhost:4566"
))
```

### Advanced Configuration Options

The typed builder supports all AWS KMS configuration options:

```kotlin
import com.trustweave.kms.*
import com.trustweave.awskms.awsKmsOptions

val kms = KeyManagementServices.create("aws", awsKmsOptions {
    region = "us-east-1"
    accessKeyId = "AKIA..."           // Optional: for explicit credentials
    secretAccessKey = "..."            // Optional: for explicit credentials
    sessionToken = "..."              // Optional: for temporary credentials
    endpointOverride = "http://..."  // Optional: for LocalStack or custom endpoints
    pendingWindowInDays = 30          // Optional: key deletion pending window (7-30 days)
    cacheTtlSeconds = 300             // Optional: public key cache TTL (default: 300)
    enabled = true                    // Optional: enable/disable provider (default: true)
    priority = 10                     // Optional: provider priority for chaining
})
```

## Algorithm Support

The AWS KMS plugin supports all AWS KMS-compatible algorithms. AWS KMS uses FIPS 140-3 Level 3 validated hardware security modules (HSMs) as documented in [NIST Certificate #4884](https://csrc.nist.gov/projects/cryptographic-module-validation-program/certificate/4884).

| Algorithm | AWS KMS Key Spec | Signing Algorithm | FIPS 140-3 Status | Notes |
|-----------|-----------------|-------------------|-------------------|-------|
| Ed25519 | `ECC_Ed25519` | `ECDSA_SHA_256` | ⚠️ Not in FIPS cert | Supported as of Nov 2025 (may use non-FIPS validated path) |
| secp256k1 | `ECC_SECG_P256K1` | `ECDSA_SHA_256` | ✅ Allowed | Blockchain-only use per FIPS certificate |
| P-256 | `ECC_NIST_P256` | `ECDSA_SHA_256` | ✅ Approved | FIPS 140-3 Level 3 validated |
| P-384 | `ECC_NIST_P384` | `ECDSA_SHA_384` | ✅ Approved | FIPS 140-3 Level 3 validated |
| P-521 | `ECC_NIST_P521` | `ECDSA_SHA_512` | ✅ Approved | FIPS 140-3 Level 3 validated |
| RSA-2048 | `RSA_2048` | `RSASSA_PKCS1_V1_5_SHA_256` | ✅ Approved | FIPS 140-3 Level 3 validated |
| RSA-3072 | `RSA_3072` | `RSASSA_PKCS1_V1_5_SHA_256` | ✅ Approved | FIPS 140-3 Level 3 validated |
| RSA-4096 | `RSA_4096` | `RSASSA_PKCS1_V1_5_SHA_256` | ✅ Approved | FIPS 140-3 Level 3 validated |

### FIPS 140-3 Compliance

AWS KMS uses FIPS 140-3 Level 3 validated HSMs (Certificate #4884, validated 11/18/2024). The validated module supports:

- **ECDSA** (FIPS 186-4) on NIST curves (P-256, P-384, P-521) - ✅ Approved
- **ECDSA secp256k1** (FIPS 186-4) - ✅ Allowed (blockchain applications only)
- **RSA** (FIPS 186-4) - ✅ Approved for all key sizes

**Important Notes:**
- Ed25519 support was added in November 2025 and may not be covered by the current FIPS certificate
- secp256k1 is allowed per FIPS certificate but restricted to blockchain-related applications
- For maximum FIPS compliance, use P-256, P-384, P-521, or RSA algorithms

### Checking Algorithm Support

```kotlin
import com.trustweave.kms.*
import com.trustweave.awskms.awsKmsOptions

val kms = KeyManagementServices.create("aws", awsKmsOptions {
    region = "us-east-1"
})

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
import com.trustweave.kms.KmsOptionKeys
import com.trustweave.kms.results.*

// Generate Ed25519 key
val result = kms.generateKey(Algorithm.Ed25519)
when (result) {
    is GenerateKeyResult.Success -> {
        val keyHandle = result.keyHandle
        println("Key created: ${keyHandle.id}")
    }
    is GenerateKeyResult.Failure -> {
        println("Error: ${result.reason}")
    }
}

// Generate key with alias (for easier rotation)
val keyWithAliasResult = kms.generateKey(
    algorithm = Algorithm.Ed25519,
    options = mapOf(
        KmsOptionKeys.ALIAS to "alias/issuer-key",
        KmsOptionKeys.DESCRIPTION to "Issuer signing key",
        KmsOptionKeys.ENABLE_AUTOMATIC_ROTATION to true
    )
)

// Generate P-256 key for FIPS compliance
val fipsKeyResult = kms.generateKey(
    algorithm = Algorithm.P256,
    options = mapOf(
        KmsOptionKeys.DESCRIPTION to "FIPS-compliant issuer key"
    )
)
```

### Signing Data

```kotlin
// Sign with key ID (KeyId value class)
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

// Sign with algorithm override
val sign = kms.sign(
    keyId = KeyId(keyId),
    data = data.toByteArray(),
    algorithm = Algorithm.Ed25519
)

// Sign using alias
val sign = kms.sign(KeyId("alias/issuer-key"), data.toByteArray())
```

### Retrieving Public Keys

```kotlin
// Get public key by key ID (KeyId value class)
val publicKeyResult = kms.getPublicKey(KeyId(keyId))
when (publicKeyResult) {
    is GetPublicKeyResult.Success -> {
        val publicKey = publicKeyResult.keyHandle
        // Use public key
    }
    is GetPublicKeyResult.Failure -> {
        println("Error: ${publicKeyResult.reason}")
    }
}

// Get public key by ARN
val publicKey = kms.getPublicKey(KeyId("arn:aws:kms:us-east-1:123456789012:key/123"))

// Get public key by alias
val publicKey = kms.getPublicKey(KeyId("alias/issuer-key"))

// Access JWK format
val jwk = publicKey.publicKeyJwk
println("Public key JWK: $jwk")
```

### Key Deletion

```kotlin
// Schedule key deletion (30-day pending window by default)
val deleteResult = kms.deleteKey(KeyId(keyId))
when (deleteResult) {
    is DeleteKeyResult.Deleted -> println("Key scheduled for deletion")
    is DeleteKeyResult.NotFound -> println("Key not found (already deleted)")
    is DeleteKeyResult.Failure.Error -> println("Error: ${deleteResult.reason}")
}
```

## Key Rotation Strategies

AWS KMS supports two types of key rotation:

### 1. AWS KMS Automatic Rotation

AWS KMS can automatically rotate customer-managed keys annually:

```kotlin
// Enable automatic rotation when creating key
val result = kms.generateKey(
    algorithm = Algorithm.Ed25519,
    options = mapOf(
        KmsOptionKeys.ENABLE_AUTOMATIC_ROTATION to true
    )
)
when (result) {
    is GenerateKeyResult.Success -> {
        val keyHandle = result.keyHandle
        // Key created with automatic rotation enabled
    }
    is GenerateKeyResult.Failure -> {
        // Handle error
    }
}

// The same key ID/alias continues to work
// AWS KMS uses newer key material internally
```

**Benefits:**
- No code changes required
- Same key ID/alias works across rotations
- Automatic and transparent

**Limitations:**
- Only for customer-managed keys
- Annual rotation schedule
- Historical key versions maintained by AWS

### 2. TrustWeave Manual Rotation Pattern

For more control, use TrustWeave's manual rotation pattern:

```kotlin
// Step 1: Generate new key
val newKeyResult = kms.generateKey(
    algorithm = Algorithm.Ed25519,
    options = mapOf(
        KmsOptionKeys.ALIAS to "alias/issuer-key-v2",
        KmsOptionKeys.DESCRIPTION to "Issuer key rotated 2025-01-15"
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

**Benefits:**
- Full control over rotation schedule
- Can rotate immediately in response to incidents
- Clear audit trail of key changes

### Using Key Aliases for Seamless Rotation

Key aliases make rotation easier:

```kotlin
// Create key with alias
val key1Result = kms.generateKey(
    algorithm = Algorithm.Ed25519,
    options = mapOf(KmsOptionKeys.ALIAS to "alias/issuer-key")
)

// Later, create new key and update alias
val key2Result = kms.generateKey(
    algorithm = Algorithm.Ed25519,
    options = mapOf(KmsOptionKeys.ALIAS to "alias/issuer-key-temp")
)

// Update alias to point to new key (requires AWS KMS extension)
// kms.updateKeyAlias("alias/issuer-key", key2.id)

// Old key remains accessible by its ID for historical verification
```

## Error Handling

The plugin maps AWS exceptions to TrustWeave exceptions:

```kotlin
val result = kms.generateKey(Algorithm.Ed25519)
when (result) {
    is GenerateKeyResult.Success -> {
        val keyHandle = result.keyHandle
        // Use key handle
    }
    is GenerateKeyResult.Failure.UnsupportedAlgorithm -> {
        println("Algorithm not supported: ${result.algorithm.name}")
        println("Supported algorithms: ${result.supportedAlgorithms.joinToString { it.name }}")
    }
    is GenerateKeyResult.Failure.InvalidOptions -> {
        println("Invalid options: ${result.reason}")
    }
    is GenerateKeyResult.Failure.Error -> {
        when {
            result.reason.contains("Access denied", ignoreCase = true) -> {
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
| `AccessDeniedException` | Insufficient IAM permissions | Grant `kms:CreateKey`, `kms:Sign`, `kms:GetPublicKey` permissions |
| `NotFoundException` | Key doesn't exist | Verify key ID/ARN/alias is correct |
| `InvalidKeyUsageException` | Wrong key usage type | Ensure key is created with `SIGN_VERIFY` usage |
| `UnsupportedAlgorithmException` | Algorithm not supported | Check algorithm compatibility table |

## IAM Permissions

Your AWS IAM role/user needs the following permissions:

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "kms:CreateKey",
                "kms:DescribeKey",
                "kms:GetPublicKey",
                "kms:Sign",
                "kms:ScheduleKeyDeletion",
                "kms:EnableKeyRotation",
                "kms:CreateAlias",
                "kms:UpdateAlias"
            ],
            "Resource": "*"
        }
    ]
}
```

For production, restrict resources to specific key ARNs:

```json
{
    "Resource": [
        "arn:aws:kms:us-east-1:123456789012:key/*",
        "arn:aws:kms:us-east-1:123456789012:alias/*"
    ]
}
```

## Algorithm Compatibility

See the [Algorithm Compatibility Table](../core-concepts/algorithm-compatibility-table.md) for detailed comparison of algorithm support across DIDs, VCs, AWS KMS, and Azure Key Vault.

**Key Points:**
- ✅ AWS KMS supports Ed25519 (as of Nov 2025)
- ✅ All NIST curves (P-256/P-384/P-521) supported
- ✅ secp256k1 supported for blockchain integration
- ✅ RSA keys supported for legacy compatibility
- ❌ BLS12-381 not supported (requires specialized KMS)

## Best Practices

1. **Use IAM Roles**: Prefer IAM roles over access keys for production
2. **Key Aliases**: Use aliases for easier key rotation
3. **Automatic Rotation**: Enable automatic rotation for customer-managed keys
4. **Key Descriptions**: Always provide descriptive key descriptions
5. **Error Handling**: Implement proper error handling for AWS exceptions
6. **Key Lifecycle**: Plan key rotation and deletion schedules
7. **Access Control**: Use IAM policies to restrict key access

## Testing

### Unit Tests

The module includes unit tests that can be run without AWS credentials:

```bash
./gradlew :kms/plugins/aws:test
```

### Integration Tests with LocalStack

For integration testing, use LocalStack:

**Typed Configuration:**
```kotlin
import com.trustweave.kms.*
import com.trustweave.awskms.awsKmsOptions

// Configure for LocalStack
val kms = KeyManagementServices.create("aws", awsKmsOptions {
    region = "us-east-1"
    endpointOverride = "http://localhost:4566"
})
```

**Map Configuration:**
```kotlin
import com.trustweave.kms.*

// Configure for LocalStack
val kms = KeyManagementServices.create("aws", mapOf(
    "region" to "us-east-1",
    "endpointOverride" to "http://localhost:4566"
))
```

Start LocalStack:

```bash
docker run -d -p 4566:4566 localstack/localstack
```

## Related Documentation

- [Key Management Guide](../core-concepts/key-management.md) - Core KMS concepts
- [Algorithm Compatibility Table](../core-concepts/algorithm-compatibility-table.md) - Algorithm support comparison
- [Key Rotation Guide](../advanced/key-rotation.md) - Key rotation strategies
- [Creating Plugins Guide](../contributing/creating-plugins.md) - Custom KMS implementations

## FIPS 140-3 Compliance

AWS KMS uses FIPS 140-3 Level 3 validated hardware security modules. For details, see:

- [NIST CMVP Certificate #4884](https://csrc.nist.gov/projects/cryptographic-module-validation-program/certificate/4884) - AWS Key Management Service HSM validation
- Certificate Status: Active (Sunset: 11/17/2026)
- Security Level: Level 3
- Module Type: Hardware (Multi-Chip Stand Alone)

The validated module supports ECDSA (FIPS 186-4) on NIST curves, ECDSA secp256k1 (blockchain use only), and RSA (FIPS 186-4) for all key sizes.

## See Also

- [AWS KMS Developer Guide](https://docs.aws.amazon.com/kms/latest/developerguide/)
- [AWS KMS API Reference](https://docs.aws.amazon.com/kms/latest/APIReference/)
- [NIST CMVP Certificate #4884](https://csrc.nist.gov/projects/cryptographic-module-validation-program/certificate/4884) - FIPS 140-3 validation details

