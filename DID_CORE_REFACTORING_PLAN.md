# did:core Module Refactoring Plan

## Executive Summary

This document identifies consolidation opportunities, package reorganization, naming improvements, elimination of `Any` types, and methods that should be moved to other modules.

---

## 1. Class Consolidation Opportunities

### 🔴 High Priority: Merge Adapter Pattern Classes

**Issue**: The adapter pattern is over-engineered. Each interface has only one implementation, making the abstraction unnecessary.

#### 1.1 Merge `DidMethodService` + `DidMethodServiceAdapter`

**Current State:**
- `DidMethodService` (interface) - uses `Any` types
- `DidMethodServiceAdapter` (only implementation)

**Recommendation:**
- **Option A**: Remove interface, use `DidMethod` directly
- **Option B**: If needed for cross-module use, use proper generics instead of `Any`

**Files:**
- `services/DidMethodService.kt` - DELETE or refactor
- `services/DidMethodServiceAdapter.kt` - DELETE or refactor

**Action**: Since `DidMethod` interface already exists, `DidMethodService` is redundant. Remove it entirely.

#### 1.2 Merge `DidDocumentAccess` + `DidDocumentAccessAdapter`

**Current State:**
- `DidDocumentAccess` (interface) - uses `Any` types
- `DidDocumentAccessAdapter` (only implementation)
- Also includes `VerificationMethodAccessAdapter` and `ServiceAccessAdapter`

**Recommendation:**
- **Option A**: Remove interface, use `DidDocument`, `VerificationMethodRef`, `Service` types directly
- **Option B**: If cross-module access needed, create a proper typed interface

**Files:**
- `services/DidDocumentAccess.kt` - REFACTOR to use proper types
- `services/DidDocumentAccessAdapter.kt` - REFACTOR or DELETE

**Action**: Replace `Any` with actual types. If used by other modules, they should depend on `did:core`.

#### 1.3 Merge `VerificationMethodAccess` + `VerificationMethodAccessAdapter`

**Current State:**
- `VerificationMethodAccess` (interface) - uses `Any`
- `VerificationMethodAccessAdapter` (only implementation)

**Recommendation**: Remove interface, use `VerificationMethodRef` directly.

**Files:**
- `services/DidDocumentAccess.kt` (contains `VerificationMethodAccess`) - REFACTOR
- `services/DidDocumentAccessAdapter.kt` (contains adapter) - REFACTOR

#### 1.4 Merge `ServiceAccess` + `ServiceAccessAdapter`

**Current State:**
- `ServiceAccess` (interface) - uses `Any`
- `ServiceAccessAdapter` (only implementation)

**Recommendation**: Remove interface, use `Service` directly.

**Files:**
- `services/DidDocumentAccess.kt` (contains `ServiceAccess`) - REFACTOR
- `services/DidDocumentAccessAdapter.kt` (contains adapter) - REFACTOR

---

## 2. Package Organization Improvements

### Current Structure Issues

```
did/core/src/main/kotlin/com/trustweave/did/
├── services/          ❌ Too many responsibilities
│   ├── DidDocumentAccess.kt
│   ├── DidDocumentAccessAdapter.kt
│   ├── DidMethodFactory.kt
│   ├── DidMethodService.kt
│   └── DidMethodServiceAdapter.kt
├── dsl/              ⚠️  Might belong in trust module
├── delegation/       ✅ Good separation
├── resolution/       ✅ Good separation
├── validation/       ✅ Good separation
└── spi/              ✅ Good separation
```

### Recommended Structure

```
did/core/src/main/kotlin/com/trustweave/did/
├── core/                    # Core interfaces and models
│   ├── DidMethod.kt
│   ├── DidMethodRegistry.kt
│   ├── DidModels.kt
│   └── DidCreationOptions.kt
├── registry/                # Registry implementations
│   └── DefaultDidMethodRegistry.kt
├── resolution/              # Resolution logic
│   └── DidResolver.kt
├── validation/             # Validation logic
│   └── DidValidator.kt
├── delegation/             # Delegation logic
│   └── DelegationService.kt
├── exception/              # Error types
│   └── DidErrors.kt
├── spi/                     # Service provider interfaces
│   └── DidMethodProvider.kt
└── util/                    # Utilities
    └── DidUtils.kt
```

**Note**: DSL classes should be moved to `trust` module (see section 5).

---

## 3. Naming Improvements

### 3.1 Service Interface Names

| Current Name | Issue | Recommended Name | Rationale |
|-------------|-------|------------------|-----------|
| `DidMethodService` | Redundant with `DidMethod` | **DELETE** | Use `DidMethod` directly |
| `DidDocumentAccess` | Unclear purpose | `DidDocumentReader` or **DELETE** | Use `DidDocument` directly |
| `DidMethodFactory` | OK but could be clearer | `DidMethodProviderFactory` | More descriptive |
| `DidDslProvider` | Provider for DSL, not clear | `DidContext` or move to trust | Better describes purpose |

### 3.2 Class Names

| Current Name | Issue | Recommended Name |
|-------------|-------|------------------|
| `DefaultDidMethodRegistry` | OK | Keep as-is |
| `DidBuilder` | OK | Keep as-is |
| `DidDocumentBuilder` | OK | Keep as-is |
| `DelegationBuilder` | OK | Keep as-is |

### 3.3 Package Names

| Current | Recommended | Rationale |
|---------|-------------|-----------|
| `services/` | **DELETE** (merge into core) | Services are core functionality |
| `dsl/` | Move to `trust/dsl/` | DSL is orchestration, not core |

---

## 4. Eliminate `Any` Usage

### 4.1 Current `Any` Usage Locations

**Found 129 instances of `Any` usage:**

1. **Service Interfaces** (CRITICAL):
   - `DidMethodService` - 4 instances
   - `DidDocumentAccess` - 15 instances
   - `DidMethodFactory` - 3 instances

2. **DelegationService** (CRITICAL):
   - Lines 216, 250, 322 - reflection-based `Any` usage

3. **DidDocumentDsl** (CRITICAL):
   - Lines 137, 168, 171, 182, 184, 196, 198, 339, 610 - massive reflection

4. **Data Models** (ACCEPTABLE):
   - `DidModels.kt` - `serviceEndpoint: Any` (W3C spec allows this)
   - `DidCreationOptions` - `additionalProperties: Map<String, Any?>` (acceptable)

### 4.2 Refactoring Strategy

#### Strategy 1: Remove Service Abstractions (RECOMMENDED)

**For `DidMethodService`:**
```kotlin
// DELETE: services/DidMethodService.kt
// DELETE: services/DidMethodServiceAdapter.kt
// USE: DidMethod interface directly
```

**For `DidDocumentAccess`:**
```kotlin
// BEFORE:
interface DidDocumentAccess {
    fun getDocument(result: Any): Any?
    fun getVerificationMethod(doc: Any): List<Any>
}

// AFTER: Remove interface, use types directly
// In DelegationService:
fun getVerificationMethod(doc: DidDocument): List<VerificationMethodRef>
```

#### Strategy 2: Use Proper Types (If Cross-Module Access Needed)

If other modules need to access DID documents without depending on `did:core`:

```kotlin
// Create a minimal interface with proper types
interface DidDocumentReader {
    fun getDocument(result: DidResolutionResult): DidDocument?
    fun getVerificationMethod(doc: DidDocument): List<VerificationMethodRef>
    fun getService(doc: DidDocument): List<Service>
}
```

**Recommendation**: Use Strategy 1. Other modules should depend on `did:core` if they need DID functionality.

---

## 5. Methods Outside Module Scope

### 5.1 DSL Classes Should Move to `trust` Module

**Current Location**: `did/core/dsl/`

**Files to Move:**
- `DidDslProvider.kt` → `trust/dsl/DidDslProvider.kt`
- `DidDsl.kt` → `trust/dsl/DidDsl.kt`
- `DidDocumentDsl.kt` → `trust/dsl/DidDocumentDsl.kt`
- `DelegationDsl.kt` → `trust/dsl/DelegationDsl.kt`

**Rationale:**
- DSL classes are orchestration/configuration, not core DID functionality
- They depend on `TrustLayerContext` (implicitly via `DidDslProvider`)
- Similar DSLs exist in `trust` module (`TrustDsl`, `TrustLayerConfig`)
- `did:core` should be a pure DID library without orchestration

### 5.2 Reflection Code Should Use Access Services

**File**: `DidDocumentDsl.kt` (lines 160-486)

**Current Issue**: Massive reflection code to update DID documents

**Fix**: Use `DidDocument.copy()` and proper types instead of reflection

**Before:**
```kotlin
val typeField = service.javaClass.getDeclaredField("type")
typeField.isAccessible = true
```

**After:**
```kotlin
// Use DidDocument directly
val updatedDoc = currentDoc.copy(
    verificationMethod = updatedVm,
    service = updatedServices,
    // ...
)
```

### 5.3 DelegationService Reflection

**File**: `DelegationService.kt` (lines 223-370)

**Current Issue**: Uses reflection to access document properties

**Fix**: Use `DidDocument` and `VerificationMethodRef` types directly

**Before:**
```kotlin
val typeField = service.javaClass.getDeclaredField("type")
```

**After:**
```kotlin
// Use Service type directly
service.type
```

---

## 6. External Dependencies Analysis

### 6.1 Current Dependencies

**build.gradle.kts:**
```kotlin
dependencies {
    implementation(project(":common"))
    implementation(libs.kotlinx.coroutines.core)
}
```

### 6.2 Dependency Issues

1. **Missing KMS Dependency** (if needed):
   - Documentation mentions KMS but it's not in dependencies
   - **Action**: Add `implementation(project(":kms:core"))` OR document why optional

2. **Common Module Usage**:
   - Uses `com.trustweave.core.exception.TrustWeaveException` ✅ OK
   - Uses `com.trustweave.core.util.ValidationResult` ✅ OK
   - These are appropriate dependencies

### 6.3 Recommended Dependencies

```kotlin
dependencies {
    implementation(project(":common"))
    implementation(libs.kotlinx.coroutines.core)
    // KMS is optional - DID methods may need it, but core doesn't require it
    // api(project(":kms:core")) // Only if exposing KMS types in public API
}
```

---

## 7. Detailed Refactoring Steps

### Phase 1: Remove `Any` Types from Service Interfaces

1. **Delete `DidMethodService` and adapter**
   ```kotlin
   // DELETE: services/DidMethodService.kt
   // DELETE: services/DidMethodServiceAdapter.kt
   // UPDATE: All usages to use DidMethod directly
   ```

2. **Refactor `DidDocumentAccess`**
   ```kotlin
   // BEFORE:
   interface DidDocumentAccess {
       fun getDocument(result: Any): Any?
       fun getVerificationMethod(doc: Any): List<Any>
   }
   
   // AFTER: Remove interface, use types directly
   // In DelegationService:
   private fun getVerificationMethod(doc: DidDocument): List<VerificationMethodRef> {
       return doc.verificationMethod
   }
   ```

3. **Update `DelegationService`**
   ```kotlin
   // BEFORE:
   class DelegationService(
       private val didResolver: DidResolver,
       private val documentAccess: DidDocumentAccess,  // Remove
       private val verificationMethodAccess: VerificationMethodAccess  // Remove
   )
   
   // AFTER:
   class DelegationService(
       private val didResolver: DidResolver
   ) {
       // Use DidDocument directly, no access services needed
   }
   ```

### Phase 2: Remove Reflection from DelegationService

**File**: `delegation/DelegationService.kt`

**Changes:**
- Remove all `javaClass.getDeclaredField()` calls
- Use `DidDocument`, `Service`, `VerificationMethodRef` properties directly
- Remove `DidDocumentAccess` and `VerificationMethodAccess` dependencies

### Phase 3: Move DSL Classes to Trust Module

1. Move files:
   ```
   did/core/dsl/* → trust/dsl/did/*
   ```

2. Update package names:
   ```kotlin
   // FROM:
   package com.trustweave.did.dsl
   
   // TO:
   package com.trustweave.trust.dsl.did
   ```

3. Update imports in `trust` module

### Phase 4: Refactor DidDocumentDsl

**File**: `dsl/DidDocumentDsl.kt` (or `trust/dsl/did/DidDocumentDsl.kt` after move)

**Changes:**
- Remove all reflection code (lines 160-486)
- Use `DidDocument.copy()` with proper types
- Use `DidMethod.updateDid()` with typed updater function

### Phase 5: Package Reorganization

1. Create new package structure:
   ```
   did/core/src/main/kotlin/com/trustweave/did/
   ├── core/
   ├── registry/
   ├── resolution/
   ├── validation/
   ├── delegation/
   ├── exception/
   ├── spi/
   └── util/
   ```

2. Move files to appropriate packages

3. Update all imports

### Phase 6: Update Error Handling

**File**: `DefaultDidMethodRegistry.kt`

**Change:**
```kotlin
// BEFORE:
throw IllegalArgumentException("DID method '${parsed.method}' is not registered...")

// AFTER:
throw DidError.DidMethodNotRegistered(
    method = parsed.method,
    availableMethods = getAllMethodNames()
)
```

---

## 8. Impact Analysis

### 8.1 Breaking Changes

1. **Service Interfaces Removed**:
   - `DidMethodService` - DELETE
   - `DidDocumentAccess` - DELETE or REFACTOR
   - `VerificationMethodAccess` - DELETE
   - `ServiceAccess` - DELETE

2. **DSL Classes Moved**:
   - All DSL classes move from `did:core` to `trust` module
   - Package names change

3. **DelegationService Constructor**:
   - Removes `DidDocumentAccess` and `VerificationMethodAccess` parameters

### 8.2 Migration Guide

**For users of `DidMethodService`:**
```kotlin
// BEFORE:
val service: DidMethodService = DidMethodServiceAdapter()
val doc = service.createDid(method, options)

// AFTER:
val doc = method.createDid(options)
```

**For users of `DidDocumentAccess`:**
```kotlin
// BEFORE:
val access: DidDocumentAccess = DidDocumentAccessAdapter()
val vms = access.getVerificationMethod(doc)

// AFTER:
val vms = doc.verificationMethod
```

**For users of DSL:**
```kotlin
// BEFORE:
import com.trustweave.did.dsl.*

// AFTER:
import com.trustweave.trust.dsl.did.*
```

---

## 9. Summary of Changes

### Files to DELETE:
1. `services/DidMethodService.kt`
2. `services/DidMethodServiceAdapter.kt`
3. `services/DidDocumentAccess.kt` (or refactor)
4. `services/DidDocumentAccessAdapter.kt` (or refactor)

### Files to MOVE:
1. `dsl/DidDslProvider.kt` → `trust/dsl/did/DidDslProvider.kt`
2. `dsl/DidDsl.kt` → `trust/dsl/did/DidDsl.kt`
3. `dsl/DidDocumentDsl.kt` → `trust/dsl/did/DidDocumentDsl.kt`
4. `dsl/DelegationDsl.kt` → `trust/dsl/did/DelegationDsl.kt`

### Files to REFACTOR:
1. `delegation/DelegationService.kt` - Remove reflection, use types directly
2. `registry/DefaultDidMethodRegistry.kt` - Use `DidError` instead of `IllegalArgumentException`
3. `dsl/DidDocumentDsl.kt` - Remove reflection (before moving)

### Package Structure Changes:
- Consolidate `services/` into core packages
- Move `dsl/` to `trust` module

### Type Safety Improvements:
- Remove all `Any` types from service interfaces
- Use proper types: `DidDocument`, `VerificationMethodRef`, `Service`
- Remove reflection in favor of direct property access

---

## 10. Expected Benefits

1. **Type Safety**: Eliminate 129+ instances of `Any` usage
2. **Simplification**: Remove unnecessary adapter pattern
3. **Maintainability**: Remove 300+ lines of reflection code
4. **Clarity**: Better package organization and naming
5. **Self-Containment**: Clear module boundaries
6. **Performance**: Direct property access instead of reflection

---

## 11. Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Breaking changes for DSL users | Medium | Provide migration guide, version bump |
| Breaking changes for service users | Low | Most are internal, provide migration |
| Reflection removal bugs | Low | Comprehensive testing |
| Package move complexity | Medium | Automated refactoring tools |

---

## 12. Testing Strategy

1. **Unit Tests**: Update all tests to use new types
2. **Integration Tests**: Verify DSL still works after move
3. **Migration Tests**: Test backward compatibility helpers
4. **Performance Tests**: Verify reflection removal improves performance

---

## Next Steps

1. ✅ Review and approve this plan
2. Create feature branch: `refactor/did-core-consolidation`
3. Execute Phase 1 (Remove `Any` types)
4. Execute Phase 2 (Remove reflection)
5. Execute Phase 3 (Move DSL)
6. Execute Phase 4 (Refactor DSL)
7. Execute Phase 5 (Package reorganization)
8. Execute Phase 6 (Error handling)
9. Update documentation
10. Update tests
11. Create migration guide
12. Release with major version bump

