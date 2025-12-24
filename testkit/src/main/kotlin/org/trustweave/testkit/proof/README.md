# Proof Engine Test Helpers

Convenience methods for testing proof engines in TrustWeave.

## Overview

The `ProofEngineTestHelpers` class provides a fluent API for creating test data needed to test proof engine implementations. It integrates with `TrustWeaveTestFixture` to provide realistic test scenarios.

## Usage

### Basic Setup

```kotlin
val fixture = TrustWeaveTestFixture.minimal()
val helpers = fixture.proofEngineHelpers()
```

### Creating Issuance Requests

```kotlin
// Simple request
val request = helpers.createIssuanceRequest(
    format = CredentialFormats.VC_LD,
    credentialType = "PersonCredential"
)

// With claims
val request = helpers.createIssuanceRequest(
    format = CredentialFormats.SD_JWT_VC,
    credentialType = "PersonCredential",
    claims = mapOf(
        "name" to "John Doe",
        "email" to "john@example.com",
        "age" to 30
    )
)

// With proof options
val request = helpers.createIssuanceRequest(
    format = CredentialFormats.VC_LD,
    credentialType = "PersonCredential",
    proofOptions = helpers.createIssuanceProofOptions()
)
```

### Creating Test Credentials

```kotlin
// Basic credential
val credential = helpers.createTestCredential(
    format = CredentialFormats.VC_LD,
    credentialType = "PersonCredential"
)

// With claims
val credential = helpers.createTestCredential(
    format = CredentialFormats.SD_JWT_VC,
    credentialType = "PersonCredential",
    claims = mapOf("name" to "Jane Doe", "age" to 25)
)
```

### Testing Edge Cases

```kotlin
// Expired credential
val expired = helpers.createExpiredCredential(
    format = CredentialFormats.VC_LD,
    expiredBy = Duration.ofHours(1)
)

// Credential without proof
val noProof = helpers.createCredentialWithoutProof(
    format = CredentialFormats.VC_LD
)

// Credential with invalid proof
val invalidProof = helpers.createCredentialWithInvalidProof(
    format = CredentialFormats.VC_LD
)
```

### Creating Verification Options

```kotlin
// Default options
val options = helpers.createVerificationOptions()

// Custom options
val options = helpers.createVerificationOptions(
    checkRevocation = true,
    checkExpiration = true,
    validateSchema = false
)
```

### Creating Presentation Requests

```kotlin
// Basic presentation request
val request = helpers.createPresentationRequest()

// With selective disclosure
val request = helpers.createPresentationRequest(
    disclosedClaims = setOf("name", "email"),
    proofOptions = helpers.createPresentationProofOptions(
        challenge = "challenge-123",
        domain = "example.com"
    )
)
```

### Creating Proof Options

```kotlin
// For issuance
val options = helpers.createIssuanceProofOptions()

// For authentication
val options = helpers.createAuthenticationProofOptions(
    challenge = "nonce-123",
    domain = "example.com"
)

// For presentation
val options = helpers.createPresentationProofOptions(
    challenge = "nonce-123",
    domain = "example.com"
)
```

## Standalone Usage (Without Fixture)

When you don't need DIDs or other fixture-dependent resources, use `ProofEngineTestData`:

```kotlin
// Create minimal issuance request
val request = ProofEngineTestData.createMinimalIssuanceRequest(
    format = CredentialFormats.VC_LD,
    credentialType = "TestCredential",
    claims = mapOf("name" to "Test")
)

// Create minimal credential
val credential = ProofEngineTestData.createMinimalCredential(
    format = CredentialFormats.VC_LD
)

// Default options
val verificationOptions = ProofEngineTestData.defaultVerificationOptions()
val presentationRequest = ProofEngineTestData.defaultPresentationRequest()
```

## KMS Integration

**Yes, the testkit includes a KMS for testing!**

The testkit provides `InMemoryKeyManagementService` which is automatically available through `TrustWeaveTestFixture`. The proof engines can be configured to use this KMS for actual signing during tests.

### Using KMS with Proof Engines

```kotlin
val fixture = TrustWeaveTestFixture.minimal()
val helpers = fixture.proofEngineHelpers()

// Get the KMS
val kms = helpers.getKms()

// Create a key for signing
val keyId = helpers.createTestKey(Algorithm.Ed25519)

// Create proof engine config with KMS
val config = helpers.createTestableProofEngineConfig()

// Create testable proof engines (with KMS integration)
val vcLdEngine = helpers.createTestableVcLdEngine()
val sdJwtEngine = helpers.createTestableSdJwtEngine()
val anonCredsEngine = helpers.createTestableAnonCredsEngine()

// Or create by format
val engine = helpers.createTestableEngine(CredentialFormats.VC_LD)
```

### Creating Keys for Testing

```kotlin
// Create Ed25519 key (default)
val keyId = helpers.createTestKey()

// Create secp256k1 key
val keyId = helpers.createTestKey(Algorithm.Secp256k1)

// Use in issuance request
val request = helpers.createIssuanceRequest(
    format = CredentialFormats.VC_LD,
    issuerKeyId = VerificationMethodId("${did.value}#${keyId.value}")
)
```

**Note:** The proof engines currently have placeholder implementations for signing. To fully test signing, the proof engines need to be updated to use the KMS from `ProofEngineConfig.properties["kms"]`. The testkit provides the infrastructure, but the engines need to be enhanced to use it.

## Available Methods

### ProofEngineTestHelpers

**KMS Integration:**
- `getKms()` - Get the KMS from fixture
- `createTestKey()` - Create a test key in KMS
- `createProofEngineConfigWithKms()` - Create config with KMS
- `createTestableProofEngineConfig()` - Create config with KMS and signer
- `createTestableVcLdEngine()` - Create VC-LD engine with KMS
- `createTestableSdJwtEngine()` - Create SD-JWT engine with KMS
- `createTestableAnonCredsEngine()` - Create AnonCreds engine with KMS
- `createTestableEngine()` - Create engine by format with KMS

**Test Data Creation:**
- `createIssuanceRequest()` - Create test IssuanceRequest (with auto key creation)
- `createTestCredential()` - Create test VerifiableCredential
- `createTestIssuer()` - Create test Issuer
- `createTestSubject()` - Create test CredentialSubject
- `createVerificationOptions()` - Create test VerificationOptions
- `createPresentationRequest()` - Create test PresentationRequest
- `createIssuanceProofOptions()` - Create proof options for issuance
- `createAuthenticationProofOptions()` - Create proof options for authentication
- `createPresentationProofOptions()` - Create proof options for presentation
- `createTestDid()` - Create test DID
- `createTestVerificationMethodId()` - Create test VerificationMethodId

**Edge Case Helpers:**
- `createExpiredCredential()` - Create expired credential for testing
- `createExpiringSoonCredential()` - Create credential expiring soon
- `createCredentialWithoutProof()` - Create credential without proof
- `createCredentialWithInvalidProof()` - Create credential with invalid proof

### ProofEngineTestData (Standalone)

- `createMinimalIssuanceRequest()` - Create minimal IssuanceRequest
- `createMinimalCredential()` - Create minimal VerifiableCredential
- `defaultVerificationOptions()` - Default VerificationOptions
- `defaultPresentationRequest()` - Default PresentationRequest

## Examples

See `ProofEngineTestHelpersExample.kt` for complete usage examples.

