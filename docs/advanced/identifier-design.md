---
title: Identifier Design Specification
nav_order: 1
parent: Advanced Topics
keywords:
  - identifiers
  - types
  - IRI
  - DID
  - type safety
  - value classes
  - design
---

# Identifier Design Specification

> **Version**: 1.0  
> **Status**: Design Specification  
> **Last Updated**: 2024  
> **Authors**: TrustWeave Architecture Team

## Executive Summary

This document specifies the design for modeling identifiers and types in TrustWeave. It establishes clear separation between **identifiers** (opaque identity references) and **types** (domain classifications), provides type safety through value classes, and ensures consistent validation and serialization across the codebase.

> **Developer Guide**: For practical usage examples and common patterns, see the [Identifiers and Types](../core-concepts/identifiers-and-types.md) guide in Core Concepts.

## Table of Contents

1. [Core Principles](#core-principles)
2. [Identifiers vs Types](#identifiers-vs-types)
3. [Architecture](#architecture)
4. [Base Identifier: IRI](#base-identifier-iri)
5. [Module-Specific Identifiers](#module-specific-identifiers)
6. [Domain Types](#domain-types)
7. [Serialization Strategy](#serialization-strategy)
8. [Type Safety & Validation](#type-safety--validation)
9. [Package Organization](#package-organization)
10. [API Ergonomic Enhancements](#api-ergonomic-enhancements)
11. [Migration Strategy](#migration-strategy)
12. [Usage Examples](#usage-examples)

---

## Core Principles

### 1. **Type Safety First**
All identifiers must be strongly typed classes to prevent misuse at compile time. Specialized identifiers like `Did` extend the base `Iri` class to maintain proper inheritance semantics.

### 2. **Validation at Construction**
Identifiers must validate their format during construction, not at usage time.

### 3. **Self-Contained Modules**
Each module owns and defines its identifiers, avoiding cross-module dependencies where possible.

### 4. **Clear Semantics**
Naming and structure must clearly distinguish between identifiers (identity references) and types (classifications).

### 5. **Zero Backward Compatibility**
No String-based identifier fields alongside typed identifiers. Direct migration to typed values.

---

## Identifiers vs Types

### **IDENTIFIERS** - Opaque Identity References

**Purpose**: Reference and identify resources uniquely.

**Characteristics**:
- Classes wrapping validated strings (regular classes to support inheritance)
- Extend base identifier classes when semantically appropriate (e.g., `Did extends Iri`, `CredentialId extends Iri`)
- Always validate format in `init` blocks
- Serialize as strings in JSON
- Examples: `Did` (extends `Iri`), `CredentialId` (extends `Iri`), `IssuerId` (extends `Iri`), `VerificationMethodId`, `KeyId`

**Usage Pattern**:
```kotlin
val did = Did("did:key:z6Mk...")                    // IDENTIFIER - which resource?
val credId = CredentialId("https://...")            // IDENTIFIER - which credential?
val iri: Iri = credId                                // Polymorphism: CredentialId IS-A Iri
```

### **TYPES** - Domain Classifications

**Purpose**: Classify and categorize concepts semantically.

**Characteristics**:
- Sealed classes or enums
- Represent categories, not identities
- Serialize as strings in JSON (category names)
- Examples: `ProofType`, `CredentialType`, `Algorithm`, `ContractStatus`

**Usage Pattern**:
```kotlin
val proofType = ProofType.Ed25519Signature2020   // TYPE - what kind of proof?
val credType = CredentialType.Education          // TYPE - what kind of credential?
```

### Comparison Table

| Aspect | **IDENTIFIERS** | **TYPES** |
|--------|----------------|-----------|
| Purpose | Reference/identify resources | Classify/categorize concepts |
| Structure | Value classes (`@JvmInline`) | Sealed classes / Enums |
| Package | `{module}.identifiers` | `{module}.types` |
| Examples | `Did`, `CredentialId`, `VerificationMethodId` | `ProofType`, `CredentialType`, `Algorithm` |
| Question Answered | "Which resource?" | "What kind?" |
| Validation | Format validation (IRI, DID pattern) | Value validation (allowed values) |
| Serialization | As strings (IRI/URI format) | As strings (category names) |

---

## Architecture

### Module Structure

```
common/
  └── identifiers/
      └── Identifiers.kt          (Iri, KeyId - base identifiers)

did/did-core/
  └── identifiers/
      └── DidIdentifiers.kt       (Did, VerificationMethodId, DidUrl)

credentials/credential-core/
  └── identifiers/
      └── CredentialIdentifiers.kt (CredentialId, IssuerId, StatusListId, SchemaId)
  └── types/
      └── CredentialTypes.kt       (ProofType, CredentialType)
      └── SchemaFormat.kt          (SchemaFormat enum)
      └── StatusPurpose.kt         (StatusPurpose enum)

wallet/wallet-core/
  └── identifiers/
      └── WalletIdentifiers.kt     (WalletId, CollectionId)
  └── types/
      └── WalletType.kt            (WalletType sealed class)

kms/kms-core/
  └── identifiers/
      └── KmsIdentifiers.kt        (KeyId re-export)
  └── types/
      └── Algorithm.kt             (Algorithm sealed class)

contract/
  └── identifiers/
      └── ContractIdentifiers.kt   (ContractId if needed)
  └── types/
      └── ContractTypes.kt         (ContractType, ContractStatus, etc.)
```

### Dependency Flow

```
credentials/credential-core
    ↓ (depends on)
did/did-core
    ↓ (depends on)
common
```

**Key Rule**: `VerificationMethodId` belongs in `did-core` because:
- It's part of the DID Core specification
- Credentials consume it but don't define it
- This avoids circular dependencies

---

## Base Identifier: IRI

### Design Rationale

All identifiers in TrustWeave are Internationalized Resource Identifiers (IRIs) following RFC 3987. The base `Iri` class provides:
- Common validation for URI, URN, DID schemes
- Fragment parsing
- Scheme detection
- Foundation for specialized identifiers that extend it

**Important**: `Iri` is a regular class (not a value class) to allow inheritance. `Did` extends `Iri` because a DID IS-A IRI semantically. This maintains proper "IS-A" relationship semantics while allowing specialized validation for DIDs.

### Implementation

```kotlin
// common/src/main/kotlin/com/trustweave/core/identifiers/Identifiers.kt

package com.trustweave.core.identifiers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Base Internationalized Resource Identifier (IRI).
 * 
 * Foundation for all identifier classes. Identifiers are opaque identity references
 * following RFC 3987 (IRI specification).
 * 
 * **Supported Schemes:**
 * - `http://`, `https://` - URIs/URLs
 * - `did:` - Decentralized Identifiers
 * - `urn:` - Uniform Resource Names
 * - `#` - Fragment identifiers
 * 
 * **Note**: This is a regular class (not a value class) to allow other identifiers
 * like `Did` to extend it, maintaining "IS-A" relationship semantics.
 * 
 * **Example:**
 * ```kotlin
 * val iri = Iri("https://example.com/resource#fragment")
 * val did = Did.parse("did:key:z6Mk...")  // Did extends Iri
 * ```
 */
@Serializable(with = IriSerializer::class)
open class Iri(val value: String) {
    init {
        require(value.isNotBlank()) {
            "IRI cannot be blank"
        }
        require(isValidIri(value)) {
            "Invalid IRI format: '$value'"
        }
    }
    
    /**
     * Check if this IRI represents a URI/URL (starts with http:// or https://).
     */
    val isUri: Boolean
        get() = value.startsWith("http://") || value.startsWith("https://")
    
    /**
     * Check if this IRI represents a URN (starts with urn:).
     */
    val isUrn: Boolean
        get() = value.startsWith("urn:")
    
    /**
     * Check if this IRI represents a DID (starts with did:).
     */
    val isDid: Boolean
        get() = value.startsWith("did:")
    
    /**
     * Get the scheme of this IRI (e.g., "http", "https", "did", "urn").
     */
    val scheme: String
        get() = value.substringBefore(':')
    
    /**
     * Parse fragment from IRI if present (e.g., "frag" from "https://example.com#frag").
     */
    val fragment: String?
        get() = value.substringAfter("#", "")
            .takeIf { it.isNotEmpty() && value.contains("#") }
    
    /**
     * Get IRI without fragment.
     */
    val withoutFragment: Iri
        get() = Iri(value.substringBefore("#"))
    
    override fun toString(): String = value
    
    companion object {
        /**
         * Basic IRI validation - allows common schemes used in Web-of-Trust.
         */
        private fun isValidIri(value: String): Boolean {
            // Allow: http, https, did, urn, and other common schemes
            // Also allow relative IRIs (no scheme) for fragments like "#key-1"
            return value.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.*$")) ||
                   value.startsWith("#") ||
                   value.matches(Regex("^[a-zA-Z0-9._~-]+(/[a-zA-Z0-9._~-]*)*$"))
        }
    }
}

/**
 * Custom serializer for Iri that serializes as String in JSON.
 * Required because Iri has validation in init block.
 */
object IriSerializer : KSerializer<Iri> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("Iri", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: Iri) {
        encoder.encodeString(value.value)
    }
    
    override fun deserialize(decoder: Decoder): Iri {
        val string = decoder.decodeString()
        return try {
            Iri(string)
        } catch (e: IllegalArgumentException) {
            throw kotlinx.serialization.SerializationException(
                "Failed to deserialize Iri: ${e.message}",
                e
            )
        }
    }
}

/**
 * Key identifier fragment.
 * 
 * Used for relative key references (e.g., "#key-1" or "key-1").
 * Can be combined with a DID to form a full verification method ID.
 * 
 * **Example:**
 * ```kotlin
 * val keyId = KeyId("key-1")
 * val vmId = VerificationMethodId(did, keyId)  // "did:key:z6Mk...#key-1"
 * ```
 */
@Serializable(with = KeyIdSerializer::class)
@JvmInline
value class KeyId(val value: String) {
    init {
        require(value.isNotBlank()) {
            "Key ID cannot be blank"
        }
        require(!value.contains(" ") && !value.contains("\n")) {
            "Key ID cannot contain whitespace"
        }
    }
    
    /**
     * Check if this is a fragment identifier (starts with #).
     */
    val isFragment: Boolean
        get() = value.startsWith("#")
    
    /**
     * Get fragment value without #.
     */
    val fragmentValue: String
        get() = if (isFragment) value.substring(1) else value
    
    override fun toString(): String = value
}

object KeyIdSerializer : KSerializer<KeyId> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("KeyId", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: KeyId) {
        encoder.encodeString(value.value)
    }
    
    override fun deserialize(decoder: Decoder): KeyId {
        val string = decoder.decodeString()
        return try {
            KeyId(string)
        } catch (e: IllegalArgumentException) {
            throw kotlinx.serialization.SerializationException(
                "Failed to deserialize KeyId: ${e.message}",
                e
            )
        }
    }
}
```

---

## Module-Specific Identifiers

### DID Module Identifiers

**Location**: `did/did-core/src/main/kotlin/com/trustweave/did/identifiers/DidIdentifiers.kt`

```kotlin
package com.trustweave.did.identifiers

import com.trustweave.core.identifiers.Iri
import com.trustweave.core.identifiers.KeyId

/**
 * Decentralized Identifier (DID).
 * 
 * Extends Iri with DID-specific validation and parsing.
 * Follows W3C DID Core specification: did:method:identifier
 * 
 * **Inheritance**: `Did extends Iri` - a DID IS-A IRI.
 */
@Serializable(with = DidSerializer::class)
class Did(
    value: String
) : Iri(value.substringBefore("#")), Comparable<Did> {  // Remove fragment, Iri validates format
    
    init {
        require(value.startsWith("did:")) {
            "Invalid DID format: '$value'. DIDs must start with 'did:'"
        }
        require(value.split(":").size >= 3) {
            "Invalid DID format: '$value'. Expected format: did:method:identifier"
        }
    }
    
    val method: String
        get() {
            val parts = this.value.substringAfter("did:").split(":", limit = 2)
            return parts.firstOrNull() ?: throw IllegalStateException("Invalid DID: ${this.value}")
        }
    
    val identifier: String
        get() = this.value.substringAfter("did:$method:")
    
    val path: String?
        get() {
            val parts = this.value.split("/", limit = 2)
            return parts.getOrNull(1)
        }
    
    val baseDid: Did
        get() = Did(this.value.substringBefore("/"))
    
    override fun toString(): String = value
    
    /**
     * Operator: did + "fragment" creates VerificationMethodId.
     */
    operator fun plus(fragment: String): VerificationMethodId {
        val keyId = if (fragment.startsWith("#")) KeyId(fragment) else KeyId("#$fragment")
        return VerificationMethodId(this, keyId)
    }
    
    /**
     * Infix: did with "fragment" - more readable alternative.
     */
    infix fun with(fragment: String): VerificationMethodId = this + fragment
    
    /**
     * Infix: did with keyId - type-safe alternative.
     */
    infix fun with(keyId: KeyId): VerificationMethodId = VerificationMethodId(this, keyId)
    
    /**
     * Infix: Check if DID belongs to method.
     */
    infix fun isMethod(method: String): Boolean = this.method == method
    
    /**
     * Comparable: Natural ordering for sorting.
     */
    override fun compareTo(other: Did): Int = value.compareTo(other.value)
}

/**
 * Full verification method identifier.
 * 
 * Combines a DID with a key ID fragment: "did:key:z6Mk...#key-1"
 */
@Serializable(with = VerificationMethodIdSerializer::class)
data class VerificationMethodId(
    val did: Did,
    val keyId: KeyId
) {
    val value: String
        get() {
            val fragment = if (keyId.isFragment) keyId.value else "#${keyId.value}"
            return "${did.value}$fragment"
        }
    
    override fun toString(): String = value
    
    companion object {
        /**
         * Parse a verification method ID string.
         * 
         * Handles both full IDs ("did:key:z6Mk...#key-1") and relative IDs ("#key-1" when did is known).
         */
        fun parse(vmIdString: String, baseDid: Did? = null): VerificationMethodId {
            return when {
                vmIdString.startsWith("did:") -> {
                    val parts = vmIdString.split("#", limit = 2)
                    if (parts.size != 2) {
                        throw IllegalArgumentException(
                            "VerificationMethodId must contain '#' fragment: '$vmIdString'"
                        )
                    }
                    VerificationMethodId(
                        did = Did(parts[0]),
                        keyId = KeyId("#${parts[1]}")
                    )
                }
                vmIdString.startsWith("#") && baseDid != null -> {
                    VerificationMethodId(baseDid, KeyId(vmIdString))
                }
                else -> throw IllegalArgumentException(
                    "Cannot parse VerificationMethodId: '$vmIdString'. " +
                    "Must be full DID URL (did:...:#...) or fragment (#...) with baseDid"
                )
            }
        }
    }
    
    /**
     * Decompose into components for destructuring.
     */
    fun decompose(): Pair<Did, KeyId> = did to keyId
}
```

### Credential Module Identifiers

**Location**: `credentials/credential-core/src/main/kotlin/com/trustweave/credential/identifiers/CredentialIdentifiers.kt`

```kotlin
package com.trustweave.credential.identifiers

import com.trustweave.core.identifiers.Iri
import com.trustweave.did.identifiers.Did

/**
 * Credential identifier (URI, URN, DID, or other IRI).
 * 
 * Extends Iri with credential-specific semantics.
 * Per W3C VC specification, credential IDs are URIs/IRIs, so CredentialId IS-A Iri.
 * 
 * **Inheritance**: `CredentialId extends Iri` - a credential ID IS-A IRI.
 */
@Serializable(with = CredentialIdSerializer::class)
class CredentialId(
    value: String
) : Iri(value) {
    /**
     * Try to parse as Did (since both CredentialId and Did extend Iri).
     */
    fun asDid(): Did? = if (isDid) try { Did(value) } catch (e: IllegalArgumentException) { null } else null
    
    override fun toString(): String = value
}

/**
 * Credential issuer identifier (DID or URI).
 * 
 * Extends Iri with issuer-specific semantics.
 * Per W3C VC specification, issuers are URIs or DIDs, so IssuerId IS-A Iri.
 * 
 * **Inheritance**: `IssuerId extends Iri` - an issuer ID IS-A IRI.
 */
@Serializable(with = IssuerIdSerializer::class)
class IssuerId(
    value: String
) : Iri(value) {
    /**
     * Try to parse as Did (since both IssuerId and Did extend Iri).
     */
    fun asDid(): Did? = if (isDid) try { Did(value) } catch (e: IllegalArgumentException) { null } else null
    
    override fun toString(): String = value
    
    companion object {
        fun fromDid(did: Did): IssuerId {
            // Did extends Iri, so we can use did.value directly
            return IssuerId(did.value)
        }
    }
}

/**
 * Credential status list identifier.
 * 
 * Extends Iri with status list-specific semantics.
 * Status list IDs are URIs/IRIs, so StatusListId IS-A Iri.
 * 
 * **Inheritance**: `StatusListId extends Iri` - a status list ID IS-A IRI.
 */
@Serializable(with = StatusListIdSerializer::class)
class StatusListId(
    value: String
) : Iri(value) {
    override fun toString(): String = value
}

/**
 * Credential schema identifier.
 * 
 * Extends Iri with schema-specific semantics.
 * Schema IDs are URIs/IRIs, so SchemaId IS-A Iri.
 * 
 * **Inheritance**: `SchemaId extends Iri` - a schema ID IS-A IRI.
 */
@Serializable(with = SchemaIdSerializer::class)
class SchemaId(
    value: String
) : Iri(value) {
    override fun toString(): String = value
}
```

---

## Domain Types

### Credential Types

**Location**: `credentials/credential-core/src/main/kotlin/com/trustweave/credential/types/CredentialTypes.kt`

```kotlin
package com.trustweave.credential.types

/**
 * Cryptographic proof/signature type classification.
 * 
 * Represents the TYPE of proof used in verifiable credentials.
 * This is a classification, not an identifier.
 */
@Serializable(with = ProofTypeSerializer::class)
sealed class ProofType {
    abstract val identifier: String
    
    object Ed25519Signature2020 : ProofType() {
        override val identifier = "Ed25519Signature2020"
    }
    
    object JsonWebSignature2020 : ProofType() {
        override val identifier = "JsonWebSignature2020"
    }
    
    object BbsBlsSignature2020 : ProofType() {
        override val identifier = "BbsBlsSignature2020"
    }
    
    data class Custom(override val identifier: String) : ProofType()
    
    override fun toString(): String = identifier
}

/**
 * Credential type classification.
 * 
 * Represents the TYPE/CATEGORY of verifiable credential.
 */
@Serializable(with = CredentialTypeSerializer::class)
sealed class CredentialType {
    abstract val value: String
    
    object VerifiableCredential : CredentialType() {
        override val value = "VerifiableCredential"
    }
    
    object Education : CredentialType() {
        override val value = "EducationCredential"
    }
    
    // ... other types
    
    data class Custom(override val value: String) : CredentialType()
    
    override fun toString(): String = value
}
```

### Simple Enums

For simple enumerations, automatic serialization works fine:

```kotlin
@Serializable
enum class StatusPurpose {
    REVOCATION,
    SUSPENSION
}

@Serializable
enum class SchemaFormat {
    JSON_SCHEMA,
    SHACL
}
```

---

## Serialization Strategy

### When Custom Serializers Are Required

**YES - Required for:**
1. Value classes with validation in `init` blocks
   - Reason: Validation exceptions need better error messages
   - Example: `Iri`, `Did`, `CredentialId`
2. Sealed classes with explicit string-to-instance mapping
   - Reason: Custom parsing logic
   - Example: `ProofType`, `CredentialType`

**NO - Not Required for:**
1. Simple enums
   - Reason: Automatic serialization works perfectly
   - Example: `StatusPurpose`, `SchemaFormat`

### Constructor vs Factory Methods

**Key Design Decision**: Use direct constructors instead of factory methods like `parse()`.

**Rationale**:
- ✅ **Simpler API**: `Did("did:key:...")` is more concise than `Did.parse("did:key:...")`
- ✅ **Validation in constructor**: All validation happens in `init` blocks, so constructor is sufficient
- ✅ **Consistent with Kotlin idioms**: Direct constructors are the standard approach
- ✅ **Matches existing codebase**: Current implementation uses direct constructors
- ✅ **Less API surface**: Fewer methods to maintain and document

**For safe parsing** (when you need nullable results), use:
```kotlin
// Option 1: try-catch
val did = try { Did("did:key:...") } catch (e: IllegalArgumentException) { null }

// Option 2: Extension function (if desired)
fun String.toDidOrNull(): Did? = try { Did(this) } catch (e: IllegalArgumentException) { null }
val did = "did:key:...".toDidOrNull()
```

### Inheritance Model

**Key Design Decision**: All IRI-based identifiers extend `Iri` (inheritance) rather than composing it.

**Rationale**:
- ✅ **True "IS-A" relationship**: These identifiers ARE IRIs semantically
- ✅ **Polymorphism**: All identifier instances can be used wherever `Iri` is expected
- ✅ **Shared behavior**: Inherit all `Iri` methods (fragment parsing, scheme detection, etc.)
- ✅ **Type safety**: Compiler enforces the relationship
- ✅ **Consistency**: Uniform approach across all IRI-based identifiers

**Identifiers that extend Iri**:
- `Did extends Iri` - A DID IS-A IRI (W3C DID Core spec)
- `CredentialId extends Iri` - A credential ID IS-A IRI (W3C VC spec)
- `IssuerId extends Iri` - An issuer ID IS-A IRI (W3C VC spec)
- `StatusListId extends Iri` - A status list ID IS-A IRI
- `SchemaId extends Iri` - A schema ID IS-A IRI

**Example**:
```kotlin
val did = Did("did:key:z6Mk...")
val credId = CredentialId("https://example.com/cred/123")
val issuerId = IssuerId.fromDid(did)

// All can be used as Iri (polymorphism)
val iri1: Iri = did       // ✅ Valid: Did IS-A Iri
val iri2: Iri = credId    // ✅ Valid: CredentialId IS-A Iri
val iri3: Iri = issuerId  // ✅ Valid: IssuerId IS-A Iri
```

**Trade-off**: `Iri` must be a regular class (not `@JvmInline` value class) to allow inheritance. This is acceptable because:
- The inheritance relationship is semantically correct per W3C specifications
- The performance difference is minimal for identifier classes
- Polymorphism benefits outweigh the slight performance cost
- Consistency across all IRI-based identifiers simplifies the API

### Serializer Pattern

All custom serializers follow this pattern:

```kotlin
object IdentifierSerializer : KSerializer<Identifier> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("Identifier", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: Identifier) {
        encoder.encodeString(value.value)
    }
    
    override fun deserialize(decoder: Decoder): Identifier {
        val string = decoder.decodeString()
        return try {
            Identifier(string)  // Constructor validates
        } catch (e: IllegalArgumentException) {
            throw SerializationException(
                "Failed to deserialize Identifier: ${e.message}",
                e
            )
        }
    }
}
```

### JSON Representation

**Identifiers** serialize as strings:
```json
{
  "id": "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
  "issuer": "did:key:z6Mk...",
  "verificationMethod": "did:key:z6Mk...#key-1"
}
```

**Types** serialize as strings (category names):
```json
{
  "type": ["VerifiableCredential", "EducationCredential"],
  "proof": {
    "type": "Ed25519Signature2020"
  }
}
```

---

## Type Safety & Validation

### Compile-Time Safety

Value classes provide compile-time type safety:

```kotlin
// ✅ Type-safe - compiler prevents misuse
fun verifyCredential(
    credentialId: CredentialId,      // Can't pass Did by mistake
    issuerDid: Did,                  // Can't pass CredentialId by mistake
    vmId: VerificationMethodId       // Clear intent
)

// ❌ This won't compile
verifyCredential(
    did,          // Error: Expected CredentialId, found Did
    credId,       // Error: Expected Did, found CredentialId
    "string"      // Error: Expected VerificationMethodId, found String
)
```

### Runtime Validation

All identifiers validate during construction:

```kotlin
// ✅ Valid - succeeds
val did = Did("did:key:z6Mk...")

// ❌ Invalid - throws IllegalArgumentException
val invalid = Did("not-a-did")
// Error: Invalid DID format: 'not-a-did'. DIDs must start with 'did:'
```

### Validation Rules

| Identifier | Validation Rules |
|------------|-----------------|
| `Iri` | Non-blank, valid IRI pattern (scheme:value or fragment) |
| `Did` | Must start with "did:", at least 3 colon-separated parts |
| `VerificationMethodId` | Must contain DID + fragment separator (#) |
| `CredentialId` | Valid IRI (inherited from Iri) |
| `KeyId` | Non-blank, no whitespace |

---

## Package Organization

### Package Naming Convention

**Identifiers**: `com.trustweave.{module}.identifiers`
- Example: `com.trustweave.credential.identifiers.CredentialId`
- Example: `com.trustweave.did.identifiers.Did`

**Types**: `com.trustweave.{module}.types`
- Example: `com.trustweave.credential.types.ProofType`
- Example: `com.trustweave.kms.types.Algorithm`

### File Organization

**One file per module for identifiers:**
```
credentials/credential-core/src/main/kotlin/com/trustweave/credential/
  └── identifiers/
      └── CredentialIdentifiers.kt  (all credential identifiers)
  └── types/
      └── CredentialTypes.kt        (all credential types)
      └── SchemaFormat.kt           (enum)
      └── StatusPurpose.kt          (enum)
```

**Rationale**: 
- Easy to find all identifiers/types for a module
- Clear ownership
- Reduces file proliferation

---

## Migration Strategy

### Phase 1: Create Base Infrastructure
1. Create `Iri` and `KeyId` in `common/identifiers/`
2. Add custom serializers
3. Ensure tests pass

### Phase 2: Module-Specific Identifiers
1. Create `DidIdentifiers.kt` in `did-core`
2. Create `CredentialIdentifiers.kt` in `credential-core`
3. Create identifier files for other modules

### Phase 3: Update Models
1. Replace all `String` identifier fields with typed identifiers
2. Update all `String` type fields with typed types
3. Update serialization code
4. Update all usages across codebase

### Phase 4: Remove Duplicates
1. Remove old identifier definitions
2. Consolidate from multiple locations
3. Update imports

### Phase 5: Update Public API
1. Update all public functions to use typed parameters
2. Update documentation
3. Remove deprecated String-based methods

---

## Usage Examples

### Creating Identifiers

```kotlin
// ✅ Creating identifiers (direct constructors with validation)
val did = Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
// All identifiers extend Iri, so they can be used wherever Iri is expected
val iri1: Iri = did  // ✅ Valid: Did IS-A Iri
val credId = CredentialId("https://example.com/credentials/123")
val iri2: Iri = credId  // ✅ Valid: CredentialId IS-A Iri
val issuerId = IssuerId.fromDid(did)
val iri3: Iri = issuerId  // ✅ Valid: IssuerId IS-A Iri
val vmId = did + "key-1"  // Operator: Creates VerificationMethodId
val vmId2 = did with "key-1"  // Infix: More readable alternative

// ✅ Safe parsing (using extension functions)
val didOrNull = "did:key:...".toDidOrNull()
val credIdOrNull = json.getString("id")?.toCredentialIdOrNull()

// ✅ Safe parsing (using try-catch if extension not available)
val didOrNull2 = try { Did("did:key:...") } catch (e: IllegalArgumentException) { null }
```

### Creating Types

```kotlin
// ✅ Creating types
val proofType = ProofType.Ed25519Signature2020
val credType = CredentialType.Education
val algorithm = Algorithm.Ed25519
val format = SchemaFormat.JSON_SCHEMA
val purpose = StatusPurpose.REVOCATION
```

### Using in Models

```kotlin
// ✅ Type-safe credential creation
val credential = VerifiableCredential(
    id = credId,              // IDENTIFIER
    type = listOf(            // TYPES
        CredentialType.VerifiableCredential,
        CredentialType.Education
    ),
    issuer = issuerId,        // IDENTIFIER
    credentialSubject = subjectJson,
    issuanceDate = Instant.now().toString(),
    proof = Proof(
        type = proofType,     // TYPE
        verificationMethod = vmId  // IDENTIFIER
    )
)

// ✅ Type-safe DID document
val document = DidDocument(
    id = did,                 // IDENTIFIER
    verificationMethod = listOf(
        VerificationMethod(
            id = vmId,        // IDENTIFIER
            type = "Ed25519VerificationKey2020",
            controller = did  // IDENTIFIER
        )
    ),
    assertionMethod = listOf(vmId)  // IDENTIFIER list
)
```

### Type-Safe Functions

```kotlin
// ✅ Clear intent in function signatures
fun issueCredential(
    issuerId: IssuerId,           // IDENTIFIER - who is issuing
    credentialType: CredentialType, // TYPE - what kind
    proofType: ProofType           // TYPE - what proof algorithm
): VerifiableCredential

fun verifyCredential(
    credentialId: CredentialId,    // IDENTIFIER - which credential
    issuerDid: Did,                // IDENTIFIER - which issuer
    vmId: VerificationMethodId     // IDENTIFIER - which key
): VerificationResult
```

---

## Benefits

### 1. **Compile-Time Safety**
- Prevents identifier misuse (e.g., passing DID where CredentialId expected)
- IDE autocompletion guides developers
- Type system enforces domain rules

### 2. **Runtime Validation**
- Invalid identifiers rejected at construction
- Clear error messages
- Consistent validation across codebase

### 3. **Domain Clarity**
- Identifiers clearly separate from types
- Self-documenting code
- Easy to find module-specific identifiers

### 4. **Maintainability**
- Single source of truth per module
- Easy to extend with new identifiers
- Clear package organization

### 5. **JSON Compatibility**
- Seamless serialization/deserialization
- Validates on deserialization
- Maintains W3C compliance

---

## API Ergonomic Enhancements

### 1. Extension Functions for Safe Parsing

Provide extension functions for null-safe identifier creation, aligning with Kotlin's idiomatic patterns:

```kotlin
// common/src/main/kotlin/com/trustweave/core/identifiers/Identifiers.kt (extensions)

/**
 * Safe parsing extension functions for all identifiers.
 * Returns null instead of throwing exceptions.
 */
inline fun String.toIriOrNull(): Iri? = 
    try { Iri(this) } catch (e: IllegalArgumentException) { null }

inline fun String.toKeyIdOrNull(): KeyId? = 
    try { KeyId(this) } catch (e: IllegalArgumentException) { null }

// did/did-core/src/main/kotlin/com/trustweave/did/identifiers/DidIdentifiersExtensions.kt
inline fun String.toDidOrNull(): Did? = 
    try { Did(this) } catch (e: IllegalArgumentException) { null }

inline fun String.toVerificationMethodIdOrNull(baseDid: Did? = null): VerificationMethodId? = 
    try { VerificationMethodId.parse(this, baseDid) } catch (e: IllegalArgumentException) { null }

// credentials/credential-core/.../CredentialIdentifiersExtensions.kt
inline fun String.toCredentialIdOrNull(): CredentialId? = 
    try { CredentialId(this) } catch (e: IllegalArgumentException) { null }

inline fun String.toIssuerIdOrNull(): IssuerId? = 
    try { IssuerId(this) } catch (e: IllegalArgumentException) { null }

inline fun String.toStatusListIdOrNull(): StatusListId? = 
    try { StatusListId(this) } catch (e: IllegalArgumentException) { null }

inline fun String.toSchemaIdOrNull(): SchemaId? = 
    try { SchemaId(this) } catch (e: IllegalArgumentException) { null }
```

**Usage**:
```kotlin
val did = "did:key:z6Mk...".toDidOrNull()  // Returns Did? instead of throwing
val credId = json.getString("id").toCredentialIdOrNull()

// Chain with null-safety
json.getString("issuer")?.toIssuerIdOrNull()?.let { issuer ->
    // Use issuer
}
```

### 2. Result-Based Parsing (Alternative Pattern)

For codebases using `Result<T>` extensively, provide Result-returning parsers:

```kotlin
/**
 * Result-based parsing for identifiers.
 * Returns Result<T> instead of throwing or returning null.
 */
inline fun String.toDid(): Result<Did> = runCatching { Did(this) }
inline fun String.toCredentialId(): Result<CredentialId> = runCatching { CredentialId(this) }
inline fun String.toIri(): Result<Iri> = runCatching { Iri(this) }

// Usage
val didResult = "did:key:...".toDid()
    .onSuccess { did -> println("Parsed: $did") }
    .onFailure { e -> println("Error: ${e.message}") }
```

### 3. Enhanced Operator Overloading

Extend operator overloading for more natural syntax:

```kotlin
// DidIdentifiers.kt
class Did(value: String) : Iri(value.substringBefore("#")) {
    // Existing: operator fun plus(fragment: String)
    
    // ✅ Add: Infix function for cleaner syntax
    infix fun with(keyId: KeyId): VerificationMethodId = VerificationMethodId(this, keyId)
    infix fun with(fragment: String): VerificationMethodId = this + fragment
    
    // ✅ Add: Comparison operators (for sorting, equality)
    // Note: Already inherited equals/hashCode from Any, but can add Comparable
}

// ✅ Make identifiers Comparable for natural sorting
class Did(value: String) : Iri(value.substringBefore("#")), Comparable<Did> {
    override fun compareTo(other: Did): Int = value.compareTo(other.value)
}
```

**Usage**:
```kotlin
// Before: did + "key-1"
// After (both work):
val vmId1 = did + "key-1"
val vmId2 = did with "key-1"      // Infix - more readable
val vmId3 = did with KeyId("key-1")

// Sorting
val sorted = listOf(did1, did2, did3).sorted()  // Natural order
```

### 4. Smart Type Conversions

Add extension functions for safe type narrowing:

```kotlin
// IriExtensions.kt

/**
 * Smart cast helpers - safely narrow Iri to specific types.
 */
fun Iri.asDidOrNull(): Did? = if (isDid) try { Did(value) } catch (e: IllegalArgumentException) { null } else null
fun Iri.asCredentialIdOrNull(): CredentialId? = try { CredentialId(value) } catch (e: IllegalArgumentException) { null }
fun Iri.asIssuerIdOrNull(): IssuerId? = try { IssuerId(value) } catch (e: IllegalArgumentException) { null }

/**
 * Require specific type - throws with clear error if conversion fails.
 */
fun Iri.requireDid(): Did = asDidOrNull() 
    ?: throw IllegalArgumentException("IRI '$value' is not a valid DID")

fun Iri.requireCredentialId(): CredentialId = asCredentialIdOrNull()
    ?: throw IllegalArgumentException("IRI '$value' is not a valid CredentialId")
```

**Usage**:
```kotlin
val iri: Iri = someOperation()
val did = iri.asDidOrNull()  // Safe
val did2 = iri.requireDid()   // Throws if not DID - clear intent
```

### 5. Collection Extensions

Add useful extensions for collections of identifiers:

```kotlin
// IdentifierCollectionExtensions.kt

/**
 * Extension functions for collections of identifiers.
 */

// Safe parsing from string collections
fun List<String>.mapToDidOrNull(): List<Did> = mapNotNull { it.toDidOrNull() }
fun List<String>.mapToCredentialIdOrNull(): List<CredentialId> = mapNotNull { it.toCredentialIdOrNull() }

// Filter by type (polymorphism at work)
fun List<Iri>.filterDids(): List<Did> = mapNotNull { it.asDidOrNull() }
fun List<Iri>.filterCredentialIds(): List<CredentialId> = mapNotNull { it.asCredentialIdOrNull() }

// Convert between types
fun List<Did>.toIris(): List<Iri> = this  // Natural conversion

// Find operations
fun List<Did>.findByMethod(method: String): Did? = firstOrNull { it.method == method }
fun List<CredentialId>.findByIssuer(issuer: Did): CredentialId? = 
    firstOrNull { it.asDidOrNull() == issuer }

// Validation helpers
fun List<Did>.allValid(): Boolean = all { it.value.startsWith("did:") }
fun List<Iri>.anyInvalid(): Boolean = any { try { Iri(it.value) } catch (e: Exception) { true } }
```

**Usage**:
```kotlin
val didStrings = listOf("did:key:...", "did:web:...")
val dids = didStrings.mapToDidOrNull()  // Filters out invalid ones
val keyDids = dids.filter { it.method == "key" }

val allIris: List<Iri> = listOf(did1, credId1, issuerId1)
val justDids = allIris.filterDids()  // Extract only DIDs
```

### 6. Destructuring Declarators

Add destructuring for composite identifiers:

```kotlin
// VerificationMethodId
data class VerificationMethodId(
    val did: Did,
    val keyId: KeyId
) {
    // Kotlin automatically provides component1(), component2() for data classes
    // So destructuring just works:
    // val (did, keyId) = vmId
}

// For Did (if we add properties we want to destructure)
class Did(value: String) : Iri(value.substringBefore("#")) {
    // Could add if useful:
    // operator fun component1(): String = method
    // operator fun component2(): String = identifier
}
```

**Usage**:
```kotlin
val vmId = VerificationMethodId(did, KeyId("key-1"))
val (did, keyId) = vmId  // Destructuring works automatically for data classes
```

### 7. String Interpolation Support

Make identifiers work seamlessly in string templates:

```kotlin
// Already works via toString(), but we can document and enhance:
class Did(value: String) : Iri(value.substringBefore("#")) {
    override fun toString(): String = value  // ✅ Already implemented
    
    // Could add formatted variants if needed:
    // fun toShortString(): String = "did:$method:${identifier.take(8)}..."
}

// Usage in string interpolation
val message = "Verifying credential issued by ${did}"  // ✅ Works automatically
val logEntry = "DID: ${did.value}, Method: ${did.method}"  // ✅ Full access
```

### 8. Type-Safe Conversion Helpers

Add companion object factory methods for common conversions:

```kotlin
class IssuerId(value: String) : Iri(value) {
    companion object {
        /**
         * Create IssuerId from Did (common case).
         * More ergonomic than: IssuerId(did.value)
         */
        fun fromDid(did: Did): IssuerId = IssuerId(did.value)
        
        /**
         * Create IssuerId from Iri (if needed).
         */
        fun fromIri(iri: Iri): IssuerId = IssuerId(iri.value)
    }
}

class CredentialId(value: String) : Iri(value) {
    companion object {
        /**
         * Create from Iri (useful when you have an Iri and want to narrow it).
         */
        fun fromIri(iri: Iri): CredentialId = CredentialId(iri.value)
        
        /**
         * Create from Did (when credential ID is a DID).
         */
        fun fromDid(did: Did): CredentialId = CredentialId(did.value)
    }
}
```

### 9. Enhanced VerificationMethodId API

Improve `VerificationMethodId` ergonomics:

```kotlin
data class VerificationMethodId(
    val did: Did,
    val keyId: KeyId
) {
    val value: String get() = "${did.value}${if (keyId.isFragment) keyId.value else "#${keyId.value}"}"
    
    override fun toString(): String = value
    
    // ✅ Add: Decompose into components
    fun decompose(): Pair<Did, KeyId> = did to keyId
    
    // ✅ Add: Factory from string with better error messages
    companion object {
        fun parse(vmIdString: String, baseDid: Did? = null): VerificationMethodId {
            return when {
                vmIdString.startsWith("did:") -> {
                    val parts = vmIdString.split("#", limit = 2)
                    if (parts.size != 2) {
                        throw IllegalArgumentException(
                            "VerificationMethodId must contain '#' fragment: '$vmIdString'"
                        )
                    }
                    VerificationMethodId(
                        did = Did(parts[0]),
                        keyId = KeyId("#${parts[1]}")
                    )
                }
                vmIdString.startsWith("#") && baseDid != null -> {
                    VerificationMethodId(baseDid, KeyId(vmIdString))
                }
                else -> throw IllegalArgumentException(
                    "Cannot parse VerificationMethodId: '$vmIdString'. " +
                    "Must be full DID URL (did:...:#...) or fragment (#...) with baseDid"
                )
            }
        }
    }
}
```

### 10. Infix Functions for Fluent Operations

Add infix functions for common operations:

```kotlin
// DidIdentifiersExtensions.kt

/**
 * Infix function: "did with fragment" - more readable than operator plus
 */
infix fun Did.with(fragment: String): VerificationMethodId = this + fragment
infix fun Did.with(keyId: KeyId): VerificationMethodId = VerificationMethodId(this, keyId)

/**
 * Infix function: Check if DID belongs to method
 */
infix fun Did.isMethod(method: String): Boolean = this.method == method

// Usage
val vmId = did with "key-1"  // More readable
if (did isMethod "key") { ... }  // Natural language
```

### 11. Scope Functions Integration

Design identifiers to work well with Kotlin scope functions:

```kotlin
// ✅ Already works well:
val did = Did("did:key:...")
    .also { println("Created: $it") }
    .let { it + "key-1" }  // Create VerificationMethodId
    .takeIf { it.keyId.fragmentValue.startsWith("key") }
```

### 12. Type-Safe Builder Pattern for Complex Identifiers

For composite identifiers, consider builder pattern:

```kotlin
// Only if VerificationMethodId construction gets complex
object VerificationMethodId {
    inline fun build(block: Builder.() -> Unit): VerificationMethodId {
        val builder = Builder()
        builder.block()
        return builder.build()
    }
    
    class Builder {
        private var did: Did? = null
        private var keyId: KeyId? = null
        
        fun did(value: Did) { this.did = value }
        fun keyId(value: KeyId) { this.keyId = value }
        
        fun build(): VerificationMethodId {
            val d = did ?: throw IllegalStateException("Did required")
            val k = keyId ?: throw IllegalStateException("KeyId required")
            return VerificationMethodId(d, k)
        }
    }
}

// Usage (only if needed, simpler API preferred):
val vmId = VerificationMethodId.build {
    did = Did("did:key:...")
    keyId = KeyId("key-1")
}
```

**Note**: Prefer simple constructors/data classes unless complexity warrants builders.

---

## Enhanced Design Summary

### Key Enhancements Applied:

1. ✅ **Extension Functions**: `String.toDidOrNull()`, `String.toCredentialIdOrNull()`
2. ✅ **Infix Functions**: `did with "key-1"` for readability
3. ✅ **Comparable Interface**: Natural sorting for identifiers
4. ✅ **Type Narrowing**: `iri.asDidOrNull()`, `iri.requireDid()`
5. ✅ **Collection Extensions**: `list.mapToDidOrNull()`, `list.filterDids()`
6. ✅ **Smart Conversions**: Factory methods in companion objects
7. ✅ **Destructuring**: Automatic for data classes
8. ✅ **Better Error Messages**: Descriptive exceptions

### Recommended Implementation Order:

1. **Phase 1** (Must Have): Extension functions for safe parsing
2. **Phase 2** (Should Have): Comparable interface, infix functions
3. **Phase 3** (Nice to Have): Collection extensions, type narrowing helpers
4. **Phase 4** (Future): Result-based parsing if adopted across codebase

---

## Future Considerations

### Potential Extensions

1. **Identifier Resolution**
   - Add resolution helpers (e.g., `CredentialId.resolve()`)
   - Cache resolved identifiers

2. **Identifier Normalization**
   - Add normalization methods (e.g., case-insensitive DID comparison)
   - Support canonical forms

3. **Composite Identifiers**
   - Support complex identifier patterns
   - Multi-part identifiers

4. **Identifier Validation Levels**
   - Strict vs. lenient validation modes
   - Configurable validation rules

---

## References

- [W3C DID Core Specification](https://www.w3.org/TR/did-core/)
- [W3C Verifiable Credentials Data Model](https://www.w3.org/TR/vc-data-model/)
- [RFC 3987 - Internationalized Resource Identifiers (IRIs)](https://tools.ietf.org/html/rfc3987)
- [Kotlin Value Classes](https://kotlinlang.org/docs/inline-classes.html)
- [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization)

---

## Document History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 1.0 | 2024 | Initial design specification | Architecture Team |

---

**Status**: ✅ Approved for Implementation  
**Next Steps**: Begin Phase 1 implementation (Base Infrastructure)

---

## API Design Excellence: Quick Reference

### ✅ Core Design Principles Applied

| Principle | Implementation |
|-----------|---------------|
| **Type Safety** | All identifiers are strongly typed classes, no String leakage |
| **Validation** | Format validation in constructors, clear error messages |
| **Inheritance** | Proper "IS-A" relationships (`Did extends Iri`) |
| **Polymorphism** | Identifiers can be used as base types (`Iri`) |
| **Simplicity** | Direct constructors, no unnecessary factory methods |
| **Extensibility** | Extension functions for ergonomic operations |
| **Discoverability** | Infix functions, operator overloading, clear naming |

### ✅ Ergonomic Patterns

```kotlin
// ✅ Direct construction (simple, idiomatic)
val did = Did("did:key:z6Mk...")

// ✅ Safe parsing (null-safe)
val did = "did:key:...".toDidOrNull()

// ✅ Infix functions (readable)
val vmId = did with "key-1"

// ✅ Operator overloading (concise)
val vmId = did + "key-1"

// ✅ Type narrowing (polymorphism)
val iri: Iri = did
val did2 = iri.asDidOrNull()

// ✅ Natural sorting (Comparable)
val sorted = listOf(did1, did2, did3).sorted()

// ✅ Collection operations
val dids = stringList.mapToDidOrNull()
val keyDids = dids.filter { it isMethod "key" }

// ✅ Destructuring (data classes)
val (did, keyId) = vmId

// ✅ Type-safe conversions
val issuerId = IssuerId.fromDid(did)
```

### ✅ Key Enhancements Summary

1. **Extension Functions**: `String.toXxxOrNull()` for all identifiers
2. **Infix Functions**: `did with "fragment"` for readability  
3. **Comparable Interface**: Natural sorting support
4. **Type Narrowing**: `iri.asDidOrNull()`, `iri.requireDid()`
5. **Collection Extensions**: `list.mapToDidOrNull()`, `list.filterDids()`
6. **Smart Conversions**: Companion object factory methods
7. **Better Errors**: Descriptive validation messages
8. **Polymorphism**: All IRI-based identifiers extend `Iri`

### ✅ What Makes This API Excellent

- **Self-Documenting**: Function signatures express intent clearly
- **Type-Safe**: Compiler prevents common mistakes
- **Ergonomic**: Extension functions make common operations concise
- **Discoverable**: IDE autocompletion guides developers
- **Consistent**: Uniform patterns across all identifiers
- **Minimal**: No unnecessary abstraction layers
- **Kotlin-Idiomatic**: Leverages language features effectively

