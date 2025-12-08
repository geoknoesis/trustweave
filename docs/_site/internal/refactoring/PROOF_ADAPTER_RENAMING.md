# ProofAdapter Renaming Analysis

## Current Name: `ProofAdapter`

## Issues with "Adapter"

1. **Implementation Detail** - "Adapter" is a design pattern name, not a domain concept
2. **Not Domain-Precise** - Doesn't convey Web-of-Trust or cryptographic meaning
3. **Generic** - Could be adapting anything
4. **Not Intuitive** - Developers need to understand it's about proof formats

## What It Actually Does

The interface:
- Handles format-specific proof operations (VC-LD, VC-JWT, SD-JWT-VC)
- Issues credentials with cryptographic proofs
- Verifies credential proofs
- Creates presentations with proofs
- Manages format-specific proof generation and verification

## Domain Terminology

In W3C VC and cryptography:
- **Proof Format** - The format of the proof (VC-LD, VC-JWT, SD-JWT-VC)
- **Proof Suite** - The cryptographic suite (Ed25519Signature2020, JsonWebSignature2020)
- **Proof Handler** - Something that handles proof operations
- **Proof Processor** - Something that processes proofs

## Recommended Names

### Option 1: `ProofFormatHandler` ⭐ (Recommended)

**Pros:**
- ✅ Domain-precise - clearly about proof formats
- ✅ Describes role - "handles" proof format operations
- ✅ Web-of-Trust aligned terminology
- ✅ Clear and intuitive

**Cons:**
- ⚠️ Slightly longer name

**Usage:**
```kotlin
interface ProofFormatHandler {
    val format: CredentialFormatId
    suspend fun issue(request: IssuanceRequest): VerifiableCredential
    suspend fun verify(credential: VerifiableCredential): VerificationResult
}
```

### Option 2: `CredentialProofFormat`

**Pros:**
- ✅ Very explicit - credential proof format
- ✅ Domain-precise
- ✅ Clear purpose

**Cons:**
- ⚠️ Longer name
- ⚠️ Might conflict with `CredentialFormat` concept

### Option 3: `ProofFormatProcessor`

**Pros:**
- ✅ Clear - processes proof formats
- ✅ Domain-appropriate

**Cons:**
- ⚠️ "Processor" is generic

### Option 4: `ProofHandler`

**Pros:**
- ✅ Short and clear
- ✅ Describes role

**Cons:**
- ⚠️ Less specific - doesn't mention "format"

## Recommendation

**`ProofFormatHandler`** is the best choice because:
1. It's domain-precise (proof format is W3C VC terminology)
2. It clearly describes the role (handles format-specific operations)
3. It's intuitive and self-documenting
4. It aligns with Web-of-Trust terminology

## Related Names to Update

If renaming `ProofAdapter` → `ProofFormatHandler`:

1. `ProofAdapterRegistry` → `ProofFormatRegistry` or `ProofFormatHandlerRegistry`
2. `ProofAdapterProvider` → `ProofFormatHandlerProvider` or `ProofFormatProvider`
3. `ProofAdapterCapabilities` → `ProofFormatCapabilities`
4. `ProofAdapterConfig` → `ProofFormatConfig`
5. `ProofAdapters` (object) → `ProofFormatHandlers` or keep as utility object

## Migration Strategy

1. Create new interface `ProofFormatHandler`
2. Deprecate `ProofAdapter` with typealias
3. Update all internal references
4. Update documentation
5. Remove deprecated typealias in next major version

