# Verifiable Credentials (VCs)

> VeriCore expansions in this guide are authored by [Geoknoesis LLC](https://www.geoknoesis.com). They reflect Geoknoesis’ recommended patterns for W3C Verifiable Credentials on the JVM.

## What is a Verifiable Credential?

A **Verifiable Credential** is a tamper-evident attestation following the W3C VC Data Model. It combines:

```kotlin
dependencies {
    implementation("com.geoknoesis.vericore:vericore-core:1.0.0-SNAPSHOT")
}
```

**Result:** Grants access to the credential builders and verification helpers referenced throughout this guide.

1. **Metadata** – issuer, issuance/expiration dates, schema references.  
2. **Credential subject** – the claims being asserted (`name`, `degree`, `license`, etc.).  
3. **Proof** – cryptographic signature binding the issuer to the credential content.

## Why VCs matter in VeriCore

- They are the unit of trust flowing between issuers and verifiers.  
- Wallets store VCs, anchor clients notarise them, and verification routines replay the proofs.  
- Typed builders and canonicalisation keep the credential lifecycle consistent across DID methods and signature suites.

## How VeriCore issues and verifies VCs

| Component | Purpose |
|-----------|---------|
| `CredentialServiceRegistry` | Discovers issuer/verifier services (in-memory or SPI). |
| `VeriCore.issueCredential` | High-level facade performing canonicalisation, signing, and proof attachment. |
| `VeriCore.verifyCredential` | Rebuilds canonical form, resolves DIDs, validates proofs, and returns `CredentialVerificationResult`. |
| `CredentialIssuanceOptions` | Lower-level SPI options (validity window, schema hints) when using `CredentialServiceRegistry`. |

Detailed API signatures live in the [Credential Service API reference](../api-reference/credential-service-api.md).

### Example: issuing a credential

```kotlin
import com.geoknoesis.vericore.VeriCore
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

suspend fun issueEmployeeBadge(vericore: VeriCore, issuerDid: String, issuerKeyId: String) =
    vericore.issueCredential(
        issuerDid = issuerDid,
        issuerKeyId = issuerKeyId,
        credentialSubject = buildJsonObject {
            put("id", "did:key:holder-123")
            put("role", "Site Reliability Engineer")
            put("level", "L5")
        },
        types = listOf("VerifiableCredential", "EmploymentCredential")
    ).getOrThrow()

**Outcome:** Issues a signed credential using typed issuance options, returning a `VerifiableCredential` that downstream wallets or verifiers can consume.

VeriCore automatically:

- Canonicalises the JSON payload using JSON Canonicalization Scheme (JCS).  
- Signs the digest through the configured `KeyManagementService`.  
- Embeds the resulting proof (`Ed25519Signature2020` by default) into the VC.  
- Returns a `VerifiableCredential` data class that you can store or present.

### Example: verifying a credential

```kotlin
import com.geoknoesis.vericore.credential.verifyCredential

suspend fun verifyBadge(vericore: VeriCore, credential: com.geoknoesis.vericore.credential.models.VerifiableCredential) =
    vericore.verifyCredential(credential).fold(
        onSuccess = { result ->
            require(result.valid) { "Proofs failed: ${result.errors}" }
            println("Credential verified with checks: ${result.checks}")
        },
        onFailure = { error ->
            println("Verification failed: ${error.message}")
        }
    )

**Outcome:** Surfaces verification success or failure reasons, letting you guard business logic with `result.valid` and log granular errors.

Verification resolves the issuer DID document, checks the signature suites, and applies optional policies (expiration, schema, revocation when present).

## Practical usage tips

- **SPI-level options** – drop down to `CredentialServiceRegistry` and supply `CredentialIssuanceOptions` when you need custom proof types, schema hints, or audiences.  
- **Anchoring** – store the credential digest with a `BlockchainAnchorClient` to prove freshness (see [Blockchain Anchoring](blockchain-anchoring.md)).  
- **Revocation** – integrate status endpoints by adding `credentialStatus` claims; custom verification policies can enforce them.  
- **Error handling** – all credential operations return `Result<T>` with structured `VeriCoreError` types. Use `result.fold()` or `result.getOrThrow()` for error handling. See [Error Handling](../advanced/error-handling.md).
- **Input validation** – VeriCore automatically validates credential structure, issuer DID format, and method registration before issuance.

## See also

- [Credential Service API](../api-reference/credential-service-api.md) for parameter details and SPI guidance.  
- [Quick Start – Step 4 & 5](../getting-started/quick-start.md#step-4-issue-a-credential-and-store-it) for a runnable walkthrough.  
- [Wallets](wallets.md) for storage and presentation.  
- [Architecture Overview](../introduction/architecture-overview.md) for the credential flow diagram.
# Verifiable Credentials (VCs)

> VeriCore and this guide are maintained by [Geoknoesis LLC](https://www.geoknoesis.com). Geoknoesis provides both the open source toolkit and the commercial support offerings referenced below.

## What is a Verifiable Credential?

A **Verifiable Credential (VC)** is a tamper-evident credential that follows the W3C Verifiable Credentials Data Model 1.1. It enables you to make claims about yourself or others in a way that is cryptographically verifiable.

## VC Structure

A Verifiable Credential contains:

1. **Metadata**: Issuer, issuance date, expiration, etc.
2. **Credential Subject**: The claims (the actual data)
3. **Proof**: Cryptographic proof of who issued it
4. **Schema**: Optional schema for validation
5. **Status**: Optional revocation status

### Example VC

```json
{
  "@context": [
    "https://www.w3.org/2018/credentials/v1",
    "https://www.w3.org/2018/credentials/examples/v1"
  ],
  "id": "https://example.com/credentials/3732",
  "type": ["VerifiableCredential", "UniversityDegreeCredential"],
  "issuer": "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
  "issuanceDate": "2023-01-01T00:00:00Z",
  "expirationDate": "2028-01-01T00:00:00Z",
  "credentialSubject": {
    "id": "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
    "degree": {
      "type": "BachelorDegree",
      "name": "Bachelor of Science in Computer Science",
      "university": "Example University"
    }
  },
  "proof": {
    "type": "Ed25519Signature2020",
    "created": "2023-01-01T00:00:00Z",
    "verificationMethod": "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK#keys-1",
    "proofPurpose": "assertionMethod",
    "proofValue": "z5J1pJ2..."
  }
}
```

## VC Lifecycle

### 1. Issuance

A credential is **issued** by an issuer to a subject:

```kotlin
import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.credential.CredentialIssuanceOptions

// Create credential (without proof)
val credential = VerifiableCredential(
    type = listOf("VerifiableCredential", "PersonCredential"),
    issuer = issuerDid,
    credentialSubject = buildJsonObject {
        put("id", subjectDid)
        put("name", "Alice")
        put("email", "alice@example.com")
    },
    issuanceDate = Instant.now().toString()
)

// Issue credential (add proof)
val issuedCredential = credentialService.issueCredential(
    credential,
    CredentialIssuanceOptions(
        proofType = "Ed25519Signature2020",
        keyId = issuerKeyId
    )
)

**Outcome:** Produces a signed credential ready for distribution, anchored to the specific proof type and key you configured.

### 2. Storage

Store credentials in a wallet:

```kotlin
import com.geoknoesis.vericore.testkit.credential.BasicWallet

val wallet = BasicWallet()
val credentialId = wallet.store(issuedCredential)

**Outcome:** Persists the credential in a wallet so it can be queried, organised, and presented later.
```

### 3. Presentation

Create a **Verifiable Presentation** to share credentials:

```kotlin
import com.geoknoesis.vericore.credential.models.VerifiablePresentation
import com.geoknoesis.vericore.credential.PresentationOptions

val presentation = VerifiablePresentation(
    type = listOf("VerifiablePresentation"),
    verifiableCredential = listOf(issuedCredential),
    holder = subjectDid,
    proof = // ... proof of presentation
)

**Outcome:** Wraps one or more credentials in a holder-signed presentation, enabling selective disclosure downstream.
```

### 4. Verification

Verify a credential or presentation:

```kotlin
import com.geoknoesis.vericore.credential.CredentialVerificationOptions
import com.geoknoesis.vericore.credential.did.CredentialDidResolver
import com.geoknoesis.vericore.credential.verifier.CredentialVerifier
import com.geoknoesis.vericore.did.DidMethodRegistry
import com.geoknoesis.vericore.did.toCredentialDidResolution

val didRegistry = DidMethodRegistry()
// register DID methods here, e.g. didRegistry.register(didMethod)

val didResolver = CredentialDidResolver { did ->
    didRegistry.resolve(did).toCredentialDidResolution()
}

val verifier = CredentialVerifier(didResolver)

val result = verifier.verify(
    credential = issuedCredential,
    options = CredentialVerificationOptions(
        checkRevocation = false, // Requires status list integration
        checkExpiration = true,
        validateSchema = false,
        didResolver = didResolver
    )
)

if (result.valid) {
    println("Credential passed structural checks.")
} else {
    println("Verification errors: ${result.errors}")
}

**Outcome:** Indicates whether the credential satisfied structural checks (expiration, DID resolution, optional revocation) and surfaces diagnostics for debugging.
```

> **Important:** The built-in verifier performs structural checks today (proof fields, expiration, DID resolution). Integrate a dedicated cryptographic proof validator and revocation resolver for production deployments.

### 5. Revocation

Revoke a credential if needed:

```kotlin
// Credential status is checked during verification
// Revocation is handled via credentialStatus field
```

## Types of Claims

### Identity Claims

Claims about who you are:

- Name
- Date of birth
- Nationality
- Email address

### Achievement Claims

Claims about what you've accomplished:

- Educational degrees
- Professional certifications
- Awards
- Skills

### Authorization Claims

Claims about what you're allowed to do:

- Access permissions
- Membership status
- Role assignments

## Proof Types

VeriCore supports multiple proof types:

- **Ed25519Signature2020**: Ed25519 signatures (recommended)
- **JsonWebSignature2020**: JWT-based proofs
- **BbsBlsSignature2020**: BBS+ signatures for selective disclosure

## Schema Validation

Credentials can reference schemas for validation:

```kotlin
val credential = VerifiableCredential(
    // ...
    credentialSchema = CredentialSchema(
        id = "https://example.com/schemas/person.json",
        type = "JsonSchemaValidator2018",
        schemaFormat = SchemaFormat.JSON_SCHEMA
    )
)
```

## Privacy Features

### Selective Disclosure

Reveal only specific fields from a credential:

```kotlin
val presentation = wallet.createSelectiveDisclosure(
    credentialIds = listOf(credentialId),
    disclosedFields = listOf("name", "email"), // Only reveal name and email
    holderDid = holderDid,
    options = PresentationOptions(...)
)
```

### Zero-Knowledge Proofs

Some proof types (like BBS+) support zero-knowledge proofs, allowing you to prove claims without revealing the actual values.

## Common Use Cases

- **Education**: Diplomas, certificates, transcripts
- **Employment**: Work history, skills, references
- **Healthcare**: Medical records, prescriptions, test results
- **Identity**: Government IDs, passports, driver's licenses
- **Membership**: Club memberships, subscriptions, loyalty programs

## Best Practices

1. **Always verify credentials** before trusting them
2. **Check expiration dates** to ensure credentials are still valid
3. **Verify revocation status** to ensure credentials haven't been revoked
4. **Use selective disclosure** to minimize data exposure
5. **Store credentials securely** in a wallet

## Next Steps

- Learn about [Wallets](wallets.md) for managing credentials
- Explore the [Wallet API Tutorial](../tutorials/wallet-api-tutorial.md)
- Check out the [Credential Service API Reference](../api-reference/credential-service-api.md) and [Core API Reference](../api-reference/core-api.md)

