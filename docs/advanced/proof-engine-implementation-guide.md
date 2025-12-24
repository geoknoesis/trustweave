---
title: Proof Engine Implementation Guide
nav_order: 1
parent: Advanced
---

# Proof Engine Implementation Guide

This guide explains how to implement a custom `ProofEngine` for TrustWeave. Proof engines handle proof suite-specific operations for Verifiable Credentials.

## Overview

A **Proof Engine** is an implementation of the `ProofEngine` SPI interface that handles:

- **Proof Generation** - Creating cryptographic proofs during credential issuance
- **Proof Verification** - Verifying proofs during credential verification  
- **Presentation Creation** - Creating verifiable presentations (if supported)

All built-in proof suites (VC-LD, SD-JWT-VC, AnonCreds) are implemented as proof engines.

## When to Implement a Proof Engine

Implement a custom proof engine when you need to:

1. **Support a new proof suite** not currently supported by TrustWeave
2. **Customize proof generation** for specific use cases
3. **Integrate with external proof libraries** or services

**Note:** For most use cases, the built-in proof engines are sufficient. Only implement a custom engine if you have specific requirements.

## ProofEngine Interface

```kotlin
interface ProofEngine {
    /** Proof suite identifier this engine handles */
    val format: ProofSuiteId
    
    /** Human-readable proof suite name */
    val formatName: String
    
    /** Proof suite version supported */
    val formatVersion: String
    
    /** Capabilities this engine supports */
    val capabilities: ProofEngineCapabilities
    
    /** Issue a Verifiable Credential with proof */
    suspend fun issue(request: IssuanceRequest): VerifiableCredential
    
    /** Verify a Verifiable Credential's proof */
    suspend fun verify(
        credential: VerifiableCredential,
        options: VerificationOptions
    ): VerificationResult
    
    /** Create a Verifiable Presentation (optional) */
    suspend fun createPresentation(
        credentials: List<VerifiableCredential>,
        request: PresentationRequest
    ): VerifiablePresentation
}
```

## Implementation Steps

### 1. Define Your Proof Suite

First, add your proof suite to the `ProofSuiteId` enum (if extending the core library) or use a custom identifier:

```kotlin
enum class ProofSuiteId(val value: String) {
    // ... existing suites
    CUSTOM_SUITE("custom-suite")
}
```

### 2. Implement the ProofEngine Interface

```kotlin
import org.trustweave.credential.spi.proof.*
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.model.vc.*
import org.trustweave.credential.requests.*
import org.trustweave.credential.results.*

class CustomProofEngine(
    private val config: ProofEngineConfig = ProofEngineConfig()
) : ProofEngine {
    
    override val format = ProofSuiteId.CUSTOM_SUITE
    override val formatName = "Custom Proof Suite"
    override val formatVersion = "1.0"
    
    override val capabilities = ProofEngineCapabilities(
        selectiveDisclosure = false,
        zeroKnowledge = false,
        revocation = true,
        presentation = true,
        predicates = false
    )
    
    override suspend fun issue(request: IssuanceRequest): VerifiableCredential {
        // 1. Validate request format matches
        require(request.format == format) {
            "Request format ${request.format.value} does not match engine format ${format.value}"
        }
        
        // 2. Extract signing key from config or request
        val keyId = request.issuerKeyId?.value
            ?: throw IllegalArgumentException("issuerKeyId is required")
        
        // 3. Build the credential document (without proof)
        val credentialDocument = buildCredentialDocument(request)
        
        // 4. Generate cryptographic proof
        val proof = generateProof(credentialDocument, keyId, request.proofOptions)
        
        // 5. Build and return VerifiableCredential
        return VerifiableCredential(
            context = request.context ?: defaultContext,
            id = request.id,
            type = request.type,
            issuer = request.issuer,
            issuanceDate = request.issuedAt,
            credentialSubject = request.credentialSubject,
            expirationDate = request.validUntil,
            credentialStatus = request.credentialStatus,
            credentialSchema = request.credentialSchema,
            evidence = request.evidence,
            proof = proof
        )
    }
    
    override suspend fun verify(
        credential: VerifiableCredential,
        options: VerificationOptions
    ): VerificationResult {
        // 1. Extract proof from credential
        val proof = credential.proof as? CustomProof
            ?: return VerificationResult.Invalid.InvalidProof(
                format = format,
                reason = "Credential does not contain expected proof type"
            )
        
        // 2. Resolve issuer DID if needed
        val issuerDid = when (val issuer = credential.issuer) {
            is Issuer.IriIssuer -> issuer.id.value
            is Issuer.ObjectIssuer -> issuer.id.value
        }
        
        // 3. Verify cryptographic proof
        val isValid = verifyProof(credential, proof, issuerDid)
        
        return if (isValid) {
            VerificationResult.Valid(
                credential = credential,
                issuerDid = Did(issuerDid),
                subjectDid = extractSubjectDid(credential),
                issuedAt = credential.issuanceDate,
                expiresAt = credential.expirationDate
            )
        } else {
            VerificationResult.Invalid.InvalidProof(
                format = format,
                reason = "Cryptographic proof verification failed"
            )
        }
    }
    
    override suspend fun createPresentation(
        credentials: List<VerifiableCredential>,
        request: PresentationRequest
    ): VerifiablePresentation {
        require(capabilities.presentation) {
            "Proof suite ${format.value} does not support presentations"
        }
        
        // Implementation for creating presentations
        // ...
    }
    
    // Helper methods
    private fun buildCredentialDocument(request: IssuanceRequest): JsonObject {
        // Build credential document for signing
    }
    
    private suspend fun generateProof(
        document: JsonObject,
        keyId: String,
        proofOptions: ProofOptions?
    ): CredentialProof {
        // Generate cryptographic proof
    }
    
    private suspend fun verifyProof(
        credential: VerifiableCredential,
        proof: CustomProof,
        issuerDid: String
    ): Boolean {
        // Verify cryptographic proof
    }
}
```

### 3. Implement ProofEngineProvider

Create a provider for your proof engine:

```kotlin
import org.trustweave.credential.spi.proof.*

class CustomProofEngineProvider : ProofEngineProvider {
    
    override val name = "custom-suite"
    
    override val supportedFormatIds = listOf(ProofSuiteId.CUSTOM_SUITE)
    
    override fun create(options: Map<String, Any?>): ProofEngine? {
        return try {
            val config = ProofEngineConfig(properties = options)
            CustomProofEngine(config)
        } catch (e: Exception) {
            null
        }
    }
}
```

### 4. Register Your Provider

For ServiceLoader-based discovery, create:

**`META-INF/services/org.trustweave.credential.spi.proof.ProofEngineProvider`**

```
com.example.CustomProofEngineProvider
```

**Note:** TrustWeave's built-in engines are directly instantiated, not discovered via ServiceLoader. For custom engines, you can either:

1. Use ServiceLoader discovery (for plugins)
2. Manually register engines when creating `CredentialService`

## Key Implementation Details

### Accessing Key Management Service

Proof engines receive KMS access through `ProofEngineConfig`:

```kotlin
class CustomProofEngine(private val config: ProofEngineConfig) {
    
    private fun getKms(): KeyManagementService? {
        return config.properties["kms"] as? KeyManagementService
    }
    
    private suspend fun sign(data: ByteArray, keyId: String): ByteArray {
        val kms = getKms() ?: throw IllegalStateException("KMS not configured")
        return kms.sign(data, KeyId(keyId))
    }
}
```

### Using Proof Options

Access proof options from the issuance request:

```kotlin
override suspend fun issue(request: IssuanceRequest): VerifiableCredential {
    val proofOptions = request.proofOptions
    
    // Get proof purpose
    val purpose = proofOptions?.purpose?.standardValue ?: "assertionMethod"
    
    // Get challenge (for presentations)
    val challenge = proofOptions?.challenge
    
    // Get domain binding
    val domain = proofOptions?.domain
    
    // Get proof suite-specific options
    val proofType = proofOptions?.additionalOptions?.get("proofType") as? String
        ?: "default-proof-type"
    
    // Use these in proof generation
}
```

### Handling Verification Options

```kotlin
override suspend fun verify(
    credential: VerifiableCredential,
    options: VerificationOptions
): VerificationResult {
    // Check revocation if requested
    if (options.checkRevocation && credential.credentialStatus != null) {
        // Verify revocation status
    }
    
    // Check expiration if requested
    if (options.checkExpiration && credential.expirationDate != null) {
        val now = Instant.now()
        if (now.isAfter(credential.expirationDate)) {
            return VerificationResult.Invalid.Expired(
                expiredAt = credential.expirationDate
            )
        }
    }
    
    // Verify proof
    // ...
}
```

## Example: Simple JWT-Based Proof Engine

Here's a simplified example of a JWT-based proof engine:

```kotlin
class SimpleJwtProofEngine(
    private val config: ProofEngineConfig = ProofEngineConfig()
) : ProofEngine {
    
    override val format = ProofSuiteId("simple-jwt")
    override val formatName = "Simple JWT"
    override val formatVersion = "1.0"
    
    override val capabilities = ProofEngineCapabilities(
        selectiveDisclosure = false,
        zeroKnowledge = false,
        revocation = true,
        presentation = false,
        predicates = false
    )
    
    override suspend fun issue(request: IssuanceRequest): VerifiableCredential {
        require(request.format == format)
        
        val keyId = request.issuerKeyId?.value
            ?: throw IllegalArgumentException("issuerKeyId required")
        
        // Build JWT claims
        val claims = buildJwtClaims(request)
        
        // Sign JWT
        val kms = getKms() ?: throw IllegalStateException("KMS not configured")
        val signature = kms.sign(claims.toByteArray(), KeyId(keyId))
        
        // Create proof
        val proof = CredentialProof.JwtProof(
            jwt = "$claims.$signature"
        )
        
        // Build credential
        return VerifiableCredential(
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            type = request.type,
            issuer = request.issuer,
            issuanceDate = request.issuedAt,
            credentialSubject = request.credentialSubject,
            expirationDate = request.validUntil,
            proof = proof
        )
    }
    
    override suspend fun verify(
        credential: VerifiableCredential,
        options: VerificationOptions
    ): VerificationResult {
        val proof = credential.proof as? CredentialProof.JwtProof
            ?: return VerificationResult.Invalid.InvalidProof(
                format = format,
                reason = "Expected JWT proof"
            )
        
        // Verify JWT signature
        val isValid = verifyJwtSignature(proof.jwt, credential.issuer)
        
        return if (isValid) {
            VerificationResult.Valid(
                credential = credential,
                issuerDid = extractIssuerDid(credential),
                subjectDid = extractSubjectDid(credential),
                issuedAt = credential.issuanceDate,
                expiresAt = credential.expirationDate
            )
        } else {
            VerificationResult.Invalid.InvalidProof(
                format = format,
                reason = "JWT signature verification failed"
            )
        }
    }
    
    private fun getKms(): KeyManagementService? {
        return config.properties["kms"] as? KeyManagementService
    }
    
    private fun buildJwtClaims(request: IssuanceRequest): String {
        // Build JWT claims from request
    }
    
    private suspend fun verifyJwtSignature(jwt: String, issuer: Issuer): Boolean {
        // Verify JWT signature
    }
}
```

## Testing Your Proof Engine

Create unit tests for your proof engine:

```kotlin
class CustomProofEngineTest {
    
    @Test
    fun `test issue credential`() = runTest {
        val kms = InMemoryKeyManagementService()
        val key = kms.generateKey(Algorithm.Ed25519)
        
        val engine = CustomProofEngine(
            config = ProofEngineConfig(
                properties = mapOf("kms" to kms)
            )
        )
        
        val request = IssuanceRequest(
            format = ProofSuiteId.CUSTOM_SUITE,
            issuer = Issuer.from("did:key:issuer"),
            issuerKeyId = VerificationMethodId("did:key:issuer#${key.id.value}"),
            credentialSubject = CredentialSubject.fromDid(
                did = Did("did:key:subject"),
                claims = mapOf("name" to JsonPrimitive("Alice"))
            ),
            type = listOf(CredentialType("VerifiableCredential"))
        )
        
        val credential = engine.issue(request)
        
        assertNotNull(credential.proof)
        assertEquals(ProofSuiteId.CUSTOM_SUITE, credential.proof.getFormatId())
    }
    
    @Test
    fun `test verify credential`() = runTest {
        // Test verification
    }
}
```

## Best Practices

1. **Validate Inputs Early** - Check request format matches engine format
2. **Handle Missing Keys** - Provide clear error messages when keys are missing
3. **Support Proof Options** - Honor `ProofOptions` for purpose, challenge, domain
4. **Implement Capabilities Correctly** - Only claim capabilities you actually support
5. **Use KMS for Signing** - Always use KeyManagementService, never sign directly
6. **Handle Errors Gracefully** - Return appropriate `VerificationResult` types
7. **Document Your Suite** - Clearly document proof suite requirements and limitations

## Integration with CredentialService

Once implemented, your proof engine can be used with `CredentialService`:

```kotlin
// Manual registration
val customEngine = CustomProofEngine(config)
val engines = mapOf(
    ProofSuiteId.CUSTOM_SUITE to customEngine
)

val service = DefaultCredentialService(
    engines = engines + createBuiltInEngines(),
    didResolver = didResolver
)

// Or via provider discovery
val service = credentialService(
    didResolver = didResolver,
    // Custom engines discovered via ServiceLoader
)
```

## Related Documentation

- [Proofs and Proof Engines](../core-concepts/proofs-and-proof-engines.md) - User-facing proof documentation
- [SPI Documentation](./spi.md) - Service Provider Interface details
- [Key Management](../core-concepts/key-management.md) - Key management for signing
- [Verifiable Credentials](../core-concepts/verifiable-credentials.md) - VC data model








