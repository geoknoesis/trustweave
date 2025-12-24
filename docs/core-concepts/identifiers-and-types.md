---
title: Identifiers and Types
nav_order: 1
parent: Core Concepts
keywords:
  - identifiers
  - types
  - type safety
  - DID
  - IRI
  - developer guide
  - proof types
  - credential types
---

# Identifiers and Types

TrustWeave uses a type-safe system for identifiers and domain types to prevent errors, improve code clarity, and make APIs more intuitive. This guide explains how to work with identifiers and types in TrustWeave.

## Quick Start

```kotlin
import org.trustweave.did.identifiers.Did
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.types.ProofType
import org.trustweave.credential.types.CredentialType

// ✅ Identifiers - identify WHICH resource
val did = Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
val credId = CredentialId("https://example.com/credentials/123")

// ✅ Types - classify WHAT KIND of resource
val proofType = ProofType.Ed25519Signature2020
val credType = CredentialType.Education
```

## Understanding the Distinction

### Identifiers: "Which Resource?"

**Identifiers** are unique references to specific resources. They answer the question: **"Which one?"**

- Examples: `Did`, `CredentialId`, `IssuerId`, `VerificationMethodId`
- Format: URIs/IRIs following RFC 3987 (e.g., `did:key:...`, `https://...`)
- Purpose: Point to a specific entity, credential, or document
- Validation: Format is validated at creation time
- Serialization: Serialize as strings in JSON

```kotlin
// ✅ Identifiers point to specific resources
val issuerDid = Did("did:key:z6Mk...")           // Which DID?
val credentialId = CredentialId("https://...")   // Which credential?
val issuerId = IssuerId("did:web:example.com")   // Which issuer?
```

### Types: "What Kind?"

**Types** are classifications that categorize concepts. They answer the question: **"What kind?"**

- Examples: `ProofType`, `CredentialType`, `StatusPurpose`, `SchemaFormat`
- Format: Enums or sealed classes with predefined values
- Purpose: Classify and categorize (proof algorithm, credential category, etc.)
- Validation: Must be one of the allowed values
- Serialization: Serialize as strings (category names)

```kotlin
// ✅ Types classify resources
val proofType = ProofType.Ed25519Signature2020  // What kind of proof?
val credType = CredentialType.Education          // What kind of credential?
val purpose = StatusPurpose.REVOCATION          // What purpose?
```

### Quick Reference Table

| Aspect | Identifiers | Types |
|--------|-------------|-------|
| **Question** | "Which resource?" | "What kind?" |
| **Package** | `{module}.identifiers` | `{module}.types` |
| **Structure** | Classes extending `Iri` | Sealed classes / Enums |
| **Examples** | `Did`, `CredentialId` | `ProofType`, `CredentialType` |
| **Validation** | Format (IRI/DID pattern) | Value (allowed categories) |

---

## Working with Identifiers

### Creating Identifiers

All identifiers are created using constructor-like syntax. They validate automatically and throw `IllegalArgumentException` if invalid.

```kotlin
import org.trustweave.did.identifiers.Did
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.identifiers.IssuerId
import org.trustweave.core.identifiers.KeyId

// ✅ Direct construction (validates format automatically)
val did = Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
val credId = CredentialId("https://example.com/credentials/123")
val issuerId = IssuerId("did:web:example.com")
val keyId = KeyId("key-1")

// ❌ Invalid formats throw IllegalArgumentException
try {
    val invalidDid = Did("not-a-did")  // Throws!
} catch (e: IllegalArgumentException) {
    println("Invalid DID format: ${e.message}")
}
```

### Safe Parsing

Use extension functions for safe parsing when the input might be invalid:

```kotlin
import org.trustweave.did.identifiers.toDidOrNull
import org.trustweave.credential.identifiers.toCredentialIdOrNull

// ✅ Safe parsing (returns null if invalid)
val did = json.getString("issuer")?.toDidOrNull()
val credId = userInput.toCredentialIdOrNull()

// Use with Kotlin null-safety
did?.let { issuerDid ->
    // Use issuerDid safely
}
```

### Working with DIDs

DIDs have specialized methods and operators for common operations:

```kotlin
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.core.identifiers.KeyId

val did = Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")

// Extract DID method
val method = did.method  // "key"

// Extract identifier
val identifier = did.identifier  // "z6Mk..."

// Create verification method ID
val vmId1 = did + "key-1"                    // Operator syntax
val vmId2 = did with "key-1"                 // Infix syntax (more readable)
val vmId3 = did with KeyId("key-1")          // Type-safe with KeyId

// Check DID method
if (did isMethod "key") {
    println("This is a did:key DID")
}

// Get base DID (without path/fragment)
val baseDid = did.baseDid
```

### Creating Verification Method IDs

Verification Method IDs combine a DID with a key fragment:

```kotlin
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.core.identifiers.KeyId

val did = Did("did:key:z6Mk...")

// ✅ Three ways to create VerificationMethodId

// 1. Using operator (concise)
val vmId1 = did + "key-1"  // Creates "did:key:z6Mk...#key-1"

// 2. Using infix (more readable)
val vmId2 = did with "key-1"

// 3. Using constructor (most explicit)
val vmId3 = VerificationMethodId(
    did = did,
    keyId = KeyId("key-1")
)

// ✅ Parsing from string
val vmId4 = VerificationMethodId.parse("did:key:z6Mk...#key-1")

// ✅ Decompose
val (didPart, keyIdPart) = vmId4.decompose()
```

### Type Inheritance: All Identifiers are IRIs

All identifiers extend the base `Iri` class, enabling polymorphism:

```kotlin
import org.trustweave.core.identifiers.Iri
import org.trustweave.did.identifiers.Did
import org.trustweave.credential.identifiers.CredentialId

val did = Did("did:key:z6Mk...")
val credId = CredentialId("https://example.com/cred/123")

// ✅ All identifiers can be used as Iri
val iri1: Iri = did      // Did IS-A Iri
val iri2: Iri = credId   // CredentialId IS-A Iri

// ✅ Use Iri features on any identifier
fun processIri(iri: Iri) {
    when {
        iri.isUri -> println("URI: ${iri.value}")
        iri.isDid -> println("DID: ${iri.value}")
        iri.isUrn -> println("URN: ${iri.value}")
    }
}

processIri(did)      // Prints: "DID: did:key:..."
processIri(credId)   // Prints: "URI: https://..."
```

### Converting Between Identifier Types

```kotlin
import org.trustweave.did.identifiers.Did
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.identifiers.IssuerId

val did = Did("did:key:z6Mk...")

// ✅ Convert DID to other identifier types
val issuerId = IssuerId.fromDid(did)
val credId = CredentialId.fromDid(did)  // If credential ID is a DID

// ✅ Narrow Iri to specific type
val iri: Iri = did
val asDid = iri.asDidOrNull()  // Returns Did if valid, null otherwise
val asCredId = iri.asCredentialIdOrNull()
```

---

## Working with Types

### Proof Types

Proof types specify the cryptographic signature algorithm:

```kotlin
import org.trustweave.credential.types.ProofType
import org.trustweave.credential.types.ProofTypes

// ✅ Using predefined types
val ed25519 = ProofType.Ed25519Signature2020
val jwt = ProofType.JsonWebSignature2020
val bbs = ProofType.BbsBlsSignature2020

// ✅ Using convenience constants
val proof1 = ProofTypes.Ed25519
val proof2 = ProofTypes.JWT
val proof3 = ProofTypes.BBS

// ✅ Custom proof type
val custom = ProofType.Custom("MyCustomProofType")

// ✅ From string
val fromString = ProofTypes.fromString("Ed25519Signature2020")

// ✅ Serialization (automatically handled)
val json = Json.encodeToString(ed25519)  // "Ed25519Signature2020"
```

### Credential Types

Credential types classify what kind of credential it is:

```kotlin
import org.trustweave.credential.types.CredentialType
import org.trustweave.credential.types.CredentialTypes

// ✅ Standard types
val verifiableCred = CredentialType.VerifiableCredential  // Always required
val education = CredentialType.Education
val employment = CredentialType.Employment
val certification = CredentialType.Certification

// ✅ Using convenience constants
val types1 = listOf(
    CredentialTypes.VERIFIABLE_CREDENTIAL,  // Always required
    CredentialTypes.EDUCATION
)

// ✅ Custom credential type
val customType = CredentialType.Custom("ProfessionalLicenseCredential")

// ✅ From string
val fromString = CredentialType.fromString("EducationCredential")

// ✅ In VerifiableCredential
val credential = VerifiableCredential(
    type = listOf(
        CredentialType.VerifiableCredential,
        CredentialType.Education
    ),
    // ...
)
```

### Status Purpose

Status purpose indicates why a status list exists:

```kotlin
import org.trustweave.credential.types.StatusPurpose

// ✅ Enum values
val revocation = StatusPurpose.REVOCATION    // For revoking credentials
val suspension = StatusPurpose.SUSPENSION    // For suspending credentials

// ✅ In CredentialStatus
val status = CredentialStatus(
    id = StatusListId("https://..."),
    type = "StatusList2021Entry",
    statusPurpose = StatusPurpose.REVOCATION  // Typed!
    // ...
)
```

### Schema Format

Schema format specifies the validation schema type:

```kotlin
import org.trustweave.credential.types.SchemaFormat

// ✅ Enum values
val jsonSchema = SchemaFormat.JSON_SCHEMA  // JSON Schema Draft 7/2020-12
val shacl = SchemaFormat.SHACL             // SHACL for RDF

// ✅ In CredentialSchema
val schema = CredentialSchema(
    id = SchemaId("https://..."),
    type = "JsonSchemaValidator2018",
    schemaFormat = SchemaFormat.JSON_SCHEMA  // Typed!
)
```

---

## Using in Models

### Verifiable Credential

All identifier and type fields are now strongly typed:

```kotlin
import org.trustweave.credential.models.VerifiableCredential
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.identifiers.IssuerId
import org.trustweave.credential.types.CredentialType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

val credential = VerifiableCredential(
    id = CredentialId("https://example.com/credentials/123"),  // ✅ Typed
    type = listOf(
        CredentialType.VerifiableCredential,
        CredentialType.Education
    ),  // ✅ Typed
    issuer = IssuerId.fromDid(Did("did:key:z6Mk...")),  // ✅ Typed
    credentialSubject = buildJsonObject {
        put("id", "did:key:holder")
        put("degree", "Bachelor of Science")
    },
    issuanceDate = "2024-01-15T10:00:00Z"
)

// ✅ Access typed fields
println(credential.id?.value)      // "https://example.com/credentials/123"
println(credential.issuer.value)   // "did:key:z6Mk..."
println(credential.type.first())   // CredentialType.VerifiableCredential
```

### Proof

Proofs use typed proof type and verification method ID:

```kotlin
import org.trustweave.credential.models.Proof
import org.trustweave.credential.types.ProofType
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId

val did = Did("did:key:z6Mk...")
val proof = Proof(
    type = ProofType.Ed25519Signature2020,  // ✅ Typed
    created = "2024-01-15T10:00:00Z",
    verificationMethod = did with "key-1",  // ✅ Typed VerificationMethodId
    proofPurpose = "assertionMethod",
    proofValue = "zQeVbY4oey5q2M3XKaxup3tmzN4DRFT...",
    challenge = null,
    domain = null
)
```

### DID Document

DID documents use typed DIDs and verification method IDs:

```kotlin
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId

val did = Did("did:key:z6Mk...")
val document = DidDocument(
    id = did,  // ✅ Typed
    verificationMethod = listOf(
        VerificationMethod(
            id = did with "key-1",  // ✅ Typed
            type = "Ed25519VerificationKey2020",
            controller = did,  // ✅ Typed
            publicKeyMultibase = "z6Mk..."
        )
    ),
    authentication = listOf(
        did with "key-1"  // ✅ Typed list
    ),
    assertionMethod = listOf(
        did with "key-1"  // ✅ Typed list
    )
)
```

---

## Benefits of Type Safety

### Compile-Time Safety

```kotlin
// ✅ Compiler prevents these mistakes:

fun verifyCredential(credId: CredentialId, issuer: IssuerId) {
    // ...
}

val did = Did("did:key:z6Mk...")
val credId = CredentialId("https://...")

verifyCredential(did, credId)  // ❌ Compile error: Did != CredentialId

// ✅ Correct usage
verifyCredential(credId, IssuerId.fromDid(did))
```

### IDE Autocomplete

IDEs provide better autocomplete and navigation:

```kotlin
// ✅ IDE knows available proof types
val proofType = ProofType.  // IDE suggests: Ed25519Signature2020, JsonWebSignature2020, etc.

// ✅ IDE knows available credential types
val credType = CredentialType.  // IDE suggests: Education, Employment, etc.

// ✅ IDE knows DID methods
val method = did.method  // IDE knows this returns String, suggests methods
```

### Refactoring Safety

Type-safe identifiers make refactoring safer:

```kotlin
// ✅ Renaming or moving identifiers is caught at compile time
// ✅ Find all usages works accurately
// ✅ Safe to extract to functions with typed parameters
```

---

## Package Structure

### Import Guidelines

```kotlin
// ✅ Identifiers
import org.trustweave.core.identifiers.Iri
import org.trustweave.core.identifiers.KeyId
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.identifiers.IssuerId
import org.trustweave.credential.identifiers.SchemaId
import org.trustweave.credential.identifiers.StatusListId

// ✅ Types
import org.trustweave.credential.types.ProofType
import org.trustweave.credential.types.CredentialType
import org.trustweave.credential.types.StatusPurpose
import org.trustweave.credential.types.SchemaFormat

// ✅ Extension functions (for safe parsing)
import org.trustweave.did.identifiers.toDidOrNull
import org.trustweave.credential.identifiers.toCredentialIdOrNull
```

### Module Organization

- **`common`**: Base identifiers (`Iri`, `KeyId`)
- **`did-core`**: DID-related identifiers (`Did`, `VerificationMethodId`)
- **`credential-core`**: Credential identifiers (`CredentialId`, `IssuerId`, etc.) and types (`ProofType`, `CredentialType`, etc.)

---

## Common Patterns

### Pattern: Safe Parsing from JSON

```kotlin
import org.trustweave.did.identifiers.toDidOrNull
import org.trustweave.credential.identifiers.toCredentialIdOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun parseCredentialFromJson(jsonString: String): VerifiableCredential? {
    return try {
        val json = Json.parseToJsonElement(jsonString).jsonObject
        
        // ✅ Safe parsing with extension functions
        val id = json["id"]?.jsonPrimitive?.content?.toCredentialIdOrNull()
        val issuer = json["issuer"]?.jsonPrimitive?.content
            ?.toDidOrNull()
            ?.let { IssuerId.fromDid(it) }
            ?: return null
        
        // ... parse rest of credential
        VerifiableCredential(
            id = id,
            issuer = issuer,
            // ...
        )
    } catch (e: Exception) {
        null
    }
}
```

### Pattern: Working with Collections

```kotlin
import org.trustweave.did.identifiers.Did
import org.trustweave.credential.identifiers.CredentialId

// ✅ Type-safe collections
val dids: List<Did> = listOf(
    Did("did:key:z6Mk..."),
    Did("did:web:example.com")
)

val credIds: Set<CredentialId> = setOf(
    CredentialId("https://example.com/cred/1"),
    CredentialId("https://example.com/cred/2")
)

// ✅ Filter by type
val keyDids = dids.filter { it isMethod "key" }

// ✅ Map to strings when needed
val didStrings = dids.map { it.value }
```

### Pattern: Type Narrowing

```kotlin
import org.trustweave.core.identifiers.Iri
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.asDidOrNull

fun processIri(iri: Iri) {
    // ✅ Narrow Iri to Did if it's a DID
    val did = iri.asDidOrNull()
    if (did != null) {
        // Handle as DID
        println("DID method: ${did.method}")
    } else {
        // Handle as other IRI
        println("Other IRI: ${iri.value}")
    }
}
```

---

## Best Practices

### ✅ DO

```kotlin
// ✅ Use typed identifiers everywhere
val did = Did("did:key:z6Mk...")
val credId = CredentialId("https://...")

// ✅ Use safe parsing for user input or JSON
val did = userInput.toDidOrNull() ?: return

// ✅ Leverage type inheritance
fun acceptAnyIri(iri: Iri) { /* ... */ }
acceptAnyIri(did)      // ✅ Works
acceptAnyIri(credId)   // ✅ Works

// ✅ Use companion factory methods when available
val issuerId = IssuerId.fromDid(did)  // More semantic than IssuerId(did.value)
```

### ❌ DON'T

```kotlin
// ❌ Don't use String for identifiers
fun processCredential(id: String) { }  // Use CredentialId instead

// ❌ Don't bypass validation
val did = Did("not-a-did")  // Will throw - use safe parsing instead

// ❌ Don't mix identifier types
fun process(did: Did, credId: CredentialId) {
    if (did.value == credId.value) { }  // Type mismatch - probably wrong logic
}

// ❌ Don't use strings for types
val proofType: String = "Ed25519Signature2020"  // Use ProofType instead
```

---

## Troubleshooting

### Common Errors

**Error: "Unresolved reference: Did"**
```kotlin
// ❌ Wrong import
import org.trustweave.core.types.Did

// ✅ Correct import
import org.trustweave.did.identifiers.Did
```

**Error: "Type mismatch: expected CredentialId, found Did"**
```kotlin
val did = Did("did:key:z6Mk...")

// ❌ Wrong
val credId: CredentialId = did

// ✅ Correct - convert explicitly
val credId = CredentialId.fromDid(did)
```

**Error: "IllegalArgumentException: Invalid DID format"**
```kotlin
// ❌ Wrong - invalid format
val did = Did("not-a-did")

// ✅ Correct - use safe parsing
val did = "did:key:z6Mk...".toDidOrNull() ?: return
```

---

## Related Documentation

- [Identifier Design Specification](../advanced/identifier-design.md) - Comprehensive design document covering the architecture and implementation details
- [Decentralized Identifiers (DIDs)](dids.md) - Learn about DIDs and DID documents
- [Verifiable Credentials](verifiable-credentials.md) - Learn about credentials and their lifecycle

---

## Summary

- **Identifiers** (`Did`, `CredentialId`, etc.) answer **"Which resource?"** - use for unique references
- **Types** (`ProofType`, `CredentialType`, etc.) answer **"What kind?"** - use for classifications
- All identifiers extend `Iri` enabling polymorphism
- Use safe parsing extensions (`toDidOrNull()`, etc.) for potentially invalid input
- Type safety catches errors at compile time and improves IDE support

