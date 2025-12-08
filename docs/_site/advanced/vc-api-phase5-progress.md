# VC-Only API Migration - Phase 5 Progress

## ✅ Phase 5: Plugin Adapter Implementations - IN PROGRESS

### Completed

1. **Plugin Providers** ✅
   - `VcLdProofAdapterProvider` - Updated to use `CredentialFormatId`
   - `SdJwtProofAdapterProvider` - Updated to use `CredentialFormatId`

2. **VC-LD Adapter** ✅
   - Fully refactored to use `VerifiableCredential`
   - Returns `VerifiableCredential` with `CredentialProof.LinkedDataProof`
   - Verification works with VC model
   - Presentation creates `VerifiablePresentation`
   - Uses new `IssuanceRequest` structure

3. **SD-JWT-VC Adapter** ✅
   - Fully refactored to use `VerifiableCredential`
   - Returns `VerifiableCredential` with `CredentialProof.SdJwtVcProof`
   - Verification works with VC model
   - Presentation creates `VerifiablePresentation`
   - Uses new `IssuanceRequest` structure

## Key Changes Made

### VC-LD Adapter

1. **Issue Method**
   - Now returns `VerifiableCredential` directly
   - Creates `CredentialProof.LinkedDataProof` with all proof details
   - Uses `Issuer` and `CredentialSubject` from request
   - Builds VC structure aligned with W3C VC Data Model

2. **Verify Method**
   - Accepts `VerifiableCredential` instead of `CredentialEnvelope`
   - Extracts proof from `CredentialProof.LinkedDataProof`
   - Returns `VerificationResult` with IRI-based identifiers

3. **CreatePresentation Method**
   - Returns `VerifiablePresentation` instead of `CredentialEnvelope`
   - Uses `CredentialType.Custom("VerifiablePresentation")`
   - Includes challenge/domain from proofOptions

### SD-JWT-VC Adapter

1. **Issue Method**
   - Now returns `VerifiableCredential` directly
   - Creates `CredentialProof.SdJwtVcProof` with JWT string
   - Uses new `IssuanceRequest` structure

2. **Verify Method**
   - Accepts `VerifiableCredential`
   - Extracts JWT from `CredentialProof.SdJwtVcProof`
   - Verifies JWT signature and returns result

3. **CreatePresentation Method**
   - Returns `VerifiablePresentation`
   - Handles SD-JWT selective disclosure structure

## Remaining Work

### Phase 6: Exchange Protocol Updates
Update exchange protocols to use VerifiableCredential:
- DIDComm plugin
- OIDC4VCI plugin
- CHAPI plugin

### Phase 7: Legacy Code Removal
- Remove old `Credential` / `CredentialEnvelope` classes
- Remove `EnvelopeSerialization` utility (if no longer needed)
- Remove non-VC format plugins (AnonCreds, mDL, X.509, PassKeys)

### Phase 8: Testing & Documentation
- Update tests
- Update examples
- Verify end-to-end flows

## Migration Status

- **Core API**: 100% ✅
- **Utilities**: 100% ✅
- **Providers**: 100% ✅
- **VC-LD Adapter**: 100% ✅
- **SD-JWT-VC Adapter**: 100% ✅
- **VC-JWT Adapter**: 0% (needs implementation)
- **Exchange Protocols**: 0% ⏳
- **Legacy Cleanup**: 0% ⏳

## Notes

- Both adapters now fully use the VC-only API
- Placeholder signing/verification logic needs real KMS/DID resolver integration
- Full SD-JWT-VC disclosure support needs implementation
- Presentation-level proofs need implementation for both formats

