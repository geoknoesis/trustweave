# Build Fix Plan

## Summary
Total compilation errors: **1,826 errors** across multiple modules

## Error Distribution by Module

| Module | Error Count | Priority |
|--------|-------------|----------|
| **trust** | 1,584 | 🔴 Critical |
| **credentials** | 148 | 🟡 High |
| **did** | 70 | 🟡 High |
| **kms** | 61 | 🟡 High |
| **wallet** | 54 | 🟡 High |
| **anchors** | 40 | 🟢 Medium |
| **testkit** | 3 | 🟢 Low |
| **contract** | 2 | 🟢 Low |

## Root Causes

### 1. Trust Module (1,584 errors) - CRITICAL
**Primary Issues:**
- DSL tests moved from credential-core have incorrect package/imports
- Missing imports for moved test files
- `CredentialDidResolver` and `CredentialDidResolution` references (removed classes)
- `DidResolutionResult` import issues (wrong package)

**Files Affected:**
- All DSL test files in `trust/src/test/kotlin/com/trustweave/trust/dsl/`
- `trust/src/test/kotlin/com/trustweave/credential/verifier/CredentialVerifierWebOfTrustTest.kt`

### 2. Credentials Module (148 errors)
**Primary Issues:**
- Plugin compilation errors (didcomm)
- Missing testkit dependencies in plugins

### 3. DID Module (70 errors)
**Primary Issues:**
- Import path issues
- Missing class references

### 4. KMS Module (61 errors)
**Primary Issues:**
- Import/package issues

### 5. Wallet Module (54 errors)
**Primary Issues:**
- Import/package issues

### 6. Anchors Module (40 errors)
**Primary Issues:**
- JUnit Platform dependency issues (test execution, not compilation)

## Fix Plan

### Phase 1: Fix Trust Module DSL Tests (Priority: CRITICAL)
**Estimated Time: 30-45 minutes**

1. **Fix package imports in moved DSL tests**
   - All files in `trust/src/test/kotlin/com/trustweave/trust/dsl/` need package verification
   - Update imports from `com.trustweave.credential.dsl` to `com.trustweave.trust.dsl`

2. **Fix CredentialDidResolver references**
   - Replace `CredentialDidResolver` with `DidResolver` from `com.trustweave.did.resolver`
   - Remove `CredentialDidResolution` references
   - Remove `toCredentialDidResolution` extension function calls

3. **Fix DidResolutionResult imports**
   - Change from `com.trustweave.did.DidResolutionResult` to `com.trustweave.did.resolver.DidResolutionResult`

4. **Fix VerificationBuilderBranchCoverageTest**
   - Remove `verifyAnchor` call (doesn't exist in API)
   - Fix `defaultProofType` to use `ProofType` enum instead of string

### Phase 2: Fix Credentials Plugin Issues (Priority: HIGH)
**Estimated Time: 15-20 minutes**

1. **Fix didcomm plugin**
   - Add testkit dependency or remove testkit usage from examples
   - Fix `override` modifiers in `DidCommMessage`

2. **Fix other plugin compilation errors**

### Phase 3: Fix Other Modules (Priority: MEDIUM)
**Estimated Time: 20-30 minutes**

1. **DID module**
   - Fix import paths
   - Resolve missing class references

2. **KMS module**
   - Fix import/package issues

3. **Wallet module**
   - Fix import/package issues

### Phase 4: Fix Test Infrastructure (Priority: LOW)
**Estimated Time: 10 minutes**

1. **Anchors module**
   - Fix JUnit Platform dependency configuration

## Detailed Fix Steps

### Step 1: Fix Trust Module DSL Test Imports
```bash
# Files to fix:
- trust/src/test/kotlin/com/trustweave/trust/dsl/*.kt (22 files)
- trust/src/test/kotlin/com/trustweave/credential/verifier/CredentialVerifierWebOfTrustTest.kt
```

**Actions:**
- Verify all package declarations are `com.trustweave.trust.dsl`
- Update all imports to use correct packages
- Replace `CredentialDidResolver` → `DidResolver`
- Fix `DidResolutionResult` import path

### Step 2: Fix CredentialVerifierWebOfTrustTest
**File:** `trust/src/test/kotlin/com/trustweave/credential/verifier/CredentialVerifierWebOfTrustTest.kt`

**Issues:**
- Lines 5-6: Remove `CredentialDidResolution` and `CredentialDidResolver` imports
- Line 10-11: Fix `DidResolutionResult` import and remove `toCredentialDidResolution`
- Replace all `CredentialDidResolver` usage with `DidResolver`

### Step 3: Fix DIDComm Plugin
**File:** `credentials/plugins/didcomm/src/main/kotlin/com/trustweave/credential/didcomm/examples/DidCommExamples.kt`

**Issues:**
- Remove or fix testkit imports (examples shouldn't depend on testkit)
- Use proper KMS interface instead of `InMemoryKeyManagementService`

**File:** `credentials/plugins/didcomm/src/main/kotlin/com/trustweave/credential/didcomm/models/DidCommMessage.kt`

**Issues:**
- Add `override` modifier to `from` and `to` properties

### Step 4: Systematic Module Fixes
1. Compile each module individually
2. Fix errors module by module
3. Verify dependencies are correct

## Execution Order

1. ✅ **Fix Trust Module DSL Tests** (Start here - highest impact)
2. ✅ **Fix CredentialVerifierWebOfTrustTest**
3. ✅ **Fix DIDComm Plugin**
4. ✅ **Fix Other Modules** (did, kms, wallet)
5. ✅ **Fix Test Infrastructure** (anchors JUnit)

## Success Criteria

- [ ] All modules compile successfully (`compileKotlin` tasks pass)
- [ ] All test code compiles (`compileTestKotlin` tasks pass)
- [ ] Full build succeeds (`./gradlew build` passes)
- [ ] No unresolved references
- [ ] No missing parameter errors
- [ ] All imports resolve correctly

## Notes

- **credential-core** main code compiles successfully ✅
- **credential-core** tests compile successfully ✅
- Main issue is in **trust** module test code (DSL tests we just moved)
- Most errors are import/package related (fixable systematically)

## Quick Fixes Applied

1. ✅ Fixed `TrustDslTest.kt` - Added imports for `DidMethods` and `KeyAlgorithms`
2. ✅ Fixed `TypeSafeHelpersTest.kt` - Added imports for type-safe helpers
3. ✅ Fixed `CredentialVerifierWebOfTrustTest.kt` - Removed `CredentialDidResolver` references

## Remaining Work

### Trust Module DSL Tests
- Add missing imports for `DidMethods`, `KeyAlgorithms` in all DSL test files
- Replace `trustLayer { }` with `TrustWeave.build { }` where needed
- Fix `CredentialDidResolver` → `DidResolver` replacements
- Fix `DidResolutionResult` import paths

### Pattern to Fix
```kotlin
// Add these imports to DSL test files:
import com.trustweave.trust.TrustWeave
import com.trustweave.trust.dsl.credential.DidMethods
import com.trustweave.trust.dsl.credential.KeyAlgorithms
import com.trustweave.trust.dsl.credential.CredentialTypes
import com.trustweave.trust.dsl.credential.ProofTypes

// Replace:
trustLayer { } → TrustWeave.build { }
CredentialDidResolver → DidResolver (from com.trustweave.did.resolver)
DidResolutionResult → DidResolutionResult (from com.trustweave.did.resolver)
```

