# VC-Only API Migration - Phase 6 Progress

## ✅ Phase 6: Exchange Protocol Core API Updates - COMPLETE

### Completed

1. **CredentialExchangeProtocol Interface** ✅
   - Updated `issue()` to return `Pair<VerifiableCredential, ExchangeMessageEnvelope>`
   - Updated `presentProof()` to return `Pair<VerifiablePresentation, ExchangeMessageEnvelope>`
   - Updated all method signatures and documentation

2. **ExchangeRequest Types** ✅
   - `ExchangeRequest.Issue.credential` changed from `Credential` to `VerifiableCredential`
   - `ProofExchangeRequest.Presentation.presentation` changed from `Credential` to `VerifiablePresentation`

3. **ExchangeResponse Types** ✅
   - `ExchangeResponse.Issue.credential` changed from `Credential` to `VerifiableCredential`
   - `ProofExchangeResponse.Presentation.presentation` changed from `Credential` to `VerifiablePresentation`

4. **DefaultExchangeService** ✅
   - Updated imports to use `VerifiableCredential` and `VerifiablePresentation`
   - All internal usage updated to work with new types

5. **Documentation Updates** ✅
   - Updated KDoc comments to reflect VC-only API
   - Updated references from "format-agnostic Credential" to "VerifiableCredential"

## Key Changes

### Interface Updates

```kotlin
// OLD
suspend fun issue(request: ExchangeRequest.Issue): Pair<Credential, ExchangeMessageEnvelope>
suspend fun presentProof(request: ProofExchangeRequest.Presentation): Pair<Credential, ExchangeMessageEnvelope>

// NEW
suspend fun issue(request: ExchangeRequest.Issue): Pair<VerifiableCredential, ExchangeMessageEnvelope>
suspend fun presentProof(request: ProofExchangeRequest.Presentation): Pair<VerifiablePresentation, ExchangeMessageEnvelope>
```

### Request Type Updates

```kotlin
// OLD
data class Issue(
    val credential: Credential,
    ...
) : ExchangeRequest()

// NEW
data class Issue(
    val credential: VerifiableCredential,
    ...
) : ExchangeRequest()
```

## Remaining Work

### Phase 7: Plugin Implementation Updates

The plugin implementations (DIDComm, OIDC4VCI) currently use an older interface structure and need significant refactoring:

1. **DIDComm Plugin**
   - Update `DidCommExchangeProtocol` to implement new `CredentialExchangeProtocol` interface
   - Update `CredentialProtocol.createCredentialIssue()` to accept `VerifiableCredential`
   - Update `ProofProtocol.createProofPresentation()` to accept `VerifiablePresentation`
   - Update credential serialization logic

2. **OIDC4VCI Plugin**
   - Similar refactoring needed for OIDC4VCI protocol implementation

### Phase 8: Legacy Code Removal
- Remove old `Credential` class (if still exists)
- Remove `CredentialEnvelope` class (if still exists)
- Remove non-VC proof adapter plugins

## Migration Status

- **Core Exchange API**: 100% ✅
- **Plugin Implementations**: 0% ⏳
- **Legacy Cleanup**: 0% ⏳

## Notes

- The core exchange protocol API is now fully VC-focused
- Plugin implementations need separate refactoring effort as they use older interface patterns
- All type-safe exchange operations now work with `VerifiableCredential` and `VerifiablePresentation`

