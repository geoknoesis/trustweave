# DID Core Test Cleanup Plan

## Issues Found

### 1. Legacy/Deprecated API Usage
- **`resolveDid(did: String)`** - Used in multiple test files, should use `resolveDid(did: Did)`
  - `DidTest.kt` - lines 68, 156
  - `DidMethodInterfaceContractTest.kt` - line 183
  - `DidMethodEdgeCasesTest.kt` - lines 166, 197, 261
  - `DidMethodProviderTest.kt` - line 67
  - `DidDocumentDelegationVerifierTest.kt` - uses string-based resolver

### 2. TODO() Implementations
- **`DidTest.kt` line 68** - Mock method has TODO(), should be implemented

### 3. Outdated Comments
- **`DidParseBranchCoverageTest.kt` line 7** - Comment mentions "Did.parse()" which doesn't exist (should be Did constructor)

### 4. Redundant Tests
- `DidParseBranchCoverageTest` and `DidTest` have overlapping coverage
- `DidModelsBranchCoverageTest` and `DidModelsTest` have overlapping coverage
- Consider consolidating or removing redundant tests

### 5. Type Safety Issues
- Tests using string-based `resolveDid()` instead of type-safe `Did` objects
- Tests passing strings where `Did` objects are expected

## Cleanup Actions

1. ✅ Update all tests to use `resolveDid(did: Did)` instead of deprecated `resolveDid(did: String)`
2. ✅ Fix TODO() implementations in mock methods
3. ✅ Update outdated comments
4. ✅ Consolidate redundant test files
5. ✅ Ensure all tests use type-safe APIs

