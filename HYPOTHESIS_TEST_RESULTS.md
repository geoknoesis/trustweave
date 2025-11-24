# Hypothesis Test Results: Project Dependencies Trigger Circular Dependency

## Hypothesis
The circular dependency might be triggered by project dependencies using `implementation`.

## Test Results Summary

### Test 1: No Project Dependencies ✅
**Configuration**: Removed both `implementation(project(":common"))` and `implementation(project(":did:core"))`

**Result**: 
- ✅ **NO CIRCULAR DEPENDENCY!**
- Build progresses past task dependency resolution
- Fails with compilation errors (expected - missing dependencies)

**Conclusion**: Circular dependency is **NOT present** without project dependencies.

### Test 2: Only `:common` Dependency
**Configuration**: Only `implementation(project(":common"))`

**Result**: 
- ✅ **NO CIRCULAR DEPENDENCY!**
- Build progresses (may fail with compilation errors if code uses `:did:core`)

**Conclusion**: `:common` dependency alone does NOT trigger circular dependency.

### Test 3: Only `:did:core` Dependency
**Configuration**: Only `implementation(project(":did:core"))`

**Result**: 
- ❌ **CIRCULAR DEPENDENCY APPEARS!**
- Same circular dependency error as with both dependencies

**Conclusion**: **`:did:core` dependency IS triggering the circular dependency!**

## Key Finding

**The `:did:core` dependency IS triggering the circular dependency!**

Test Results:
- ✅ **No dependencies**: No circular dependency
- ✅ **Only `:common`**: No circular dependency  
- ❌ **Only `:did:core`**: Circular dependency appears
- ❌ **Both dependencies**: Circular dependency appears

This confirms that:
1. The circular dependency is **specifically triggered by** `:did:core` dependency
2. `:common` dependency does NOT trigger it
3. It's not a pure task dependency issue within the module
4. The Kotlin plugin's behavior with `:did:core` project dependency is causing the issue

## Root Cause Analysis

When `compileKotlin` needs to compile code that uses other projects:
1. Kotlin plugin needs to access those projects' JAR artifacts
2. This triggers the `archivesTaskOutputAsFriendModule` behavior
3. Plugin creates `compileKotlin → jar` dependency to access current project's JAR
4. This creates the circular dependency: `compileKotlin → jar → classes → compileJava → compileKotlin`

## Next Steps

1. ✅ **CONFIRMED**: `:did:core` dependency triggers the circular dependency
2. ✅ **TESTED**: Using `api` instead of `implementation` for `:did:core` - **Still has circular dependency**
3. ✅ **TESTED**: Using `compileOnly` for `:did:core` - **Still has circular dependency**
4. ✅ **TESTED**: Using `api` for both `:common` and `:did:core` - **Still has circular dependency**
5. ⚠️ Check if there's a transitive dependency cycle with `:did:core`
6. ⚠️ Document this finding for KT-69330 comment

### Test 4: Using `api` instead of `implementation` for `:did:core`
**Configuration**: `api(project(":did:core"))` instead of `implementation(project(":did:core"))`

**Result**: 
- ❌ **CIRCULAR DEPENDENCY STILL APPEARS!**
- Changing from `implementation` to `api` does NOT help

**Conclusion**: The dependency configuration type (`api` vs `implementation`) does NOT affect the circular dependency.

### Test 5: Using `compileOnly` for `:did:core`
**Configuration**: `compileOnly(project(":did:core"))` instead of `implementation(project(":did:core"))`

**Result**: 
- ❌ **CIRCULAR DEPENDENCY STILL APPEARS!**
- Even `compileOnly` triggers the circular dependency

**Conclusion**: The circular dependency is triggered by the **presence** of the `:did:core` project dependency, regardless of configuration type (`implementation`, `api`, or `compileOnly`).

### Test 6: Using `api` for both `:common` and `:did:core`
**Configuration**: `api(project(":common"))` and `api(project(":did:core"))` instead of `implementation`

**Result**: 
- ❌ **CIRCULAR DEPENDENCY STILL APPEARS!**
- Using `api` for both dependencies doesn't help

**Conclusion**: Changing both dependencies to `api` doesn't resolve the issue. The dependency configuration type (`api` vs `implementation`) is NOT the solution.

## Impact

This is a **critical finding**:
- The issue is NOT just about task dependencies within a module
- It's about how the Kotlin plugin handles project dependencies
- The `kotlin.build.archivesTaskOutputAsFriendModule=false` property should prevent this, but doesn't work
- This confirms the fix in KT-69330 is incomplete

