---
title: DIDComm Crypto Implementation Notes
---

# DIDComm Crypto Implementation Notes

## Current Status: Placeholder Implementation

The current `DidCommCrypto` class has **placeholder implementations** for the core cryptographic operations. This means the code structure is correct, but the actual cryptographic calculations are not implemented.

## What's Missing

### 1. ECDH-1PU Key Agreement (Lines 260-287)

**Current (Placeholder):**
```kotlin
private suspend fun performEcdh1puKeyAgreement(...): ByteArray {
    // Simplified ECDH-1PU implementation
    // In production, use a proper DIDComm library
    // This is a placeholder that demonstrates the structure

    // For now, return a placeholder shared secret
    return ByteArray(32) // ❌ This is just dummy data!
}
```

**What It Should Do:**
1. Load the private key from KMS (using the `keyId`)
2. Convert JWK public keys to `ECPublicKey` objects
3. Perform **ECDH-1PU** key agreement:
   - ECDH with sender's public key: `ECDH(sender_private, recipient_public)`
   - ECDH with ephemeral key: `ECDH(ephemeral_private, recipient_public)`
   - Combine using the ECDH-1PU algorithm (RFC 7748 + additional steps)
4. Return the actual shared secret (32 bytes for X25519/Ed25519)

**Why It's Complex:**
- ECDH-1PU is a specific variant that includes the sender's public key in the key agreement
- Requires proper curve point operations
- Must handle different curve types (Ed25519, secp256k1, P-256, etc.)
- Needs proper key derivation

### 2. Key Derivation (Lines 289-295)

**Current (Simplified):**
```kotlin
private fun deriveKeys(sharedSecret: ByteArray): Pair<ByteArray, ByteArray> {
    // Derive CEK and KEK using HKDF
    // Simplified: split the shared secret
    val cek = sharedSecret.sliceArray(0..15) // ❌ Just splitting bytes!
    val kek = sharedSecret.sliceArray(16..31)
    return Pair(cek, kek)
}
```

**What It Should Do:**
1. Use **HKDF** (HMAC-based Key Derivation Function) as specified in RFC 5869
2. Derive Content Encryption Key (CEK) - 32 bytes for AES-256
3. Derive Key Encryption Key (KEK) - 32 bytes for AES-256-KW
4. Use proper salt and info parameters as per DIDComm spec

**Why It Matters:**
- Simple byte splitting is **not cryptographically secure**
- HKDF ensures proper key derivation with entropy
- Required for interoperability with other DIDComm implementations

### 3. Private Key Access

**Current Issue:**
The code tries to use `kms.getPublicKey(keyId)` but never accesses the **private key**, which is needed for:
- ECDH key agreement (requires private key)
- Decryption (requires recipient's private key)

**What's Needed:**
- A way to get private keys from KMS (or store them separately)
- Proper key material handling
- Secure key storage and access

## What a Full Implementation Would Look Like

### Option 1: Use `didcomm-java` Library

The `didcomm-java` library (from `org.didcommx:didcomm`) provides:

```kotlin
// Add to build.gradle.kts
dependencies {
    implementation("org.didcommx:didcomm:0.3.2")
}

// Usage
import org.didcommx.didcomm.DIDComm
import org.didcommx.didcomm.message.Message
import org.didcommx.didcomm.pack.EncryptedPackedMessage

val didComm = DIDComm()

// Pack (encrypt) a message
val packed = didComm.pack(
    message = message,
    from = fromDid,
    to = listOf(toDid),
    signFrom = fromKeyId
)

// Unpack (decrypt) a message
val unpacked = didComm.unpack(
    packed = packedMessage,
    to = recipientDid,
    from = senderDid
)
```

**Benefits:**
- ✅ Fully tested and compliant
- ✅ Handles all edge cases
- ✅ Interoperable with other DIDComm implementations
- ✅ Actively maintained

### Option 2: Implement ECDH-1PU Manually

If you want to implement it yourself, you'd need to:

```kotlin
private suspend fun performEcdh1puKeyAgreement(
    privateKeyId: String,
    senderPublicKeyJwk: Map<String, Any?>,
    recipientPublicKeyJwk: Map<String, Any?>,
    epk: JsonObject
): ByteArray {
    // 1. Get private key from KMS
    val privateKey = kms.getPrivateKey(privateKeyId) // ⚠️ Need this method!

    // 2. Convert JWK to EC public keys
    val senderPublicKey = jwkToECPublicKey(senderPublicKeyJwk)
    val recipientPublicKey = jwkToECPublicKey(recipientPublicKeyJwk)
    val ephemeralPublicKey = jwkToECPublicKey(epk)

    // 3. Perform ECDH operations
    val keyAgreement = KeyAgreement.getInstance("ECDH", "BC")
    keyAgreement.init(privateKey)

    // ECDH with sender's key
    keyAgreement.doPhase(senderPublicKey, true)
    val senderShared = keyAgreement.generateSecret()

    // ECDH with ephemeral key
    keyAgreement.init(privateKey)
    keyAgreement.doPhase(ephemeralPublicKey, true)
    val ephemeralShared = keyAgreement.generateSecret()

    // 4. Combine using ECDH-1PU algorithm
    // This is the complex part - requires specific algorithm
    val combined = combineEcdh1pu(senderShared, ephemeralShared, senderPublicKey)

    return combined
}

private fun deriveKeys(sharedSecret: ByteArray): Pair<ByteArray, ByteArray> {
    // Use HKDF
    val hkdf = HKDF.fromHmacSha256()
    val salt = ByteArray(32) // Should be from message or protocol
    val info = "didcomm-encryption".toByteArray()

    val cek = hkdf.extractAndExpand(sharedSecret, salt, info, 32)
    val kek = hkdf.extractAndExpand(sharedSecret, salt, "didcomm-key-wrapping".toByteArray(), 32)

    return Pair(cek, kek)
}
```

**Challenges:**
- ❌ ECDH-1PU is complex and easy to get wrong
- ❌ Need to handle multiple curve types
- ❌ Requires extensive testing
- ❌ Must ensure interoperability

## Recommendation

**For Production:** Use `didcomm-java` library

1. **Add dependency:**
```kotlin
dependencies {
    implementation("org.didcommx:didcomm:0.3.2")
}
```

2. **Refactor `DidCommCrypto` to use the library:**
```kotlin
class DidCommCrypto(
    private val kms: KeyManagementService,
    private val resolveDid: suspend (String) -> DidDocument?
) {
    private val didComm = DIDComm()

    suspend fun encrypt(...): DidCommEnvelope {
        // Use didComm.pack() instead of manual encryption
    }

    suspend fun decrypt(...): JsonObject {
        // Use didComm.unpack() instead of manual decryption
    }
}
```

3. **Benefits:**
   - Production-ready cryptography
   - Full DIDComm V2 compliance
   - Interoperability guaranteed
   - Less code to maintain
   - Security tested by community

## Current Implementation Status

| Component | Status | Notes |
|-----------|--------|-------|
| Message Structure | ✅ Complete | JWM format correct |
| Envelope Structure | ✅ Complete | Correct format |
| AES-256-GCM | ✅ Working | Real implementation |
| AES-256-KW | ✅ Working | Real implementation |
| ECDH-1PU | ❌ Placeholder | Returns dummy data |
| HKDF | ❌ Placeholder | Just splits bytes |
| Private Key Access | ❌ Missing | KMS doesn't expose private keys |

## Summary

The current implementation has the **structure** correct but uses **placeholder functions** that return dummy data instead of performing real cryptographic operations. For production use, you should either:

1. **Use `didcomm-java` library** (recommended) - Drop-in replacement
2. **Implement ECDH-1PU manually** - Complex, error-prone, requires extensive testing

The placeholder code demonstrates the flow and structure, but **will not actually encrypt/decrypt messages correctly**.

