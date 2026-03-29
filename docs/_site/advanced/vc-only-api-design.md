# VC-Only API Design - Aligning with W3C Verifiable Credentials

## Executive Summary

This document outlines the design for refactoring TrustWeave's credential API to use **only DID and VC standards**, aligning the credential model directly with the **W3C Verifiable Credentials Data Model**.

**Key Decision:** The framework will focus exclusively on:
- **IRIs** (Internationalized Resource Identifiers) - for entity identification, leveraging the common `Iri` base class
  - **DIDs** (Decentralized Identifiers) - primary identifier type, extends `Iri`
  - **URIs/URLs** - supported via `Iri` base class (e.g., `https://example.com/users/123`)
  - **URNs** - supported via `Iri` base class (e.g., `urn:uuid:...`)
- **VCs** (Verifiable Credentials) - W3C standard credentials

This simplifies the architecture, removes format-agnostic abstractions, and aligns the API directly with W3C VC Data Model while leveraging common identifier types for maximum flexibility. Per W3C VC spec, `credentialSubject.id` can be any IRI (not just a DID).

---

## Current State Analysis

### Current Architecture

The current credential API is **format-agnostic** and supports multiple credential ecosystems:
- W3C Verifiable Credentials (VC-LD, VC-JWT)
- SD-JWT-VC
- AnonCreds (non-VC standard)
- mDL/mdoc (ISO standard, not VC)
- X.509/PKI (non-VC standard)
- PassKeys/WebAuth (non-VC standard)

### Current Credential Model

```kotlin
data class Credential(
    val id: CredentialId?,
    val issuer: IssuerId,
    val subject: SubjectId,  // Generic subject
    val type: List<CredentialType>,
    val claims: Claims,  // Generic claims map
    val issuedAt: Instant,
    val validFrom: Instant?,
    val validUntil: Instant?,
    val status: CredentialStatus?,
    val evidence: List<Evidence>?,
    val proof: CredentialProof  // Format-agnostic proof
)
```

**Issues:**
1. Generic `subject: SubjectId` - not aligned with VC's `credentialSubject`
2. Generic `claims: Claims` - not aligned with VC's `credentialSubject` structure
3. Format-agnostic abstractions that don't match VC model
4. Missing VC-specific fields (e.g., `@context`, `credentialSchema`, `refreshService`, `termsOfUse`)

---

## Target State: W3C VC-Aligned API

### W3C Verifiable Credentials Data Model Structure

```json
{
  "@context": ["https://www.w3.org/2018/credentials/v1"],
  "id": "http://example.edu/credentials/3732",
  "type": ["VerifiableCredential", "UniversityDegreeCredential"],
  "issuer": {
    "id": "https://example.edu/issuers/565049",
    "name": "Example University"
  },
  "issuanceDate": "2010-01-01T00:00:00Z",
  "expirationDate": "2020-01-01T00:00:00Z",
  "credentialSubject": {
    "id": "did:example:ebfeb1f712ebc6f1c276e12ec21",
    "degree": {
      "type": "BachelorDegree",
      "name": "Bachelor of Science and Arts"
    }
  },
  "credentialStatus": {
    "id": "https://example.edu/status/24",
    "type": "CredentialStatusList2017"
  },
  "credentialSchema": {
    "id": "https://example.org/examples/degree.json",
    "type": "JsonSchemaValidator2018"
  },
  "proof": { ... }
}
```

### New VC-Aligned Credential Model

```kotlin
/**
 * Verifiable Credential as defined by W3C Verifiable Credentials Data Model.
 * 
 * Aligned with W3C VC 2.0 Data Model.
 * Supports VC-LD (Linked Data Proofs) and VC-JWT (JWT) proof formats.
 */
@Serializable
data class VerifiableCredential(
    // Required VC fields
    @SerialName("@context")
    val context: List<String> = listOf("https://www.w3.org/2018/credentials/v1"),
    
    val id: CredentialId? = null,
    
    val type: List<CredentialType>,  // Must include "VerifiableCredential"
    
    val issuer: Issuer,  // VC issuer (IRI - typically DID, but can be URI/URN - or object with id/name)
    
    @SerialName("issuanceDate")
    val issuanceDate: Instant,
    
    // Optional VC fields
    @SerialName("expirationDate")
    val expirationDate: Instant? = null,
    
    @SerialName("credentialSubject")
    val credentialSubject: CredentialSubject,  // VC subject (IRI id + claims - typically DID but can be URI/URN)
    
    @SerialName("credentialStatus")
    val credentialStatus: CredentialStatus? = null,
    
    @SerialName("credentialSchema")
    val credentialSchema: CredentialSchema? = null,
    
    val evidence: List<Evidence>? = null,
    
    @SerialName("termsOfUse")
    val termsOfUse: List<TermsOfUse>? = null,
    
    @SerialName("refreshService")
    val refreshService: RefreshService? = null,
    
    // Proof (format-specific: VC-LD, VC-JWT, or SD-JWT-VC)
    val proof: CredentialProof? = null  // Optional in data model, required when verified
)
```

### VC-Specific Types

```kotlin
/**
 * VC Issuer - can be an IRI string (DID, URI, etc.) or object with id and optional name.
 * 
 * Per W3C VC Data Model, issuer can be any IRI (typically a DID, but can be URI/URN).
 * Leverages the common Iri base class for identifier support.
 */
@Serializable
sealed class Issuer {
    @Serializable
    data class IriIssuer(val id: Iri) : Issuer() {
        /**
         * Check if issuer is a DID.
         */
        val isDid: Boolean get() = id.isDid
    }
    
    @Serializable
    data class ObjectIssuer(
        val id: Iri,  // Can be DID, URI, URN, etc.
        val name: String? = null,
        val additionalProperties: Map<String, JsonElement> = emptyMap()
    ) : Issuer() {
        /**
         * Check if issuer is a DID.
         */
        val isDid: Boolean get() = id.isDid
    }
    
    companion object {
        /**
         * Create issuer from IRI string (DID, URI, URN, etc.).
         */
        fun from(iri: String): Issuer = IriIssuer(Iri(iri))
        
        /**
         * Create issuer from IRI.
         */
        fun from(iri: Iri): Issuer = IriIssuer(iri)
        
        /**
         * Create issuer from DID (convenience method).
         */
        fun fromDid(did: Did): Issuer = IriIssuer(did)  // Did extends Iri
    }
}

/**
 * VC Credential Subject - contains an IRI id (DID, URI, URN, etc.) and claims.
 * 
 * Per W3C VC Data Model, credentialSubject.id can be any IRI, not just a DID.
 * Leverages the common Iri base class for identifier support.
 * 
 * **Examples:**
 * ```kotlin
 * // DID subject (most common)
 * val did = Did("did:key:z6Mk...")
 * val subject = CredentialSubject(id = did, claims = mapOf(...))
 * 
 * // URI subject
 * val uri = Iri("https://example.com/users/123")
 * val subject = CredentialSubject(id = uri, claims = mapOf(...))
 * 
 * // URN subject
 * val urn = Iri("urn:uuid:f81d4fae-7dec-11d0-a765-00a0c91e6bf6")
 * val subject = CredentialSubject(id = urn, claims = mapOf(...))
 * ```
 */
@Serializable
data class CredentialSubject(
    val id: Iri,  // Subject IRI (DID, URI, URN, etc.) - required per VC spec
    val claims: Map<String, JsonElement> = emptyMap()  // Additional claims
) {
    /**
     * Check if the subject ID is a DID.
     */
    val isDid: Boolean
        get() = id.isDid
    
    /**
     * Check if the subject ID is an HTTP/HTTPS URL.
     * 
     * **Note:** All URLs are URIs, but not all URIs are URLs.
     * DIDs and URNs are URIs but not URLs.
     */
    val isHttpUrl: Boolean
        get() = id.isHttpUrl
    
    /**
     * Check if the subject ID is a URN.
     */
    val isUrn: Boolean
        get() = id.isUrn
    
    /**
     * Convenience accessor for claims.
     */
    operator fun get(key: String): JsonElement? = claims[key]
    
    companion object {
        /**
         * Create CredentialSubject from DID (convenience method).
         */
        fun fromDid(did: Did, claims: Map<String, JsonElement> = emptyMap()): CredentialSubject {
            return CredentialSubject(id = did, claims = claims)  // Did extends Iri
        }
        
        /**
         * Create CredentialSubject from IRI string.
         */
        fun fromIri(iri: String, claims: Map<String, JsonElement> = emptyMap()): CredentialSubject {
            return CredentialSubject(id = Iri(iri), claims = claims)
        }
    }
}

/**
 * VC Proof - format-specific (VC-LD, VC-JWT, SD-JWT-VC).
 */
@Serializable
sealed class CredentialProof {
    /**
     * VC-LD (Linked Data Proofs) - JSON-LD proof structure.
     */
    @Serializable
    data class LinkedDataProof(
        val type: String,  // e.g., "Ed25519Signature2020"
        val created: Instant,
        val verificationMethod: String,  // DID URL
        val proofPurpose: String,  // e.g., "assertionMethod"
        val proofValue: String,  // Base58/Base64 encoded signature
        val additionalProperties: Map<String, JsonElement> = emptyMap()
    ) : CredentialProof()
    
    /**
     * VC-JWT - JWT compact serialization.
     */
    @Serializable
    data class JwtProof(
        val jwt: String  // Compact JWT string
    ) : CredentialProof()
    
    /**
     * SD-JWT-VC - Selective Disclosure JWT VC.
     */
    @Serializable
    data class SdJwtVcProof(
        val sdJwtVc: String,  // SD-JWT-VC compact format
        val disclosures: List<String>? = null  // Optional disclosures
    ) : CredentialProof()
}
```

---

## Changes Required

### 1. Refactor Credential Model

**File:** `credentials/credential-api/src/main/kotlin/org.trustweave/credential/model/`

- Remove: Generic `Credential` class
- Add: `VerifiableCredential` aligned with W3C VC Data Model
- Add: VC-specific types (`Issuer`, `CredentialSubject`, `CredentialProof`)
- Add: VC extensions (`CredentialSchema`, `RefreshService`, `TermsOfUse`)

### 2. Simplify Proof Adapters

**File:** `credentials/credential-api/src/main/kotlin/org.trustweave/credential/spi/proof/`

- Remove: Format-agnostic `ProofAdapter` interface
- Add: VC-specific `VcProofAdapter` interface
- Keep only VC proof formats:
  - `VcLdProofAdapter` (Linked Data Proofs)
  - `VcJwtProofAdapter` (JWT format)
  - `SdJwtVcProofAdapter` (SD-JWT-VC)

### 3. Remove Non-VC Format Support

**Files to remove/modify:**
- `plugins/proof/anoncreds/` - Remove (not VC standard)
- `plugins/proof/mdl/` - Remove (not VC standard)
- `plugins/proof/x509/` - Remove (not VC standard)
- `plugins/proof/passkey/` - Remove (not VC standard)

**Files to keep:**
- `plugins/proof/vcld/` - Keep (VC standard)
- `plugins/proof/vcjwt/` - Keep (VC standard)
- `plugins/proof/sdjwt/` - Keep (SD-JWT-VC is VC standard)

### 4. Update CredentialService API

**File:** `credentials/credential-api/src/main/kotlin/org.trustweave/credential/CredentialService.kt`

```kotlin
/**
 * Credential service for W3C Verifiable Credentials.
 * 
 * Focused on VC standards only (VC-LD, VC-JWT, SD-JWT-VC).
 */
interface CredentialService {
    /**
     * Issue a Verifiable Credential.
     */
    suspend fun issue(request: IssuanceRequest): VerifiableCredential
    
    /**
     * Verify a Verifiable Credential.
     */
    suspend fun verify(
        credential: VerifiableCredential,
        options: VerificationOptions
    ): VerificationResult
    
    /**
     * Create a Verifiable Presentation from credentials.
     */
    suspend fun createPresentation(
        credentials: List<VerifiableCredential>,
        request: PresentationRequest
    ): VerifiablePresentation
}
```

### 5. Update Identifiers

**File:** `credentials/credential-api/src/main/kotlin/org.trustweave/credential/identifiers/`

- Keep: `CredentialId`, `IssuerId`, `Iri` (base class from common module), `Did` (from did module, extends `Iri`)
- Use: `Iri` in `CredentialSubject.id` to support DID, URI, URN, etc. (per W3C VC spec)
- Use: `Iri` in `Issuer.id` to support DID, URI, URN, etc. (per W3C VC spec)
- Remove: Generic `SubjectId` sealed class (replaced with `Iri` in `CredentialSubject`)
- Add: VC-specific identifier helpers leveraging common `Iri` base

---

## Identifier Design: Leveraging Common IRI Base

### Why IRI (not just DID)?

Per W3C VC Data Model specification:
- `credentialSubject.id` can be **any IRI** (DID, URI, URN, etc.)
- `issuer` can be **any IRI** (typically a DID, but can be URI/URN)
- This enables credentials about non-DID entities (e.g., web resources, UUIDs)

### Using Common Identifier Base

TrustWeave's identifier hierarchy:
```
Iri (base class in common module)
  ├── Did (extends Iri) - from did module
  ├── CredentialId (extends Iri)
  ├── IssuerId (extends Iri)
  └── ... other IRI-based identifiers
```

**Benefits:**
- **Polymorphism**: `Did` instances can be used wherever `Iri` is expected
- **Type Safety**: Compile-time checking for valid IRIs
- **Consistency**: Uniform identifier handling across the framework
- **Flexibility**: Supports DID, URI, URN, and other IRI schemes
- **Standards Compliance**: Aligns with W3C VC spec that allows any IRI

**Example:**
```kotlin
import org.trustweave.core.identifiers.Iri
import org.trustweave.did.identifiers.Did

// All of these work seamlessly:
val didSubject = CredentialSubject(id = Did("did:key:..."), claims = ...)
val uriSubject = CredentialSubject(id = Iri("https://example.com/user/123"), claims = ...)
val urnSubject = CredentialSubject(id = Iri("urn:uuid:..."), claims = ...)

// All are valid per W3C VC spec, and all use the common Iri base class
// Did extends Iri, so it can be used directly
```

---

## Benefits

1. **Standards Compliance**: Direct alignment with W3C VC Data Model
2. **IRI Support**: Full IRI support (DID, URI, URN) via common identifier base class
3. **Simplicity**: Removes format-agnostic abstractions
4. **Interoperability**: Full compatibility with VC ecosystem
5. **Clarity**: Clear VC-focused API surface
6. **Maintainability**: Less code, fewer abstractions
7. **Type Safety**: Leverages common identifier types (`Iri`, `Did`) for consistency

---

## Next Steps

1. Review and approve this design
2. Create detailed implementation plan
3. Begin Phase 1: Create new VC model
4. Migrate existing code incrementally

