# Final Hypothesis Test Summary

## Hypothesis Confirmed ✅

**The `:did:core` project dependency IS triggering the circular dependency, regardless of dependency configuration type.**

## Complete Test Results

### Test 1: No Project Dependencies ✅
- **Result**: NO circular dependency
- **Conclusion**: Circular dependency requires project dependencies

### Test 2: Only `:common` Dependency ✅
- **Result**: NO circular dependency
- **Conclusion**: `:common` does NOT trigger the issue

### Test 3: Only `:did:core` Dependency ❌
- **Result**: Circular dependency APPEARS
- **Conclusion**: **`:did:core` IS the trigger**

### Test 4: `:did:core` with `api` instead of `implementation` ❌
- **Result**: Circular dependency STILL APPEARS
- **Conclusion**: Dependency configuration type doesn't matter

### Test 5: `:did:core` with `compileOnly` ❌
- **Result**: Circular dependency STILL APPEARS
- **Conclusion**: Even `compileOnly` triggers it - it's about the **presence** of the dependency

### Test 6: Both `:common` and `:did:core` with `api` ❌
- **Result**: Circular dependency STILL APPEARS
- **Conclusion**: Using `api` for both dependencies doesn't help - dependency configuration type is NOT the solution

## Key Findings

1. **`:did:core` dependency is the root cause** - triggers circular dependency regardless of configuration
2. **`:common` dependency is safe** - does not trigger the issue
3. **Dependency configuration type doesn't matter** - `implementation`, `api`, and `compileOnly` all trigger it
4. **It's about the presence of the dependency** - not how it's configured

## Root Cause

The Kotlin plugin's `archivesTaskOutputAsFriendModule` feature is triggered when:
- `compileKotlin` needs to compile code that references `:did:core`
- The plugin needs to access `:did:core`'s JAR artifact
- This triggers the friend module behavior
- Plugin creates `compileKotlin → jar` dependency
- Creates circular dependency: `compileKotlin → jar → classes → compileJava → compileKotlin`

## Why `kotlin.build.archivesTaskOutputAsFriendModule=false` Doesn't Work

The property is:
- ✅ Read correctly by Gradle
- ❌ NOT respected by the Kotlin plugin
- ❌ The dependency is still created

This confirms the fix in KT-69330 is incomplete or doesn't work for this specific case.

## Impact

This is a **critical finding**:
- The issue is specifically triggered by `:did:core` project dependency
- No workaround using dependency configuration types
- The documented fix (`kotlin.build.archivesTaskOutputAsFriendModule=false`) doesn't work
- This needs to be reported to JetBrains as a bug in the fix

## Next Steps

1. ✅ Document all test results
2. ⚠️ Check if there's something specific about `:did:core` that triggers this
3. ⚠️ Comment on KT-69330 with these findings
4. ⚠️ Consider if there's a way to work around the `:did:core` dependency issue

