---
title: JSON Canonicalization
nav_exclude: true
---

# JSON Canonicalization

Canonicalization ensures that logically equivalent JSON payloads produce identical byte representations. This is essential for hashing, signing, and anchoring: if two parties serialize the same credential differently, their digests (and therefore proofs) would diverge.

```kotlin
dependencies {
    implementation("org.trustweave:trustweave-common:1.0.0-SNAPSHOT")
}
```

**Result:** Lets you use `DigestUtils` and canonicalisation helpers shown in this guide. Note: `DigestUtils` is now part of the `common` module in `org.trustweave.core.util` package.

## Why Canonicalize?

- **Stable hashes** – order-insensitive JSON (maps/objects) would otherwise produce different digests when keys are rearranged.
- **Signature verification** – the W3C Verifiable Credential data model requires deterministic serialization before signing and verification.
- **Interoperability** – canonicalization guarantees that independent SDKs agree on a canonical form.

## TrustWeave Canonicalization Pipeline

1. **Kotlinx Serialization** canonicalizes Kotlin data classes into JSON following your serializers.
2. **JCS (JSON Canonicalization Scheme)** sorts keys, removes insignificant whitespace, and normalizes numbers/Unicode.
3. The resulting canonical UTF-8 bytes feed into hashing (`sha256DigestMultibase`) or signing operations.

```kotlin
import org.trustweave.core.util.DigestUtils

val digest = DigestUtils.sha256DigestMultibase(jsonElement)
println("Canonical digest: $digest")
```

**Outcome:** Produces a deterministic multibase-encoded digest you can store alongside credentials, anchors, or proofs.

The helper accepts either a `JsonElement` or raw JSON string. Use it whenever you need a canonical digest for anchors, proofs, or ledger commits.

## Handling Edge Cases

- **Floating point** – numbers are emitted in their shortest canonical form (no trailing zeros, no `+` signs).
- **Unicode** – strings are normalized to NFC with escaped control characters as required by JCS.
- **Nulls** – `null` values are preserved; arrays maintain order.

## Best Practices

- Always canonicalize before hashing or signing external inputs.
- Store or log canonical digests alongside business identifiers to simplify audits.
- Avoid hand-crafted JSON manipulation when canonicalization helpers already exist—they enforce the required spec.

## Further Reading

- [Quick Start – Step 2](../getting-started/quick-start.md#step-2-bootstrap-TrustWeave-and-compute-a-digest)
- [W3C JSON Canonicalization Scheme](https://www.rfc-editor.org/rfc/rfc8785)
- [Digest Utilities API](../modules/trustweave-json.md) for more helpers.

