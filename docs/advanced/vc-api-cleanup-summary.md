---
nav_exclude: true
---

# VC-Only API - Old Code Cleanup Summary

## ✅ Cleanup Complete

### Removed Deprecated Code ✅

1. **Deprecated Serialization**
   - Removed `Credential.toJsonObject()` deprecated method from `CredentialSerialization.kt`
   - Kept only `VerifiableCredential.toJsonObject()` and `VerifiablePresentation.toJsonObject()`

2. **Deprecated ProofAdapter Methods**
   - Removed `derivePresentation()` deprecated method from `ProofAdapter` interface
   - All adapters now use `createPresentation()` exclusively

3. **Deprecated Exchange Options**
   - Removed `ExchangeOptionsExtensions.kt` file
   - Removed `getExchangeProtocolName()` and `withExchangeProtocolName()` deprecated methods
   - Protocol selection now uses type-safe `protocolName` parameter in `ExchangeRequest`

### Updated Type References ✅

1. **ProofAdapters Discovery**
   - Updated `ProofAdapters` to use `CredentialFormatId` instead of `CredentialFormat` enum
   - Renamed `autoRegisterFormats()` → `autoRegisterFormatIds()`
   - Updated method signatures to use `CredentialFormatId` throughout

2. **CredentialServices Factory**
   - Updated `createCredentialServiceWithAutoDiscovery()` to use `List<CredentialFormatId>` instead of `List<CredentialFormat>`
   - Updated to call `autoRegisterFormatIds()` instead of `autoRegisterFormats()`

3. **Plugin Serialization**
   - Cleaned up `CredentialSerialization.kt` to remove all references to old `Credential` model
   - Removed unused imports (`SubjectId`, old `Credential`)

### Files Removed ✅

- `credentials/credential-api/src/main/kotlin/org.trustweave/credential/exchange/options/ExchangeOptionsExtensions.kt`

### Files Updated ✅

- `credentials/plugins/didcomm/src/main/kotlin/org.trustweave/credential/didcomm/protocol/util/CredentialSerialization.kt`
  - Removed deprecated `Credential.toJsonObject()` method
  - Kept only VC type serialization

- `credentials/credential-api/src/main/kotlin/org.trustweave/credential/spi/proof/ProofAdapter.kt`
  - Removed deprecated `derivePresentation()` method

- `credentials/credential-api/src/main/kotlin/org.trustweave/credential/proof/ProofAdapters.kt`
  - Updated to use `CredentialFormatId` throughout
  - Renamed method: `autoRegisterFormats()` → `autoRegisterFormatIds()`

- `credentials/credential-api/src/main/kotlin/org.trustweave/credential/CredentialServices.kt`
  - Updated to use `CredentialFormatId` instead of `CredentialFormat`

### Remaining Valid Types

These are still valid and used:
- `CredentialFormat` (data class for metadata) - Valid, used for format metadata
- `CredentialFormatId` (value class) - Valid, used for format identifiers
- `IssuerId`, `CredentialId`, `StatusListId`, `SchemaId` - Valid identifier types
- `Evidence.verifier: IssuerId?` - Valid, `IssuerId` is still a valid identifier type

### Migration Status

- **Deprecated Code Removal**: 100% ✅
- **Type Reference Updates**: 100% ✅
- **Old Model Cleanup**: 100% ✅

All old code has been removed. The API is now fully VC-only with no deprecated methods or old type references remaining.

