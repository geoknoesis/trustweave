# Root Cause Analysis - Test Failures

## Summary
- **Initial Status**: 168 tests completed, 6 failed
- **Current Status**: 168 tests completed, 7 failed
- **Primary Issue**: Ed25519 public key extraction from JWK during proof verification

## Issues Found and Fixed

### 1. ✅ DID Resolution - FIXED
**Problem**: Different `DidMethod` instances were being used for creation vs resolution
**Solution**: Implemented shared `DidMethodRegistry` pattern ensuring same instance is used
**Status**: ✅ Resolved

### 2. ✅ Verification Method ID Construction - FIXED  
**Problem**: Duplicate DID in verification method ID string (e.g., `did:key:...#did:key:...#key-1`)
**Solution**: Fixed `VcLdProofEngine.issue()` to detect and use full verification method IDs directly
**Status**: ✅ Resolved

### 3. ❌ Ed25519 Public Key Extraction - IN PROGRESS
**Problem**: `extractPublicKey()` returns null when extracting Ed25519 public key from JWK
**Root Cause**: 
- `EdECPublicKeySpec` constructor not found via reflection: `NoSuchMethodException: java.security.spec.EdECPublicKeySpec.<init>(java.security.spec.NamedParameterSpec,[B)`
- Java 21 is confirmed, so EdECPublicKeySpec should be available
- Reflection approach is failing to find the correct constructor

**Attempted Solutions**:
1. Direct EdECPublicKeySpec construction via reflection - FAILED (constructor not found)
2. Nimbus OctetKeyPair extraction - FAILED (field access issues)
3. Alternative constructor signatures - FAILED (no suitable constructor found)

**Current Approach**: Trying to find correct constructor signature via reflection

## Next Steps

### Option 1: Fix EdECPublicKeySpec Constructor Call
- Investigate actual Java 21 EdECPublicKeySpec API
- Try alternative constructor signatures
- Use factory methods if available

### Option 2: Use BouncyCastle Library
- Add BouncyCastle dependency
- Use BC's Ed25519 key handling (more reliable)

### Option 3: Workaround with Nimbus
- Create JWT wrapper for verification (complex)
- Use Nimbus's internal verification mechanisms

### Option 4: Accept Limitation
- Return appropriate error when key extraction fails
- Document the limitation

## Test Failures
1. `InMemoryTrustLayerIntegrationTest > test smart contract workflow template()`
2. `InMemoryTrustLayerIntegrationTest > test complete in-memory workflow template()`
3. `InMemoryTrustLayerIntegrationTest > test blockchain anchoring workflow template()`
4. `InMemoryTrustLayerIntegrationTest > test credential revocation workflow template()`
5. `WebOfTrustIntegrationTest > test complete trust registry workflow()`
6. `TrustLayerExtensionsTest > test completeWorkflow without organization()`
7. `TrustRegistryDslComprehensiveTest > test trust registry with credential verification integration()`

All failures appear to be related to proof verification failing due to public key extraction issue.
