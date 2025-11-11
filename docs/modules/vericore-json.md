# vericore-json

The `vericore-json` module provides JSON canonicalization and digest computation utilities.

Add it to your build when you need deterministic hashing:

```kotlin
dependencies {
    implementation("com.geoknoesis.vericore:vericore-json:1.0.0-SNAPSHOT")
}
```

**Result:** Gradle pulls in the canonicalisation helpers so calls such as `DigestUtils.sha256DigestMultibase` are available in your project.

## Overview

This module provides functions for:
- Canonicalizing JSON (stable key ordering)
- Computing SHA-256 digests with multibase encoding
- Handling nested JSON structures

## Key Components

### DigestUtils

Main utility object for JSON operations.

#### canonicalizeJson

Canonicalizes a JSON string by ensuring stable key ordering.

```kotlin
fun canonicalizeJson(json: String): String
```

**Example:**
```kotlin
val json1 = """{"b":2,"a":1}"""
val json2 = """{"a":1,"b":2}"""

val canonical1 = DigestUtils.canonicalizeJson(json1)
val canonical2 = DigestUtils.canonicalizeJson(json2)

// Both produce: """{"a":1,"b":2}"""
assertEquals(canonical1, canonical2)
```

#### canonicalizeJsonElement

Canonicalizes a `JsonElement` by ensuring stable key ordering.

```kotlin
fun canonicalizeJsonElement(element: JsonElement): String
```

**Example:**
```kotlin
val element = buildJsonObject {
    put("b", 2)
    put("a", 1)
}

val canonical = DigestUtils.canonicalizeJsonElement(element)
// Produces: """{"a":1,"b":2}"""
```

#### sha256DigestMultibase

Computes SHA-256 digest with multibase encoding (base58btc).

```kotlin
fun sha256DigestMultibase(data: String): String
fun sha256DigestMultibase(element: JsonElement): String
```

**Example:**
```kotlin
val json = buildJsonObject {
    put("vcId", "vc-12345")
    put("issuer", "did:web:example.com")
}

val digest = DigestUtils.sha256DigestMultibase(json)
// Produces: "uABC123..." (multibase-encoded digest)
```

## Features

### Automatic JSON Detection

When using `sha256DigestMultibase(String)`, the function automatically detects if the string is valid JSON and canonicalizes it:

```kotlin
// JSON string - automatically canonicalized
val digest1 = DigestUtils.sha256DigestMultibase("""{"b":2,"a":1}""")

// Non-JSON string - used as-is
val digest2 = DigestUtils.sha256DigestMultibase("plain text")
```

### Consistent Digests

Same content always produces the same digest, regardless of key order:

```kotlin
val json1 = """{"b":2,"a":1}"""
val json2 = """{"a":1,"b":2}"""

val digest1 = DigestUtils.sha256DigestMultibase(json1)
val digest2 = DigestUtils.sha256DigestMultibase(json2)

assertEquals(digest1, digest2) // Same digest!
```

### Nested Structures

Handles complex nested JSON objects and arrays:

```kotlin
val json = buildJsonObject {
    put("z", 1)
    put("a", buildJsonObject {
        put("c", 3)
        put("b", 2)
    })
}

val canonical = DigestUtils.canonicalizeJsonElement(json)
// Keys are sorted at all levels
```

## Multibase Encoding

Digests are encoded using multibase with base58btc encoding:

- **Prefix**: `u` (multibase code for base58btc)
- **Alphabet**: Bitcoin alphabet (no 0, O, I, l)
- **Format**: `u<base58-encoded-digest>`

## Usage Examples

### Computing a Digest

```kotlin
import com.geoknoesis.vericore.json.DigestUtils
import kotlinx.serialization.json.*

val vc = buildJsonObject {
    put("id", "vc-12345")
    put("issuer", "did:web:example.com")
    put("credentialSubject", buildJsonObject {
        put("id", "subject-123")
    })
}

val digest = DigestUtils.sha256DigestMultibase(vc)
println("Digest: $digest")
```