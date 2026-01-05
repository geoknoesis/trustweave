---
nav_exclude: true
---

# VC-Only API Plugin Migration Summary

## ✅ Plugin Protocol Implementations Complete

### DIDComm Plugin ✅

**Files Updated:**
- `DidCommExchangeProtocol.kt` - Updated to match new `CredentialExchangeProtocol` interface
- `CredentialProtocol.kt` - Updated `createCredentialIssue()` to accept `VerifiableCredential`
- `ProofProtocol.kt` - Updated `createProofPresentation()` to accept `VerifiablePresentation`
- `CredentialSerialization.kt` - Added serialization utilities for VC types

**Key Changes:**
- Uses `ExchangeProtocolName.DidComm` instead of string
- Uses `ExchangeProtocolCapabilities` instead of `supportedOperations`
- Method signatures updated:
  - `offer()` → `ExchangeMessageEnvelope`
  - `request()` → `ExchangeMessageEnvelope`
  - `issue()` → `Pair<VerifiableCredential, ExchangeMessageEnvelope>`
  - `requestProof()` → `ExchangeMessageEnvelope`
  - `presentProof()` → `Pair<VerifiablePresentation, ExchangeMessageEnvelope>`

### OIDC4VCI Plugin ✅

**Files Updated:**
- `Oidc4VciExchangeProtocol.kt` - Updated to match new `CredentialExchangeProtocol` interface
- `Oidc4VciService.kt` - Updated `issueCredential()` to accept `VerifiableCredential`
- `Oidc4VciModels.kt` - Updated `Oidc4VciIssueResult` to use `VerifiableCredential`

**Key Changes:**
- Uses `ExchangeProtocolName.Oidc4Vci` instead of string
- Uses `ExchangeProtocolCapabilities` instead of `supportedOperations`
- All method signatures updated to match new interface
- Removed all references to `CredentialEnvelope`

### Proof Adapter Plugins ✅

**Already Complete:**
- `VcLdProofAdapter` - Already uses `VerifiableCredential` and `VerifiablePresentation`
- `SdJwtProofAdapter` - Already uses `VerifiableCredential` and `VerifiablePresentation`

**Remaining (Non-VC formats):**
- `AnonCredsProofAdapter` - Uses old types (but AnonCreds is not a VC format)
- `MdlProofAdapter` - Uses old types (but mDL is not a VC format)
- `X509ProofAdapter` - Uses old types (but X.509 is not a VC format)
- `PassKeyProofAdapter` - Uses old types (but PassKeys are not a VC format)

Note: Non-VC format adapters may remain as-is or be removed, depending on the framework's focus on VC-only standards.

## Migration Status

- **DIDComm Protocol**: 100% ✅
- **OIDC4VCI Protocol**: 100% ✅
- **Proof Adapter Plugins (VC formats)**: 100% ✅

## Next Steps

1. Update tests to use VC types
2. Remove or deprecate non-VC format adapters if focusing exclusively on VC standards
3. Update documentation and examples

