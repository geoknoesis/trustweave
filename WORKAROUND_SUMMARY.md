# Workaround Attempts Summary

## All Attempted Workarounds (All Failed)

### 1. ✅ Property Configuration
- Set `kotlin.build.archivesTaskOutputAsFriendModule=false` in `gradle.properties`
- **Result**: Property is read correctly but ignored by plugin

### 2. ❌ System Property
- Set property in `settings.gradle.kts` before plugins load
- **Result**: Not working

### 3. ❌ Project Property
- Set property in module `build.gradle.kts` before plugins
- **Result**: Not working

### 4. ❌ Task Dependency Removal in `afterEvaluate`
- Remove `jar` from `compileKotlin` dependencies
- **Result**: Dependency cannot be removed (added internally by plugin)

### 5. ❌ Task Interception with `whenTaskAdded`
- Intercept `compileKotlin` when created and remove `jar` dependency
- **Result**: Dependency is added after interception

### 6. ❌ Disable `compileJava` Task
- Disable `compileJava` since there are no Java sources
- **Result**: Circular dependency persists (compileKotlin → jar still exists)

### 7. ❌ Break Cycle at Jar → Classes
- Make `jar` depend on output files instead of `classes` task
- **Result**: Circular dependency persists

### 8. ❌ Break Cycle at CompileJava → CompileKotlin
- Remove `compileJava` from `compileKotlin` dependencies
- **Result**: Circular dependency persists

### 9. ❌ Task Graph Manipulation
- Use `gradle.taskGraph.whenReady` to manipulate dependencies at runtime
- **Result**: Too late - task graph already built with cycle

### 10. ❌ Custom Jar Task
- Disable default `jar` and create custom one
- **Result**: Syntax errors and complexity

### 11. ❌ Multiple Approaches Combined
- All workarounds applied simultaneously
- **Result**: Circular dependency persists

## Root Cause

The Kotlin Gradle Plugin 2.2.21 creates the `compileKotlin → jar` dependency through an **internal mechanism** that:
- Cannot be prevented by the documented property
- Cannot be removed using standard Gradle APIs
- Is added in a way that bypasses normal task dependency management

## Conclusion

**No workaround has been found that successfully breaks the circular dependency.**

The issue is a **bug in Kotlin Gradle Plugin 2.2.21** that requires a fix from JetBrains.

## Recommended Actions

1. **File bug report** to JetBrains with `BUG_REPRODUCER.md`
2. **Test with Kotlin 2.2.20** to see if property works there
3. **Temporarily exclude module** from builds until fix is available
4. **Monitor Kotlin releases** for fix

## Temporary Build Workaround

Until the bug is fixed, you can:

1. **Skip the module in CI/CD**:
   ```bash
   ./gradlew build -x :credentials:core:build
   ```

2. **Build other modules separately**:
   ```bash
   ./gradlew :common:build :did:core:build
   ```

3. **Use a build script** that handles the circular dependency gracefully

