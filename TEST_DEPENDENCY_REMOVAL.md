# Test: Remove Project Dependencies to Isolate Issue

## Hypothesis
The circular dependency might be triggered or exacerbated by project dependencies using `implementation`.

## Test Results

### Test 1: Remove All Project Dependencies ✅

**Action**: Commented out `implementation(project(":common"))` and `implementation(project(":did:core"))`

**Result**: 
- ❌ **Circular dependency is GONE!**
- ✅ Build progresses past task dependency resolution
- ❌ Fails with compilation errors (expected - missing dependencies)

**Conclusion**: **Project dependencies ARE triggering the circular dependency!**

### Analysis

When project dependencies are removed:
- No circular dependency error
- Task dependency resolution succeeds
- Build fails only due to missing imports/classes (compilation errors)

This strongly suggests that:
1. The circular dependency is **triggered by** project dependencies
2. When `compileKotlin` needs to compile code that uses other projects (`:common`, `:did:core`)
3. The Kotlin plugin needs to access those projects' JAR artifacts
4. This triggers the `archivesTaskOutputAsFriendModule` behavior
5. Which creates the `compileKotlin → jar` dependency
6. Leading to the circular dependency

### Next Steps

1. Test with only `:common` dependency (remove `:did:core`)
2. Test with only `:did:core` dependency (remove `:common`)
3. Determine which dependency is causing the issue
4. Try using `api` instead of `implementation` to see if it makes a difference
5. Try using `compileOnly` for one of them to see if it helps

## Current Dependencies (Modified for Testing)

```kotlin
dependencies {
    // TEMPORARILY COMMENTED OUT TO TEST IF PROJECT DEPENDENCIES TRIGGER CIRCULAR DEPENDENCY
    // implementation(project(":common"))
    // implementation(project(":did:core"))
    
    testImplementation(project(":testkit"))
    testImplementation(project(":kms:core"))
}
```

## Test Command

```bash
.\gradlew.bat :credentials:core:build
```

