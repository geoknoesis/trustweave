---
title: Data Types
nav_order: 60
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

### Did

Type-safe Decentralized Identifier (value class).

```kotlin
@JvmInline
value class Did(val value: String)
```

**Properties:**
- `value`: The underlying string value of the DID (e.g., "did:key:z6Mk...")

**Usage:**
```kotlin
// Create Did from string
val did = Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")

// Access underlying string value
val didString = did.value

// Use in DID operations
val resolution = didResolver.resolve(did)
```

**Note:** `Did` is a value class that provides compile-time type safety and validation. The constructor validates that the DID follows the `did:method:identifier` format.

### DidDocument

W3C-compliant DID document (`org.trustweave.did.model.DidDocument`).

```kotlin
data class DidDocument(
    val id: Did,                                             // Typed DID
    val context: List<String> = listOf("https://www.w3.org/ns/did/v1"),
    val alsoKnownAs: List<DidOrUrl> = emptyList(),
    val controller: List<Did> = emptyList(),
    val verificationMethod: List<VerificationMethod> = emptyList(),
    val authentication: List<VerificationMethodId> = emptyList(),
    val assertionMethod: List<VerificationMethodId> = emptyList(),
    val keyAgreement: List<VerificationMethodId> = emptyList(),
    val capabilityInvocation: List<VerificationMethodId> = emptyList(),
    val capabilityDelegation: List<VerificationMethodId> = emptyList(),
    val service: List<DidService> = emptyList()             // Not `Service` and not nullable
)
```

**Properties:** Typed `id` (`Did`), JSON-LD `context`, optional `alsoKnownAs`/`controller`, lists of `VerificationMethod`, verification-relationship references (`VerificationMethodId`), and a `DidService` list (empty when none).

### VerifiableCredential

W3C VC Data Model credential (`org.trustweave.credential.model.vc.VerifiableCredential`). Supports both VC 1.1 and VC 2.0 contexts and three proof formats (VC-LD, VC-JWT, SD-JWT-VC).

```kotlin
data class VerifiableCredential(
    @SerialName("@context")
    val context: List<String> = listOf("https://www.w3.org/2018/credentials/v1"),
    val id: CredentialId? = null,                  // Typed, optional
    val type: List<CredentialType>,                // Must include "VerifiableCredential"
    val issuer: Issuer,                            // Issuer (Iri-backed, can include id/name object)
    val issuanceDate: Instant,                     // kotlinx.datetime.Instant
    val credentialSubject: CredentialSubject,      // Typed subject (id + claims)
    val validFrom: Instant? = null,                // VC 2.0
    val expirationDate: Instant? = null,           // VC 1.1
    val validUntil: Instant? = null,               // VC 2.0
    val name: String? = null,                      // VC 2.0
    val description: String? = null,               // VC 2.0
    val credentialStatus: CredentialStatus? = null,
    val credentialSchema: CredentialSchema? = null,
    val evidence: List<Evidence>? = null,
    val termsOfUse: List<TermsOfUse>? = null,
    val proof: CredentialProof? = null             // Sealed: LinkedDataProof / JwtProof / SdJwtProof
)
```

**Notes:** `id` is a `CredentialId` (not raw `String`); `type` is a `List<CredentialType>`; `issuer` is an `Issuer` value object (use `Issuer.fromDid(Did)`); dates are `kotlinx.datetime.Instant`; `proof` is the sealed `CredentialProof` hierarchy (not a flat `Proof`).

### VerifiablePresentation

W3C VP Data Model presentation (`org.trustweave.credential.model.vc.VerifiablePresentation`).

```kotlin
data class VerifiablePresentation(
    @SerialName("@context")
    val context: List<String> = listOf("https://www.w3.org/2018/credentials/v1"),
    val id: CredentialId? = null,                      // Typed, optional
    val type: List<CredentialType>,                    // Must include "VerifiablePresentation"
    val holder: Iri,                                   // Holder IRI (typically a DID)
    val verifiableCredential: List<VerifiableCredential>,
    val proof: CredentialProof? = null                 // Sealed CredentialProof
    // Format-specific fields exist (e.g. challenge/domain via ProofOptions)
)
```

**Notes:** `holder` is an `Iri` (DID/URI), not a flat `String`; `proof` uses the same `CredentialProof` sealed hierarchy as credentials.

## DID Types

### DidResolutionResult

Sealed result of DID resolution (`org.trustweave.did.resolver.DidResolutionResult`). Use an exhaustive **`when`**—there is no single nullable **`document`** on one flat type.

- **`Success`** — **`document`** (**`DidDocument`**, always present), **`documentMetadata`**, **`resolutionMetadata`**
- **`Failure`** (sealed) — **`NotFound`** (**`did`**, optional **`reason`**), **`InvalidFormat`** (invalid DID string, **`reason`**), **`MethodNotRegistered`** (**`method`**, **`availableMethods`**), **`ResolutionError`** (**`did`**, **`reason`**, optional **`cause`**)

```kotlin
import org.trustweave.did.resolver.DidResolutionResult

when (val result = trustWeave.resolveDid(did)) {
    is DidResolutionResult.Success -> result.document
    is DidResolutionResult.Failure.NotFound -> { /* … */ result }
    is DidResolutionResult.Failure.InvalidFormat -> { /* … */ result }
    is DidResolutionResult.Failure.MethodNotRegistered -> { /* … */ result }
    is DidResolutionResult.Failure.ResolutionError -> { /* … */ result }
}
```

### DidResolutionMetadata

Structured resolution metadata (`org.trustweave.did.resolver.DidResolutionMetadata`): **`contentType`**, optional W3C-style **`error`** / **`errorMessage`**, **`pattern`**, **`driverUrl`**, **`duration`**, **`retrieved`**, **`canonicalId`**, **`equivalentId`**, **`nextUpdate`**, **`nextVersionId`**, and extensible string **`properties`**. Success and failure results carry metadata on the corresponding **`DidResolutionResult`** subtype.

### DidCreationOptions

Options for creating DIDs (`org.trustweave.did.DidCreationOptions`).

```kotlin
data class DidCreationOptions(
    val algorithm: KeyAlgorithm = KeyAlgorithm.ED25519,
    val purposes: List<KeyPurpose> = listOf(KeyPurpose.AUTHENTICATION),
    val additionalProperties: Map<String, Any?> = emptyMap()
)
```

**Properties:**
- **`algorithm`**: Key algorithm (e.g. **`ED25519`**, **`SECP256K1`**, **`RSA`**)
- **`purposes`**: **`KeyPurpose`** entries (`AUTHENTICATION`, `ASSERTION`, `KEY_AGREEMENT`, `CAPABILITY_INVOCATION`, `CAPABILITY_DELEGATION`). The enum value `ASSERTION` maps to the DID spec's `assertionMethod` relationship.
- **`additionalProperties`**: Method-specific options

## Credential Types

### VerificationResult

Sealed result of credential (or presentation) verification (`org.trustweave.credential.results.VerificationResult`).

- **`Valid`** — Cryptographic and configured checks passed; includes **`credential`**, issuer/subject IRIs, issuance and optional expiration instants, **`warnings`**, and optional format metadata.
- **`Invalid`** — Sealed failure hierarchy, including **`InvalidProof`**, **`Expired`**, **`NotYetValid`**, **`Revoked`**, **`InvalidIssuer`**, **`UntrustedIssuer`**, **`UnsupportedFormat`**, **`AdapterNotReady`**, **`SchemaValidationFailed`**, **`MultipleFailures`**, and others.

Use **`result.isValid`** or an exhaustive **`when`**; aggregate messages with **`result.allErrors`** on invalid branches.

### IssuanceResult

Sealed result of **`trustWeave.issue { … }`** (`org.trustweave.credential.results.IssuanceResult`). **`Success`** exposes the issued **`VerifiableCredential`**; **`Failure`** includes **`UnsupportedFormat`**, **`AdapterNotReady`**, **`InvalidRequest`**, **`AdapterError`**, **`MultipleFailures`**, and other variants. Use **`result.isSuccess`** / exhaustive **`when`**; on any **`Failure`**, use **`result.allErrors`** for a single list of human-readable messages (logging, APIs).

### PresentationResult

Sealed result of the TrustWeave presentation flow (`org.trustweave.trust.types.PresentationResult`), e.g. **`presentationFromWalletResult`**. **`Success`** carries the **`VerifiablePresentation`**; **`Failure`** includes **`AdapterNotReady`**, **`InvalidRequest`**, and **`AdapterError`**. Use **`when`**; on failure, use **`result.allErrors`**.

### CredentialStatus

Revocation status entry attached to a `VerifiableCredential` (`org.trustweave.credential.model.vc.CredentialStatus`).

```kotlin
data class CredentialStatus(
    val id: StatusListId,                                  // Typed status list entry id
    val type: String,                                      // e.g. "StatusList2021Entry"
    val statusPurpose: StatusPurpose = StatusPurpose.REVOCATION,
    val statusListIndex: String? = null,                   // Index encoded as string
    val statusListCredential: StatusListId? = null,        // Status list credential id
    val formatData: Map<String, JsonElement> = emptyMap()
)
```

## Wallet Types

### CredentialFilter

Filter for querying credentials (`org.trustweave.wallet.CredentialFilter`).

```kotlin
data class CredentialFilter(
    val issuer: String? = null,             // Filter by issuer DID
    val type: List<String>? = null,         // Filter by one or more credential types
    val subjectId: String? = null,          // Filter by subject id
    val expired: Boolean? = null,           // Filter by expiration status
    val revoked: Boolean? = null            // Filter by revocation status
)
```

### CredentialCollection

Collection of credentials (`org.trustweave.wallet.CredentialCollection`).

```kotlin
data class CredentialCollection(
    val id: String,
    val name: String,
    val description: String? = null,
    val createdAt: Instant = Clock.System.now(),   // kotlinx.datetime.Instant
    val credentialCount: Int = 0
)
```

### CredentialMetadata

Metadata for a credential (`org.trustweave.wallet.CredentialMetadata`).

```kotlin
data class CredentialMetadata(
    val credentialId: String,
    val notes: String? = null,
    val tags: Set<String> = emptySet(),
    val metadata: Map<String, Any> = emptyMap(),
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now()
)
```

### WalletCapabilities

Capabilities supported by a wallet (`org.trustweave.wallet.WalletCapabilities`).

```kotlin
data class WalletCapabilities(
    val credentialStorage: Boolean = true,
    val credentialQuery: Boolean = true,
    val collections: Boolean = false,
    val tags: Boolean = false,
    val metadata: Boolean = false,
    val archive: Boolean = false,
    val refresh: Boolean = false,
    val createPresentation: Boolean = false,
    val selectiveDisclosure: Boolean = false,
    val didManagement: Boolean = false,
    val keyManagement: Boolean = false,
    val credentialIssuance: Boolean = false
) {
    fun supports(feature: String): Boolean
}
```

### WalletStatistics

Statistics about a wallet (`org.trustweave.wallet.WalletStatistics`).

```kotlin
data class WalletStatistics(
    val totalCredentials: Int = 0,
    val validCredentials: Int = 0,
    val expiredCredentials: Int = 0,
    val revokedCredentials: Int = 0,
    val collectionsCount: Int = 0,
    val tagsCount: Int = 0,
    val archivedCount: Int = 0
)
```

## Blockchain Types

### AnchorResult

Result of anchoring a payload to a blockchain (`org.trustweave.anchor.AnchorResult`).

```kotlin
data class AnchorResult(
    val ref: AnchorRef,
    val payload: JsonElement,
    val mediaType: String = "application/json",
    val timestamp: Long? = null     // Epoch seconds
)
```

### AnchorRef

Reference to anchored data on a blockchain (`org.trustweave.anchor.AnchorRef`).

```kotlin
data class AnchorRef(
    val chainId: String,                     // CAIP-2 chain id (e.g. "algorand:mainnet")
    val txHash: String,                      // Transaction hash or operation id
    val contract: String? = null,            // Optional registry contract / app id
    val extra: Map<String, String> = emptyMap()
)
```

## Key Types

### KeyId

Type-safe key identifier (value class).

```kotlin
@JvmInline
value class KeyId(val value: String)
```

**Properties:**
- `value`: The underlying string value of the key identifier

**Usage:**
```kotlin
// Create KeyId from string
val keyId = KeyId("did:key:z6Mk...#key-1")

// Access underlying string value
val keyIdString = keyId.value

// Use in KMS operations
val signature = kms.sign(keyId, data)
val publicKey = kms.getPublicKey(keyId)
```

**Note:** `KeyId` is a value class that provides compile-time type safety. When you have a `KeyHandle`, the `id` property is already a `KeyId`, so you can use it directly. When constructing from a string, use `KeyId("your-key-string")`.

### KeyInfo

Information about a cryptographic key as exposed by `KeyManagement`-capable wallets (`org.trustweave.wallet.KeyInfo`).

```kotlin
data class KeyInfo(
    val id: String,                          // Key id (string, not the `KeyId` value class)
    val algorithm: String,                   // Key algorithm
    val publicKeyJwk: Map<String, Any?>? = null,
    val publicKeyMultibase: String? = null,
    val createdAt: Instant = Clock.System.now()
)
```

### KeyPurpose

Purpose of a cryptographic key (`org.trustweave.did.KeyPurpose`).

```kotlin
enum class KeyPurpose(val purposeName: String) {
    AUTHENTICATION("authentication"),
    ASSERTION("assertionMethod"),              // NOT `ASSERTION_METHOD`
    KEY_AGREEMENT("keyAgreement"),
    CAPABILITY_INVOCATION("capabilityInvocation"),
    CAPABILITY_DELEGATION("capabilityDelegation");
}
```

**Note:** the enum constant is `ASSERTION` (not `ASSERTION_METHOD`); its `purposeName` is `"assertionMethod"` to match the DID Core spec.

## Proof Types

### CredentialProof

Sealed cryptographic proof (`org.trustweave.credential.model.vc.CredentialProof`). The `proof` field on `VerifiableCredential` / `VerifiablePresentation` is of this sealed type — there is no flat `Proof` class. The serializer is the custom `CredentialProofSerializer`, so any variant round-trips through `kotlinx.serialization` automatically.

```kotlin
sealed class CredentialProof {
    data class LinkedDataProof(
        val type: String,                    // e.g. "Ed25519Signature2020", "JsonWebSignature2020"
        val created: kotlinx.datetime.Instant,
        val verificationMethod: String,      // DID URL or IRI
        val proofPurpose: String,            // "assertionMethod", "authentication", ...
        val proofValue: String,              // Base58/Base64 signature
        val additionalProperties: Map<String, JsonElement> = emptyMap()
    ) : CredentialProof()

    data class JwtProof(val jwt: String) : CredentialProof()

    data class SdJwtVcProof(
        val sdJwtVc: String,                 // SD-JWT-VC compact serialization
        val disclosures: List<String>? = null
    ) : CredentialProof()

    data class MdocProof(
        val deviceResponse: ByteArray,       // ISO 18013-5 CBOR DeviceResponse
        val docType: String
    ) : CredentialProof()
}
```

| Variant | Format | Produced by | Typical `proof.type` / payload |
|---|---|---|---|
| `LinkedDataProof` | W3C VC-LD (JSON-LD Data Integrity) | `VcLdProofEngine`, `Bbs2023ProofEngine` | `Ed25519Signature2020`, `JsonWebSignature2020`, `DataIntegrityProof` (`bbs-2023`) |
| `JwtProof` | W3C VC-JWT (compact JWS) | `VcLdProofEngine` in JWT mode | Compact JWT string |
| `SdJwtVcProof` | IETF SD-JWT-VC | `SdJwtProofEngine` | SD-JWT-VC string + tilde-separated disclosures |
| `MdocProof` | ISO 18013-5 mDoc/mDL (CBOR/COSE) | `MdocProofEngine` (`credentials/plugins/mdl`) | CBOR `DeviceResponse` bytes + `docType` |

The proof variant is picked by the `ProofEngine` matching the credential's `ProofSuiteId`
(`VC_LD`, `VC_JWT`, `SD_JWT_VC`, `MDOC`, `BBS_2023`). Engines are auto-discovered via
the `ProofEngineProvider` SPI — see [Proof Engine Implementation Guide](advanced/proof-engine-implementation-guide.md).

## Service Types

### DidService

Service endpoint in a DID document (`org.trustweave.did.model.DidService`).

```kotlin
data class DidService(
    val id: String,                          // Service id (often a relative URI)
    val type: List<String>,                  // DID 1.1 allows a string or set; modelled as non-empty list
    val serviceEndpoint: Any                 // URL string, object, or array
)
```

## Related Documentation

- **[Core API](core-api.md)** - API methods that use these types
- **[Wallet API](wallet-api.md)** - Wallet-specific types
- **[Quick Reference](quick-reference.md)** - Quick API lookup

