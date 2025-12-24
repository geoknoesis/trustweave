# ProofAdapter Implementation Guide

## Overview

This guide provides patterns and examples for implementing `ProofAdapter` plugins. The SD-JWT-VC adapter serves as a reference implementation.

---

## Implementation Pattern

All `ProofAdapter` implementations follow this pattern:

### 1. **Issue Flow**
```
IssuanceRequest 
  → Convert to format-specific structure
  → Generate proof/signature
  → Convert back to CredentialEnvelope
```

### 2. **Verify Flow**
```
CredentialEnvelope
  → Extract proof from envelope.proof.data
  → Verify format-specific proof
  → Return VerificationResult
```

### 3. **Presentation Flow** (if supported)
```
CredentialEnvelope + PresentationRequest
  → Extract disclosed claims
  → Generate selective disclosure
  → Return presentation CredentialEnvelope
```

---

## Helper Utilities

### EnvelopeSerialization

Located in `credential-api/internal/util/EnvelopeSerialization.kt`:

- `claimsToJsonObject(claims: Claims): JsonObject` - Convert claims map to JSON
- `envelopeToJsonObject(envelope: CredentialEnvelope): JsonObject` - Convert envelope to JSON
- `createEnvelope(...)` - Create envelope from components
- `extractClaims(request: IssuanceRequest): Claims` - Extract claims from request

**Usage Example**:
```kotlin
import org.trustweave.credential.internal.util.EnvelopeSerialization

// Convert request to JSON for format-specific processing
val claimsJson = EnvelopeSerialization.claimsToJsonObject(request.claims)

// Create envelope after proof generation
val envelope = EnvelopeSerialization.createEnvelope(
    id = credentialId,
    issuer = request.issuer,
    subject = request.subject,
    type = request.type,
    claims = request.claims,
    issuedAt = request.issuedAt,
    validFrom = request.validFrom,
    validUntil = request.validUntil,
    status = request.status,
    evidence = request.evidence,
    proofFormat = CredentialFormat.SdJwtVc,
    proofData = proofJsonElement
)
```

---

## SD-JWT-VC Reference Implementation

See `credentials/plugins/proof/sdjwt/SdJwtProofAdapter.kt` for a working reference.

### Key Implementation Points:

1. **Issue Method**:
   - Build JWT claims from `IssuanceRequest`
   - Sign JWT using issuer's private key
   - Store JWT in `CredentialProof.data` as JSON object with `"jwt"` field

2. **Verify Method**:
   - Extract JWT from `envelope.proof.data.jsonObject["jwt"]`
   - Parse and verify JWT signature
   - Resolve issuer DID and get verification key
   - Return `VerificationResult.Valid` or `Invalid`

3. **Error Handling**:
   - Use specific `VerificationResult.Invalid` types
   - Provide clear error messages
   - Include format-specific error details when applicable

---

## Implementation Checklist

For each `ProofAdapter` implementation:

### ✅ Basic Structure
- [ ] Implement `ProofAdapter` interface
- [ ] Set correct `format`, `formatName`, `formatVersion`
- [ ] Define `capabilities` accurately

### ✅ Issue Method
- [ ] Validate `request.format` matches adapter format
- [ ] Convert `IssuanceRequest` to format-specific structure
- [ ] Generate proof/signature using format-specific library
- [ ] Convert back to `CredentialEnvelope` with proof in `proof.data`
- [ ] Handle errors gracefully

### ✅ Verify Method
- [ ] Validate `envelope.proof.format` matches adapter format
- [ ] Extract proof data from `envelope.proof.data`
- [ ] Parse format-specific proof structure
- [ ] Verify cryptographic proof
- [ ] Resolve issuer DID and verify key
- [ ] Return appropriate `VerificationResult`
- [ ] Handle all error cases

### ✅ Presentation Method (if supported)
- [ ] Check `capabilities.selectiveDisclosure`
- [ ] Extract `disclosedClaims` from `PresentationRequest`
- [ ] Generate selective disclosure structure
- [ ] Return presentation `CredentialEnvelope`

### ✅ Lifecycle Methods
- [ ] Implement `initialize()` for setup
- [ ] Implement `close()` for cleanup
- [ ] Implement `isReady()` check

### ✅ SPI Registration
- [ ] Create `ProofAdapterProvider` implementation
- [ ] Register in `META-INF/services/org.trustweave.credential.proof.spi.ProofAdapterProvider`
- [ ] Test auto-discovery

---

## Common Patterns

### Pattern 1: JWT-Based Formats (SD-JWT, VC-JWT)

```kotlin
override suspend fun issue(request: IssuanceRequest): CredentialEnvelope {
    // 1. Build JWT claims
    val claimsBuilder = JWTClaimsSet.Builder()
        .issuer(request.issuer.value)
        .subject(request.subject.value)
        // ... add claims
    
    // 2. Sign JWT
    val signedJWT = SignedJWT(header, claimsSet)
    signedJWT.sign(signer)
    
    // 3. Store in proof.data
    val proofData = buildJsonObject {
        put("jwt", signedJWT.serialize())
    }
    
    // 4. Create envelope
    return EnvelopeSerialization.createEnvelope(..., proofData)
}
```

### Pattern 2: JSON-LD Formats (VC-LD)

```kotlin
override suspend fun issue(request: IssuanceRequest): CredentialEnvelope {
    // 1. Build JSON-LD document
    val vcDocument = buildJsonObject {
        put("@context", listOf("https://www.w3.org/2018/credentials/v1"))
        put("type", request.type.map { it.value })
        // ... add claims
    }
    
    // 2. Canonicalize
    val canonical = canonicalizeDocument(vcDocument)
    
    // 3. Sign
    val signature = signDocument(canonical)
    
    // 4. Create proof object
    val proofData = buildJsonObject {
        put("document", vcDocument)
        put("proof", buildJsonObject {
            put("type", "Ed25519Signature2020")
            put("proofValue", signature)
            // ...
        })
    }
    
    return EnvelopeSerialization.createEnvelope(..., proofData)
}
```

### Pattern 3: Binary Formats (mDL, CBOR)

```kotlin
override suspend fun issue(request: IssuanceRequest): CredentialEnvelope {
    // 1. Build CBOR structure
    val cborBytes = buildCborDocument(request)
    
    // 2. Sign with COSE
    val coseSigned = signCose(cborBytes)
    
    // 3. Encode as base64 for JSON storage
    val proofData = buildJsonObject {
        put("cbor", Base64.getEncoder().encodeToString(coseSigned))
    }
    
    return EnvelopeSerialization.createEnvelope(..., proofData)
}
```

---

## Integration with DID Resolution

Most adapters need to resolve issuer DIDs to get verification keys:

```kotlin
private suspend fun getVerifier(issuerDid: Did): Verifier? {
    // TODO: Integrate with DidResolver
    // 1. Resolve DID Document
    // 2. Find verification method matching proof type
    // 3. Get public key
    // 4. Create verifier
    
    return null // Placeholder
}
```

**Note**: Full implementation requires integration with `DidResolver` from `did-core` module.

---

## Testing

Each plugin should include:

1. **Unit Tests**:
   - Test `issue()` with various request configurations
   - Test `verify()` with valid and invalid credentials
   - Test `derivePresentation()` (if supported)
   - Test error cases

2. **Integration Tests**:
   - Test with real DID resolution
   - Test with actual cryptographic keys
   - Test end-to-end issuance and verification

---

## Next Steps

1. **Complete SD-JWT-VC**: Finish selective disclosure implementation
2. **Implement VC-LD**: Add JSON-LD canonicalization and signing
3. **Implement AnonCreds**: Integrate with AnonCreds library
4. **Implement mDL**: Add COSE/CBOR support
5. **Implement X.509**: Add certificate chain verification
6. **Implement PassKey**: Add WebAuthn integration

---

## Resources

- **SD-JWT-VC Spec**: IETF draft-ietf-oauth-sd-jwt-vc
- **VC 2.0 Spec**: W3C Verifiable Credentials Data Model v2.0
- **AnonCreds**: Hyperledger AnonCreds specification
- **mDL Spec**: ISO/IEC 18013-5
- **WebAuthn**: W3C Web Authentication specification

