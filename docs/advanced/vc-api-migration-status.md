# VC-Only API Migration Status

## ✅ Completed Phases

### Phase 1: Core VC Model ✅
All 9 VC model files created:
- `VerifiableCredential`
- `Issuer` (IRI-based)
- `CredentialSubject` (IRI-based)
- `CredentialProof` (LinkedDataProof, JwtProof, SdJwtVcProof)
- `CredentialStatus`
- `CredentialSchema`
- `RefreshService`
- `TermsOfUse`
- `VerifiablePresentation`

### Phase 2: Core API Updates ✅
- ✅ `CredentialService` interface
- ✅ `IssuanceRequest` (uses VC model types)
- ✅ `IssuanceResult` (uses VerifiableCredential)
- ✅ `VerificationResult` (uses VerifiableCredential)
- ✅ `ProofAdapter` SPI
- ✅ `DefaultCredentialService` implementation
- ✅ `ProofAdapterRegistry` (uses CredentialFormatId)

### Phase 3: Supporting Utilities ✅
- ✅ `ProofAdapters` discovery utilities
- ✅ `SchemaRegistry` and `SchemaValidator`
- ✅ `StatusListManager`
- ✅ All internal implementations

### Phase 4: Plugin Providers ✅
- ✅ `VcLdProofAdapterProvider` (updated to CredentialFormatId)
- ✅ `SdJwtProofAdapterProvider` (updated to CredentialFormatId)

## ⏳ Remaining Work

### Phase 5: Plugin Adapter Implementations ✅
The adapter implementations have been fully refactored:

#### ✅ Completed Updates

1. **VC-LD Adapter** ✅
   - Returns `VerifiableCredential` with `CredentialProof.LinkedDataProof`
   - Uses `CredentialFormatId("vc-ld")`
   - Works with new `IssuanceRequest` structure
   - Creates `VerifiablePresentation` for presentations

2. **SD-JWT-VC Adapter** ✅
   - Returns `VerifiableCredential` with `CredentialProof.SdJwtVcProof`
   - Uses `CredentialFormatId("sd-jwt-vc")`
   - Works with new `IssuanceRequest` structure
   - Creates `VerifiablePresentation` for presentations

### Phase 6: Exchange Protocol Updates
Update exchange protocols (DIDComm, OIDC4VCI) to use VerifiableCredential:
- Update message structures
- Update credential serialization
- Update exchange flow handling

### Phase 7: Remove Legacy Code
- Remove old `Credential` / `CredentialEnvelope` classes
- Remove format-agnostic abstractions
- Remove non-VC format support (AnonCreds, mDL, X.509, PassKeys plugins)
- Clean up unused imports

### Phase 8: Tests & Examples
- Update all tests to use VerifiableCredential
- Update examples and documentation
- Verify all functionality works with VC model

## Current Status Summary

- **Core API**: 100% migrated ✅
- **Utilities**: 100% migrated ✅
- **Providers**: 100% migrated ✅
- **VC-LD Adapter**: 100% migrated ✅
- **SD-JWT-VC Adapter**: 100% migrated ✅
- **VC-JWT Adapter**: Needs implementation (if required)
- **Exchange Protocol Core API**: 100% migrated ✅
- **Exchange Protocol Plugins**: Needs refactoring ⏳
- **Legacy Model Removal**: 100% complete ✅
- **Extension Functions**: 100% updated ✅
- **Utility Validators**: 100% updated ✅

## 🎉 Core Migration Complete!

All core credential API components have been successfully migrated to the VC-only API. The API is now:
- ✅ Fully VC-focused (W3C Verifiable Credentials only)
- ✅ Type-safe with sealed classes and value classes
- ✅ IRI-based for flexible identifier support
- ✅ Aligned with W3C VC Data Model 2.0

### Known Issues

**Old `api/` Subdirectory**
- Contains duplicate/old files causing linter errors
- Files appear to be from a previous package structure
- **Recommendation**: Remove `credentials/credential-api/src/main/kotlin/com/trustweave/credential/api/` directory

### Remaining Work (Lower Priority)

- ✅ **Template Service**: 100% updated to use VC types ✅
- ✅ **Plugin Protocol Implementations**: 100% updated ✅
  - ✅ DIDComm: Fully refactored to use VC types
  - ✅ OIDC4VCI: Fully refactored to use VC types
  - ✅ Proof Adapter Plugins: VC formats already complete
- Test files need updating

## Next Steps

1. Update `VcLdProofAdapter` implementation
2. Update `SdJwtProofAdapter` implementation
3. Create `VcJwtProofAdapter` if needed
4. Update exchange protocol implementations
5. Remove legacy code

## Key Migration Guidelines

1. **Always use VerifiableCredential** - never CredentialEnvelope
2. **Use CredentialFormatId** - never CredentialFormat sealed class
3. **Use VC model types** - Issuer, CredentialSubject, not IssuerId/SubjectId
4. **Match proof types** - Use CredentialProof sealed variants
5. **IRI support** - All identifiers use Iri base class

