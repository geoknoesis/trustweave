# Circular Dependency Issue - Status Report

## Current Situation

The circular dependency issue in `credentials:core` cannot be resolved with any of the attempted solutions:

### ❌ Attempted Solutions (All Failed)

1. **Official Property**: `kotlin.build.archivesTaskOutputAsFriendModule=false` in `gradle.properties`
   - Status: Not working (bug in Kotlin 2.2.21)
   - Documentation: https://kotlinlang.org/docs/gradle-configure-project.html#other-details

2. **System Property**: Setting property in `settings.gradle.kts` before plugins load
   - Status: Not working

3. **Project Property**: Setting property in module `build.gradle.kts` before plugins
   - Status: Not working

4. **Task Dependency Removal in `afterEvaluate`**: Removing `jar` from `compileKotlin` dependencies
   - Status: Not working - dependency is added in a way that can't be removed

5. **Task Interception with `whenTaskAdded`**: Intercepting task when created
   - Status: Not working

6. **Multiple Configuration Points**: Trying all approaches simultaneously
   - Status: Not working

## Root Cause

The Kotlin Gradle Plugin 2.2.21 is adding the `compileKotlin → jar` dependency through an internal mechanism that:
- Cannot be prevented by the documented property
- Cannot be removed after the fact using standard Gradle APIs
- Creates a circular dependency: `compileKotlin → jar → classes → compileJava → compileKotlin`

## Available Options

### Option 1: Downgrade Kotlin Version ⚠️
**Risk**: May lose features or have other compatibility issues

```kotlin
// In settings.gradle.kts
plugins {
    kotlin("jvm") version "2.2.20"  // or 2.1.21
    kotlin("plugin.serialization") version "2.2.20"
}
```

**Action**: Test if the property works in earlier versions.

### Option 2: Restructure Project 🔧
**Risk**: Significant refactoring required

- Split `credentials:core` into smaller modules
- Remove Java compilation from the module (if possible)
- Change module dependencies to break the cycle

### Option 3: Wait for JetBrains Fix 🐛
**Risk**: Blocks development until fix is available

- File bug report (see `BUG_REPRODUCER.md`)
- Monitor Kotlin releases for fix
- Use workaround in the meantime (if one is found)

### Option 4: Temporary Build Workaround 🚧
**Risk**: Fragile, may break with updates

Skip the problematic module in CI/CD:
```bash
./gradlew build -x :credentials:core:build
```

Or build it separately with a workaround script.

## Recommendation

1. **Immediate**: File bug report to JetBrains with `BUG_REPRODUCER.md`
2. **Short-term**: Test Kotlin 2.2.20 to see if property works there
3. **Long-term**: Consider restructuring if issue persists

## Impact Assessment

- **Build**: ❌ Cannot build `credentials:core` module
- **Development**: ⚠️ Blocks work on credentials functionality
- **CI/CD**: ❌ Fails on full project builds
- **Other Modules**: ✅ Not affected (only `credentials:core`)

## Next Steps

1. Review this document with the team
2. Decide on approach (downgrade vs restructure vs wait)
3. File bug report to JetBrains
4. Document decision in project README

