---
title: DIDComm V2 Integration Guide
---

# DIDComm V2 Integration Guide

## Overview

This guide explains how to integrate the production-ready `didcomm-java` library to replace the placeholder cryptographic implementation.

## Current Implementation Status

The DIDComm plugin has two crypto implementations:

1. **Placeholder Implementation** (`DidCommCrypto.kt`)
   - ✅ Structure is correct
   - ❌ Returns dummy data (not real encryption)
   - ⚠️ Suitable for development/testing only

2. **Production Implementation** (`DidCommCryptoProduction.kt`)
   - ✅ Uses `didcomm-java` library
   - ✅ Full ECDH-1PU implementation
   - ⚠️ Requires library dependency

## Step 1: Add didcomm-java Dependency

### Option A: Add to libs.versions.toml (Recommended)

Add to `gradle/libs.versions.toml`:

```toml
[versions]
didcomm-java = "0.3.2"

[libraries]
didcomm-java = { module = "org.didcommx:didcomm", version.ref = "didcomm-java" }
```

Then in `credentials/plugins/didcomm/build.gradle.kts`:

```kotlin
dependencies {
    // ... existing dependencies ...
    
    // DIDComm library (production crypto)
    implementation(libs.didcomm.java)
}
```

### Option B: Direct Dependency

Add directly to `credentials/plugins/didcomm/build.gradle.kts`:

```kotlin
dependencies {
    // ... existing dependencies ...
    
    // DIDComm library (production crypto)
    implementation("org.didcommx:didcomm:0.3.2")
}
```

## Step 2: Update DidCommCryptoProduction

Uncomment the code in `DidCommCryptoProduction.kt`:

```kotlin
class DidCommCryptoProduction(...) {
    private val didComm = org.didcommx.didcomm.DIDComm()
    
    suspend fun encrypt(...): String {
        // Uncomment the implementation code
        val didCommMessage = org.didcommx.didcomm.message.Message.builder()
            .id(message["id"]?.jsonPrimitive?.content ?: UUID.randomUUID().toString())
            .type(message["type"]?.jsonPrimitive?.content ?: "")
            .from(fromDid)
            .to(listOf(toDid))
            .body(message["body"]?.jsonObject ?: buildJsonObject { })
            .build()
        
        val packed = didComm.pack(
            message = didCommMessage,
            from = fromDid,
            to = listOf(toDid),
            signFrom = fromKeyId
        )
        
        return packed.value
    }
    
    suspend fun decrypt(...): JsonObject {
        // Uncomment the implementation code
        val packed = org.didcommx.didcomm.pack.EncryptedPackedMessage(packedMessage)
        val unpacked = didComm.unpack(
            packed = packed,
            to = recipientDid,
            from = senderDid
        )
        
        // Convert back to JsonObject
        // ...
    }
}
```

## Step 3: Update Factory to Use Production Crypto

Update `DidCommFactory.kt`:

```kotlin
object DidCommFactory {
    fun createInMemoryService(
        kms: KeyManagementService,
        resolveDid: suspend (String) -> DidDocument?,
        useProductionCrypto: Boolean = true // Enable production crypto
    ): DidCommService {
        val crypto = if (useProductionCrypto) {
            DidCommCryptoAdapter(kms, resolveDid, useProduction = true)
        } else {
            DidCommCryptoAdapter(kms, resolveDid, useProduction = false)
        }
        val packer = DidCommPacker(crypto, resolveDid)
        return InMemoryDidCommService(packer, resolveDid)
    }
}
```

## Step 4: Update DidCommPacker

Update `DidCommPacker.kt` to handle both envelope and packed string formats:

```kotlin
class DidCommPacker(
    private val crypto: DidCommCryptoAdapter, // Use adapter
    private val resolveDid: suspend (String) -> DidDocument?
) {
    suspend fun pack(...): String {
        val messageJson = message.toJsonObject()
        
        if (encrypt) {
            // Use adapter which handles both implementations
            if (crypto is DidCommCryptoAdapter && crypto.useProduction) {
                // Production crypto returns packed string directly
                return crypto.encryptAsPacked(
                    messageJson, fromDid, fromKeyId, toDid, toKeyId
                )
            } else {
                // Placeholder crypto returns envelope
                val envelope = crypto.encrypt(...)
                return envelopeToJson(envelope)
            }
        }
        // ...
    }
}
```

## Step 5: Key Management Integration

The `didcomm-java` library needs access to private keys. You'll need to:

1. **Extend KMS Interface** (if needed):
   ```kotlin
   interface KeyManagementService {
       // ... existing methods ...
       
       suspend fun getPrivateKey(keyId: String): PrivateKey
   }
   ```

2. **Create Key Adapter**:
   ```kotlin
   class DidCommKeyResolver(
       private val kms: KeyManagementService
   ) : org.didcommx.didcomm.secrets.SecretsResolver {
       override suspend fun getKey(keyId: String): PrivateKey {
           return kms.getPrivateKey(keyId)
       }
   }
   ```

3. **Pass to DIDComm**:
   ```kotlin
   val keyResolver = DidCommKeyResolver(kms)
   val didComm = org.didcommx.didcomm.DIDComm(secretsResolver = keyResolver)
   ```

## Testing

### Test with Placeholder (Development)

```kotlin
val didcomm = DidCommFactory.createInMemoryService(
    kms = kms,
    resolveDid = resolveDid,
    useProductionCrypto = false // Use placeholder
)
```

### Test with Production Crypto

```kotlin
val didcomm = DidCommFactory.createInMemoryService(
    kms = kms,
    resolveDid = resolveDid,
    useProductionCrypto = true // Use production
)
```

## Verification

After integration, verify:

1. ✅ Messages encrypt correctly
2. ✅ Messages decrypt correctly
3. ✅ Interoperability with other DIDComm implementations
4. ✅ All tests pass

## Troubleshooting

### "ClassNotFoundException: org.didcommx.didcomm.DIDComm"

**Solution:** The `didcomm-java` dependency is not on the classpath. Add it to `build.gradle.kts`.

### "UnsupportedOperationException: Production crypto not available"

**Solution:** Uncomment the implementation code in `DidCommCryptoProduction.kt`.

### "Cannot resolve getPrivateKey"

**Solution:** Extend your KMS implementation to provide private key access, or create an adapter.

## Migration Path

1. **Phase 1 (Current)**: Use placeholder crypto for development
2. **Phase 2**: Add `didcomm-java` dependency
3. **Phase 3**: Uncomment production crypto code
4. **Phase 4**: Test with production crypto
5. **Phase 5**: Switch to production crypto by default

## References

- [didcomm-java GitHub](https://github.com/sicpa-dlab/didcomm-java)
- [DIDComm V2 Specification](https://didcomm.org/book/v2/)
- [Maven Central - didcomm](https://mvnrepository.com/artifact/org.didcommx/didcomm)

