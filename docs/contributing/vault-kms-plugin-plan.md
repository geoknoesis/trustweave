---
title: HashiCorp Vault KMS Plugin Implementation Plan
---

# HashiCorp Vault KMS Plugin Implementation Plan

## Overview

This plan outlines the implementation of a HashiCorp Vault Key Management Service (KMS) plugin for TrustWeave. The plugin will be implemented as the `kms/plugins/hashicorp` module and will integrate with Vault's Transit secrets engine to provide cryptographic key operations for DID and Verifiable Credential workflows.

## Background

HashiCorp Vault is a popular open-source secrets management platform that provides:
- **Transit Engine**: Cryptographic operations (encryption, signing, key generation)
- **Key Management**: Centralized key lifecycle management
- **Multi-cloud Support**: Works on-premises and in cloud environments
- **Policy-based Access Control**: Fine-grained access control via Vault policies

## Vault Transit Engine Capabilities

The Transit engine supports the following key types:
- **ed25519**: Ed25519 signing keys
- **ecdsa-p256**: ECDSA P-256 (NIST P-256)
- **ecdsa-p384**: ECDSA P-384 (NIST P-384)
- **ecdsa-p521**: ECDSA P-521 (NIST P-521)
- **ecdsa-p256k1**: ECDSA secp256k1 (Bitcoin/Ethereum curve)
- **rsa-2048**: RSA 2048-bit keys
- **rsa-3072**: RSA 3072-bit keys
- **rsa-4096**: RSA 4096-bit keys

**Note**: Vault Transit does NOT support BLS12-381.

## Implementation Tasks

### 1. Module Structure and Dependencies

**Task**: Create `kms/plugins/hashicorp` module with build configuration

**Files**:
- `kms/plugins/hashicorp/build.gradle.kts`

**Dependencies**:
- `trustweave-common`
- `trustweave-kms`
- Vault Java client library (e.g., `com.bettercloud:vault-java-driver` or direct HTTP client)
- HTTP client (OkHttp or similar)
- JSON serialization (Jackson or Kotlinx Serialization)

**Acceptance Criteria**:
- Module compiles successfully
- All dependencies resolved
- Module included in `settings.gradle.kts`

---

### 2. Configuration Class

**Task**: Implement `VaultKmsConfig` data class with builder pattern

**Files**:
- `kms/plugins/hashicorp/src/main/kotlin/com/geoknoesis/TrustWeave/hashicorpkms/VaultKmsConfig.kt`

**Features**:
- Vault server address (required)
- Authentication methods:
  - Token authentication (default)
  - AppRole authentication (roleId + secretId)
  - Environment variable support (`VAULT_ADDR`, `VAULT_TOKEN`, `VAULT_NAMESPACE`)
- Transit engine path (default: "transit")
- Namespace support (for Vault Enterprise)
- Engine version (default: 2)

**Builder Pattern**:
```kotlin
VaultKmsConfig.builder()
    .address("http://localhost:8200")
    .token("hvs.xxx")
    .transitPath("transit")
    .namespace("admin")
    .build()
```

**Environment Variable Support**:
- `VAULT_ADDR` - Vault server address
- `VAULT_TOKEN` - Authentication token
- `VAULT_NAMESPACE` - Vault namespace (Enterprise)
- `VAULT_TRANSIT_PATH` - Transit engine path (optional, defaults to "transit")

**Acceptance Criteria**:
- Configuration can be created from builder
- Configuration can be loaded from environment variables
- Configuration can be created from options map
- Validation ensures required fields are present

---

### 3. Algorithm Mapping

**Task**: Implement `AlgorithmMapping` utilities for TrustWeave ↔ Vault Transit mapping

**Files**:
- `kms/plugins/hashicorp/src/main/kotlin/com/geoknoesis/TrustWeave/hashicorpkms/AlgorithmMapping.kt`

**Mappings**:
- `Algorithm.Ed25519` → `"ed25519"`
- `Algorithm.Secp256k1` → `"ecdsa-p256k1"`
- `Algorithm.P256` → `"ecdsa-p256"`
- `Algorithm.P384` → `"ecdsa-p384"`
- `Algorithm.P521` → `"ecdsa-p521"`
- `Algorithm.RSA.RSA_2048` → `"rsa-2048"`
- `Algorithm.RSA.RSA_3072` → `"rsa-3072"`
- `Algorithm.RSA.RSA_4096` → `"rsa-4096"`

**Functions**:
- `toVaultKeyType(algorithm: Algorithm): String` - Convert TrustWeave algorithm to Vault key type
- `fromVaultKeyType(keyType: String): Algorithm?` - Parse Vault key type to TrustWeave algorithm
- `resolveKeyName(keyId: String, config: VaultKmsConfig): String` - Resolve key name for Vault API

**Acceptance Criteria**:
- All supported algorithms map correctly
- Unsupported algorithms throw appropriate exceptions
- Key name resolution handles various formats

---

### 4. Vault Client Factory

**Task**: Implement `VaultKmsClientFactory` for creating Vault HTTP clients

**Files**:
- `kms/plugins/hashicorp/src/main/kotlin/com/geoknoesis/TrustWeave/hashicorpkms/VaultKmsClientFactory.kt`

**Features**:
- HTTP client creation with proper authentication
- Token-based authentication
- AppRole authentication (if configured)
- Namespace header support (Vault Enterprise)
- Error handling and retry logic

**Vault API Endpoints** (Transit engine):
- `POST /v1/{transitPath}/keys/{keyName}` - Create key
- `GET /v1/{transitPath}/keys/{keyName}` - Get key info
- `POST /v1/{transitPath}/keys/{keyName}/config` - Update key config
- `POST /v1/{transitPath}/sign/{keyName}` - Sign data
- `GET /v1/{transitPath}/keys/{keyName}/{version}` - Get public key

**Acceptance Criteria**:
- Client created with correct authentication
- Namespace headers added when configured
- Proper error handling for authentication failures

---

### 5. Vault Key Management Service

**Task**: Implement `VaultKeyManagementService` implementing `KeyManagementService` interface

**Files**:
- `kms/plugins/hashicorp/src/main/kotlin/com/geoknoesis/TrustWeave/hashicorpkms/VaultKeyManagementService.kt`

**Supported Algorithms**:
- Ed25519
- secp256k1
- P-256, P-384, P-521
- RSA-2048, RSA-3072, RSA-4096

**Methods Implementation**:

1. **`getSupportedAlgorithms()`**:
   - Return set of supported algorithms

2. **`generateKey(algorithm, options)`**:
   - Use Vault Transit API: `POST /v1/{transitPath}/keys/{keyName}`
   - Extract key name from options or generate UUID
   - Set key type based on algorithm
   - Retrieve public key after creation
   - Return `KeyHandle` with key name, algorithm, and public key JWK

3. **`getPublicKey(keyId)`**:
   - Use Vault Transit API: `GET /v1/{transitPath}/keys/{keyName}`
   - Extract public key from response
   - Convert to JWK format
   - Return `KeyHandle`

4. **`sign(keyId, data, algorithm)`**:
   - Use Vault Transit API: `POST /v1/{transitPath}/sign/{keyName}`
   - Base64 encode input data
   - Specify hash algorithm (SHA-256, SHA-384, SHA-512 based on key type)
   - Return signature bytes

5. **`deleteKey(keyId)`**:
   - Use Vault Transit API: `DELETE /v1/{transitPath}/keys/{keyName}`
   - Return true if successful, false if key doesn't exist

**Key Naming**:
- Keys are identified by name in Vault Transit
- Format: `{transitPath}/keys/{keyName}`
- Key names should be URL-safe

**Public Key Format**:
- Vault Transit returns public keys in PEM format
- Convert PEM to JWK for `KeyHandle.publicKeyJwk`

**Error Handling**:
- Map Vault API errors to TrustWeave exceptions
- `404` → `KeyNotFoundException`
- `403` → `TrustWeaveException` with access denied message
- `400` → `UnsupportedAlgorithmException` or `TrustWeaveException`

**Acceptance Criteria**:
- All `KeyManagementService` methods implemented
- Proper error handling and exception mapping
- Public keys converted to JWK format
- Algorithm advertisement working correctly

---

### 6. Vault Key Management Service Provider

**Task**: Implement `VaultKeyManagementServiceProvider` for SPI registration

**Files**:
- `kms/plugins/hashicorp/src/main/kotlin/com/geoknoesis/TrustWeave/hashicorpkms/VaultKeyManagementServiceProvider.kt`

**Features**:
- Provider name: `"vault"` or `"hashicorp-vault"`
- Advertise supported algorithms
- Create `VaultKeyManagementService` from options
- Support configuration from options map or environment variables

**Acceptance Criteria**:
- Provider discoverable via ServiceLoader
- Configuration loaded correctly
- Service created successfully

---

### 7. SPI Registration

**Task**: Create SPI registration file

**Files**:
- `kms/plugins/hashicorp/src/main/resources/META-INF/services/com.trustweave.kms.spi.KeyManagementServiceProvider`

**Content**:
```
com.trustweave.hashicorpkms.VaultKeyManagementServiceProvider
```

**Acceptance Criteria**:
- Provider automatically discovered when module is on classpath

---

### 8. Unit Tests

**Task**: Create comprehensive unit tests

**Files**:
- `kms/plugins/hashicorp/src/test/kotlin/com/geoknoesis/TrustWeave/hashicorpkms/VaultKmsConfigTest.kt`
- `kms/plugins/hashicorp/src/test/kotlin/com/geoknoesis/TrustWeave/hashicorpkms/AlgorithmMappingTest.kt`
- `kms/plugins/hashicorp/src/test/kotlin/com/geoknoesis/TrustWeave/hashicorpkms/VaultKeyManagementServiceTest.kt`
- `kms/plugins/hashicorp/src/test/kotlin/com/geoknoesis/TrustWeave/hashicorpkms/VaultKeyManagementServiceProviderTest.kt`

**Test Coverage**:
- Configuration creation and validation
- Algorithm mapping (all supported algorithms)
- Key generation for each algorithm
- Public key retrieval
- Signing operations
- Error handling (key not found, authentication failures)
- Provider discovery and creation

**Mocking Strategy**:
- Mock Vault HTTP client responses
- Use testcontainers or local Vault instance for integration tests (optional)

**Acceptance Criteria**:
- All tests pass
- Good test coverage (>80%)
- Tests run without requiring actual Vault instance

---

### 9. Integration with Build System

**Task**: Update root settings to include new module

**Files**:
- `settings.gradle.kts`

**Changes**:
- Add `"kms/plugins/hashicorp"` to `include()` list

**Acceptance Criteria**:
- Module included in build
- Module compiles successfully

---

### 10. Documentation

**Task**: Create comprehensive documentation

**Files**:
- `docs/integrations/hashicorp-vault-kms.md`

**Sections**:
1. Overview
2. Installation
3. Configuration
   - Basic configuration
   - Authentication methods (Token, AppRole)
   - Environment variables
   - Namespace support (Enterprise)
4. Algorithm Support
   - Supported algorithms table
   - Vault Transit key types
5. Usage Examples
   - Key generation
   - Signing
   - Public key retrieval
   - Key rotation
6. Vault Setup
   - Enabling Transit engine
   - Creating policies
   - Key naming conventions
7. Error Handling
8. Best Practices
9. Troubleshooting

**Update**:
- `docs/integrations/README.md` - Add Vault KMS to integrations list

**Acceptance Criteria**:
- Complete documentation with examples
- Clear configuration instructions
- Troubleshooting guide included

---

## Vault Transit Engine API Reference

### Key Creation
```http
POST /v1/transit/keys/{keyName}
{
  "type": "ed25519",
  "convergent_encryption": false,
  "derived": false,
  "exportable": false,
  "allow_plaintext_backup": false
}
```

### Get Key Info
```http
GET /v1/transit/keys/{keyName}
```

Response includes:
- `keys`: Map of key versions
- `latest_version`: Latest key version
- `type`: Key type
- `public_key`: Public key in PEM format

### Sign Data
```http
POST /v1/transit/sign/{keyName}
{
  "input": "base64-encoded-data",
  "hash_algorithm": "sha2-256",
  "marshaling_algorithm": "asn1"
}
```

### Get Public Key
```http
GET /v1/transit/keys/{keyName}/{version}
```

---

## Algorithm Support Matrix

| TrustWeave Algorithm | Vault Transit Key Type | Hash Algorithm | Notes |
|-------------------|------------------------|----------------|-------|
| Ed25519 | `ed25519` | N/A (direct signing) | Native Ed25519 support |
| secp256k1 | `ecdsa-p256k1` | `sha2-256` | Blockchain-compatible |
| P-256 | `ecdsa-p256` | `sha2-256` | FIPS-compliant |
| P-384 | `ecdsa-p384` | `sha2-384` | FIPS-compliant |
| P-521 | `ecdsa-p521` | `sha2-512` | FIPS-compliant |
| RSA-2048 | `rsa-2048` | `sha2-256` | Legacy support |
| RSA-3072 | `rsa-3072` | `sha2-256` | Higher security |
| RSA-4096 | `rsa-4096` | `sha2-256` | Maximum security |

---

## Authentication Methods

### 1. Token Authentication (Default)
```kotlin
val config = VaultKmsConfig.builder()
    .address("http://localhost:8200")
    .token("hvs.xxx")
    .build()
```

### 2. AppRole Authentication
```kotlin
val config = VaultKmsConfig.builder()
    .address("http://localhost:8200")
    .appRolePath("approle")
    .roleId("xxx")
    .secretId("yyy")
    .build()
```

### 3. Environment Variables
```bash
export VAULT_ADDR=http://localhost:8200
export VAULT_TOKEN=hvs.xxx
export VAULT_NAMESPACE=admin
```

---

## Key Naming Conventions

Vault Transit uses key names (not IDs) to identify keys. Recommendations:
- Use descriptive names: `did-issuer-key`, `vc-signing-key`
- Include algorithm in name: `ed25519-main-key`
- Use hierarchical naming: `production/issuer/ed25519-key`
- Avoid special characters (use hyphens, underscores)

---

## Vault Policy Example

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

---

## Testing Strategy

### Unit Tests
- Mock Vault HTTP responses
- Test algorithm mappings
- Test configuration parsing
- Test error handling

### Integration Tests (Optional)
- Use Testcontainers with Vault container
- Test against real Vault instance
- Verify end-to-end key operations

---

## Dependencies

### Required
- `trustweave-common`
- `trustweave-kms`
- HTTP client (OkHttp or similar)
- JSON library (Jackson or Kotlinx Serialization)

### Optional
- Vault Java client library (if using existing client)
- Testcontainers (for integration tests)

---

## Implementation Order

1. Module structure and dependencies
2. Configuration class
3. Algorithm mapping
4. Vault client factory
5. Key management service (core implementation)
6. Service provider
7. SPI registration
8. Unit tests
9. Build system integration
10. Documentation

---

## Success Criteria

- ✅ All TrustWeave algorithms supported (except BLS12-381)
- ✅ Token and AppRole authentication working
- ✅ Environment variable configuration supported
- ✅ Proper error handling and exception mapping
- ✅ Public keys converted to JWK format
- ✅ Comprehensive test coverage
- ✅ Complete documentation
- ✅ Module compiles and integrates successfully

---

## Future Enhancements

- Key rotation support using Vault's key versioning
- Key export support (if exportable keys enabled)
- Convergent encryption support
- Integration with Vault's PKI engine
- Support for Vault's KMIP secrets engine

---

## References

- [HashiCorp Vault Transit Engine Documentation](https://developer.hashicorp.com/vault/docs/secrets/transit)
- [Vault API Reference](https://developer.hashicorp.com/vault/api-docs)
- [Vault Java Client Libraries](https://github.com/hashicorp/vault-java-driver)

