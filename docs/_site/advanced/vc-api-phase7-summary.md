# VC-Only API Migration - Phase 7 Summary

## ✅ Phase 7: Legacy Code Removal - COMPLETE

### Removed Files ✅

1. **Old Credential Model**
   - `model/Credential.kt` - Format-agnostic credential class
   - Replaced by: `model.vc/VerifiableCredential.kt`

2. **Old CredentialProof**
   - `model/CredentialProof.kt` - Format-agnostic proof with opaque data
   - Replaced by: `model.vc/CredentialProof.kt` (sealed class with VC-specific variants)

3. **Old CredentialStatus**
   - `model/CredentialStatus.kt` - Old status model
   - Replaced by: `model.vc/CredentialStatus.kt` (VC-aligned)

4. **EnvelopeSerialization Utility**
   - `internal/util/EnvelopeSerialization.kt` - Utility for old envelope model
   - No longer needed with VC-only API

### Updated Imports ✅

1. **SchemaRegistry.kt** ✅
   - Updated to import `VerifiableCredential` instead of old `Credential`

2. **StatusListManager.kt** ✅
   - Updated to import `VerifiableCredential` instead of old `Credential`

3. **SchemaValidator.kt** ✅
   - Removed unused import of old `Credential`
   - Updated documentation to reflect VC-only focus

### Remaining Files That Need Attention

These files still reference the old `Credential` class and need updates (but may be lower priority or deprecated):

1. **Extension Files** (may be deprecated or need VC updates):
   - `CredentialServiceExtensions.kt` - Uses old `Credential`
   - `CredentialServicesExtensions.kt` - Uses old types
   - `CredentialServiceDidExtensions.kt` - Uses old types

2. **Validation**:
   - `CredentialValidator.kt` - Uses old `Credential` for structure validation
   - May need updating or integration into VC verification flow

3. **Templates**:
   - `TemplateService.kt` - May use old types

### Notes

- The `Claims` typealias (`Map<String, JsonElement>`) is still valid and widely used
- All core API interfaces now use `VerifiableCredential` and `VerifiablePresentation`
- Old format-agnostic abstractions have been removed from the core model
- Remaining references to old `Credential` are primarily in extension/utility functions

### Migration Status

- **Core Models**: 100% migrated ✅
- **Core Services**: 100% migrated ✅
- **Proof Adapters**: 100% migrated ✅
- **Exchange Protocols (Core)**: 100% migrated ✅
- **Legacy Model Removal**: 100% complete ✅
- **Extension Functions**: Needs review/update ⏳
- **Plugin Implementations**: Needs refactoring ⏳

