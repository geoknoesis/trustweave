---
title: Proof Engine Implementation Guide
nav_order: 80
parent: Advanced
redirect_from:
  - /advanced/proof-engine-implementation-guide/

---

# Proof Engine Implementation Guide

This guide explains how to implement a custom `ProofEngine` for TrustWeave. Proof engines handle proof suite-specific operations for Verifiable Credentials.

## Overview

A **Proof Engine** is an implementation of the `ProofEngine` SPI interface that handles:

- **Proof Generation** - Creating cryptographic proofs during credential issuance
- **Proof Verification** - Verifying proofs during credential verification  
- **Presentation Creation** - Creating verifiable presentations (if supported)

The built-in proof engines shipped in `credential-api` are **VC-LD** (`VcLdProofEngine`) and **SD-JWT-VC** (`SdJwtProofEngine`). Additional engines are provided by separate plugin modules (e.g. `credentials/plugins/bbs/` for BBS-2023, `credentials/plugins/mdl/` for ISO 18013-5 mDoc).

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

`ProofSuiteId` is a closed enum in `org.trustweave.credential.format` whose current values are `VC_LD`, `VC_JWT`, `SD_JWT_VC`, `MDOC`, and `BBS_2023`. To add a new proof suite you must add an entry to that enum (the SPI does not accept arbitrary string identifiers):

```kotlin
enum class ProofSuiteId(val value: String) {
    // ... existing entries (VC_LD, VC_JWT, SD_JWT_VC, MDOC, BBS_2023)
    CUSTOM_SUITE("custom-suite")
}
```

> **Limitation:** `ProofSuiteId` is a closed enum, so the SPI cannot accept arbitrary string suite identifiers from out-of-tree code. If you need a new suite that is not in the enum, you must add an entry to `org.trustweave.credential.format.ProofSuiteId` and rebuild `credentials/credential-api`. For third-party engines this means either contributing the new enum value upstream (open a GitHub issue with the requested identifier and the W3C/IETF spec link), or vendoring a patched `credential-api`. There is no out-of-tree workaround for adding a brand-new proof suite today.

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
                credential = credential,
                reason = "Credential does not contain expected proof type"
            )

        // 2. Resolve issuer IRI (typically a DID, but per W3C VC may be any IRI)
        val issuerIri: Iri = credential.issuer.id

        // 3. Verify cryptographic proof
        val isValid = verifyProof(credential, proof, issuerIri)

        return if (isValid) {
            VerificationResult.Valid(
                credential = credential,
                issuerIri = issuerIri,
                subjectIri = credential.credentialSubject.id,
                issuedAt = credential.issuanceDate,
                expiresAt = credential.validUntil ?: credential.expirationDate
            )
        } else {
            VerificationResult.Invalid.InvalidProof(
                credential = credential,
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
        issuerIri: Iri
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
        // KMS contract is `sign(keyId, data, algorithm?)` and returns SignResult.
        return kms.sign(KeyId(keyId), data).signature
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
    
    // Check expiration if requested (validUntil for VC 2.0, expirationDate for VC 1.1)
    val effectiveExpiry = credential.validUntil ?: credential.expirationDate
    if (options.checkExpiration && effectiveExpiry != null) {
        val now = kotlinx.datetime.Clock.System.now()
        if (now > effectiveExpiry) {
            return VerificationResult.Invalid.Expired(
                credential = credential,
                expiredAt = effectiveExpiry
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

    // `ProofSuiteId` is a closed enum — use the closest built-in (here VC_JWT) or add a new entry.
    override val format = ProofSuiteId.VC_JWT
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
        val signature = kms.sign(KeyId(keyId), claims.toByteArray()).signature
        
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
                credential = credential,
                reason = "Expected JWT proof"
            )

        // Verify JWT signature
        val isValid = verifyJwtSignature(proof.jwt, credential.issuer)

        return if (isValid) {
            VerificationResult.Valid(
                credential = credential,
                issuerIri = credential.issuer.id,
                subjectIri = credential.credentialSubject.id,
                issuedAt = credential.issuanceDate,
                expiresAt = credential.validUntil ?: credential.expirationDate
            )
        } else {
            VerificationResult.Invalid.InvalidProof(
                credential = credential,
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
        // generateKey returns a sealed GenerateKeyResult — use getOrThrow() (or pattern match) to obtain the handle.
        val key = kms.generateKey(Algorithm.Ed25519).getOrThrow()

        val engine = CustomProofEngine(
            config = ProofEngineConfig(
                properties = mapOf("kms" to kms)
            )
        )
        
        val request = IssuanceRequest(
            format = ProofSuiteId.CUSTOM_SUITE,
            issuer = Issuer.from("did:key:issuer"),
            issuerKeyId = VerificationMethodId.parse("did:key:issuer#${key.id.value}"),
            credentialSubject = CredentialSubject.fromDid(
                did = Did("did:key:subject"),
                claims = mapOf("name" to JsonPrimitive("Alice"))
            ),
            type = listOf(CredentialType("VerifiableCredential"))
        )

        val credential = engine.issue(request)

        assertNotNull(credential.proof)
        assertEquals(ProofSuiteId.CUSTOM_SUITE, credential.proof!!.getFormatId())
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
// Quick path: use built-in VC-LD and SD-JWT-VC engines wired with a DID resolver.
val service = credentialService(didResolver = didResolver)

// To add a custom engine, instantiate it and pass an engines map.
// `createBuiltInEngines` and `DefaultCredentialService` are internal; if you need to
// fully replace the engine set you must construct via the public `credentialService(...)`
// factories in `org.trustweave.credential.CredentialServices`, or register your
// `ProofEngineProvider` via Java ServiceLoader so it is auto-discovered by plugin loaders.
```

## Related Documentation

- [SPI Documentation](spi.md) - Service Provider Interface details
- [Proofs and Proof Engines](../../core-concepts/proofs-and-proof-engines.md) - User-facing proof documentation (if available)
- [Key Management](../../core-concepts/key-management.md) - Key management for signing (if available)
- [Verifiable Credentials](../../core-concepts/verifiable-credentials.md) - VC data model (if available)








