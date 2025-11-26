---
title: Data Types
nav_order: 3
parent: API Reference
keywords:
  - data types
  - types
  - models
  - structures
---

# Data Types Reference

Complete reference for all TrustWeave data types.

## Core Types

### DidDocument

W3C-compliant DID document.

```kotlin
data class DidDocument(
    val id: String,                          // DID string (e.g., "did:key:z6Mk...")
    val verificationMethod: List<VerificationMethod>,  // Public keys
    val authentication: List<String>,        // Authentication key references
    val assertionMethod: List<String>,       // Assertion key references
    val service: List<Service>? = null       // Optional service endpoints
)
```

**Properties:**
- `id`: The DID identifier
- `verificationMethod`: Array of verification methods with public keys
- `authentication`: Authentication key references
- `assertionMethod`: Assertion key references (for signing)
- `service`: Optional service endpoints

### VerifiableCredential

W3C-compliant verifiable credential.

```kotlin
data class VerifiableCredential(
    val id: String,                           // Credential ID
    val type: List<String>,                  // Credential types
    val issuer: String,                       // Issuer DID
    val issuanceDate: String,                 // ISO 8601 timestamp
    val credentialSubject: JsonObject,       // Subject claims
    val proof: Proof,                        // Cryptographic proof
    val expirationDate: String? = null,     // Optional expiration
    val credentialStatus: CredentialStatus? = null  // Optional revocation status
)
```

**Properties:**
- `id`: Unique credential identifier
- `type`: Credential types (must include "VerifiableCredential")
- `issuer`: Issuer DID
- `issuanceDate`: When credential was issued (ISO 8601)
- `credentialSubject`: The claims being made (JsonObject)
- `proof`: Cryptographic proof (signature)
- `expirationDate`: Optional expiration date
- `credentialStatus`: Optional revocation status

### VerifiablePresentation

W3C-compliant verifiable presentation.

```kotlin
data class VerifiablePresentation(
    val id: String,                           // Presentation ID
    val type: List<String>,                  // Presentation types
    val holder: String,                       // Holder DID
    val verifiableCredential: List<VerifiableCredential>,  // Credentials
    val proof: Proof,                        // Cryptographic proof
    val expirationDate: String? = null      // Optional expiration
)
```

**Properties:**
- `id`: Unique presentation identifier
- `type`: Presentation types
- `holder`: Holder DID
- `verifiableCredential`: List of credentials in presentation
- `proof`: Cryptographic proof
- `expirationDate`: Optional expiration date

## DID Types

### DidResolutionResult

Result of DID resolution.

```kotlin
data class DidResolutionResult(
    val document: DidDocument?,              // Resolved DID document
    val metadata: DidResolutionMetadata      // Resolution metadata
)
```

**Properties:**
- `document`: Resolved DID document (null if not found)
- `metadata`: Resolution metadata (method, error, etc.)

### DidResolutionMetadata

Metadata about DID resolution.

```kotlin
data class DidResolutionMetadata(
    val method: String,                      // DID method used
    val error: String? = null                // Error message if resolution failed
)
```

### DidCreationOptions

Options for creating DIDs.

```kotlin
data class DidCreationOptions(
    val algorithm: KeyAlgorithm = KeyAlgorithm.ED25519,
    val purposes: List<KeyPurpose> = listOf(KeyPurpose.AUTHENTICATION, KeyPurpose.ASSERTION_METHOD),
    val additionalProperties: Map<String, Any> = emptyMap()
)
```

**Properties:**
- `algorithm`: Key algorithm (ED25519, SECP256K1, RSA)
- `purposes`: List of key purposes
- `additionalProperties`: Method-specific options

## Credential Types

### CredentialVerificationResult

Result of credential verification.

```kotlin
data class CredentialVerificationResult(
    val valid: Boolean,                      // Overall validity
    val proofValid: Boolean,                 // Proof signature is valid
    val issuerValid: Boolean,                // Issuer DID resolved successfully
    val notExpired: Boolean,                 // Credential has not expired
    val notRevoked: Boolean,                 // Credential is not revoked
    val errors: List<String>,               // List of error messages
    val warnings: List<String>             // List of warnings
)
```

**Properties:**
- `valid`: Overall validity (all checks passed)
- `proofValid`: Proof signature is valid
- `issuerValid`: Issuer DID resolved successfully
- `notExpired`: Credential has not expired
- `notRevoked`: Credential is not revoked
- `errors`: List of error messages if verification failed
- `warnings`: List of warnings (e.g., expiring soon)

### CredentialStatus

Revocation status information.

```kotlin
data class CredentialStatus(
    val id: String,                          // Status list ID
    val type: String,                       // Status list type
    val statusPurpose: String,              // Purpose (revocation, suspension)
    val statusListIndex: Int                // Index in status list
)
```

## Wallet Types

### CredentialFilter

Filter for querying credentials.

```kotlin
data class CredentialFilter(
    val issuer: String? = null,             // Filter by issuer DID
    val type: String? = null,               // Filter by credential type
    val expired: Boolean? = null            // Filter by expiration status
)
```

### CredentialCollection

Collection of credentials.

```kotlin
data class CredentialCollection(
    val id: String,                         // Collection ID
    val name: String,                       // Collection name
    val description: String? = null,        // Optional description
    val createdAt: Long? = null             // Creation timestamp
)
```

### CredentialMetadata

Metadata for a credential.

```kotlin
data class CredentialMetadata(
    val credentialId: String,               // Credential ID
    val tags: Set<String> = emptySet(),     // Tags
    val notes: String? = null,              // Optional notes
    val customMetadata: Map<String, Any> = emptyMap()  // Custom metadata
)
```

### WalletCapabilities

Capabilities supported by a wallet.

```kotlin
data class WalletCapabilities(
    val organization: Boolean = false,      // Supports organization (collections, tags)
    val presentation: Boolean = false,      // Supports presentation creation
    val lifecycle: Boolean = false,         // Supports lifecycle management
    val didManagement: Boolean = false,     // Supports DID management
    val keyManagement: Boolean = false      // Supports key management
)
```

### WalletStatistics

Statistics about a wallet.

```kotlin
data class WalletStatistics(
    val totalCredentials: Int,              // Total number of credentials
    val collectionsCount: Int,              // Number of collections
    val tagsCount: Int,                     // Number of unique tags
    val archivedCount: Int = 0              // Number of archived credentials
)
```

## Blockchain Types

### AnchorResult

Result of anchoring data to blockchain.

```kotlin
data class AnchorResult(
    val ref: AnchorRef,                     // Anchor reference
    val timestamp: String                   // ISO 8601 timestamp
)
```

**Properties:**
- `ref`: Anchor reference (chain ID, transaction hash, block number)
- `timestamp`: When data was anchored (ISO 8601)

### AnchorRef

Reference to anchored data on blockchain.

```kotlin
data class AnchorRef(
    val chainId: String,                    // Chain ID (CAIP-2 format)
    val txHash: String,                     // Transaction hash
    val blockNumber: Long? = null           // Block number (if available)
)
```

**Properties:**
- `chainId`: Chain identifier (e.g., "algorand:testnet")
- `txHash`: Transaction hash
- `blockNumber`: Block number where anchored (if available)

## Key Types

### KeyInfo

Information about a cryptographic key.

```kotlin
data class KeyInfo(
    val keyId: String,                      // Key identifier
    val algorithm: String,                  // Key algorithm
    val publicKey: ByteArray,               // Public key bytes
    val purposes: List<KeyPurpose>         // Key purposes
)
```

**Properties:**
- `keyId`: Key identifier
- `algorithm`: Key algorithm (Ed25519, Secp256k1, etc.)
- `publicKey`: Public key bytes
- `purposes`: Key purposes (AUTHENTICATION, ASSERTION_METHOD, etc.)

### KeyPurpose

Purpose of a cryptographic key.

```kotlin
enum class KeyPurpose {
    AUTHENTICATION,                         // Authentication
    ASSERTION_METHOD,                       // Assertion (signing)
    KEY_AGREEMENT,                         // Key agreement
    CAPABILITY_INVOCATION,                 // Capability invocation
    CAPABILITY_DELEGATION                   // Capability delegation
}
```

## Proof Types

### Proof

Cryptographic proof (signature).

```kotlin
data class Proof(
    val type: String,                       // Proof type (Ed25519Signature2020, etc.)
    val created: String,                    // Creation timestamp (ISO 8601)
    val verificationMethod: String,         // Verification method reference
    val proofPurpose: String,              // Proof purpose (assertionMethod, etc.)
    val proofValue: String                  // Proof value (signature)
)
```

**Properties:**
- `type`: Proof type (Ed25519Signature2020, JsonWebSignature2020, etc.)
- `created`: When proof was created (ISO 8601)
- `verificationMethod`: Verification method reference (key ID)
- `proofPurpose`: Proof purpose (assertionMethod, authentication, etc.)
- `proofValue`: Proof value (signature)

## Service Types

### Service

Service endpoint in DID document.

```kotlin
data class Service(
    val id: String,                         // Service ID
    val type: String,                       // Service type
    val serviceEndpoint: String             // Service endpoint URL
)
```

**Properties:**
- `id`: Service identifier
- `type`: Service type (LinkedDomains, DIDCommMessaging, etc.)
- `serviceEndpoint`: Service endpoint URL

## Related Documentation

- **[Core API](core-api.md)** - API methods that use these types
- **[Wallet API](wallet-api.md)** - Wallet-specific types
- **[Quick Reference](quick-reference.md)** - Quick API lookup

