# KT-69330 Resolution Analysis

## Issue Status
**YouTrack Issue**: [KT-69330](https://youtrack.jetbrains.com/issue/KT-69330) - **RESOLVED**

## Fix Information
According to documentation:
- **Fix introduced in**: Kotlin 2.0.20
- **Fix mechanism**: The `archivesTaskOutputAsFriendModule` property was introduced
- **Property**: `kotlin.build.archivesTaskOutputAsFriendModule=false` should prevent circular dependencies

## Our Current Situation

### Versions Tested
- **Kotlin 2.0.20+**: Should include the fix
- **Kotlin 2.2.21**: Current version (should definitely include the fix)
- **Result**: ❌ **Issue still persists!**

### Evidence
1. ✅ Property is set: `kotlin.build.archivesTaskOutputAsFriendModule=false`
2. ✅ Property is read: Verified via debug output
3. ❌ Property is NOT respected: Circular dependency still occurs
4. ❌ Build fails with the same error

## Possible Explanations

### 1. Fix Doesn't Work for Our Configuration
- The fix may only work in specific scenarios
- Our multi-module setup with specific dependencies might not be covered
- The `credentials:core` module has a unique configuration

### 2. Regression
- The fix worked in 2.0.20 but broke in a later version
- We tested 2.0.0, 2.1.0, 2.2.0, 2.2.20, 2.2.21 - all have the issue
- This suggests the fix never worked, or there's a regression

### 3. Different Issue
- Our issue might be similar but not exactly the same as KT-69330
- The root cause might be different
- The property might work for other cases but not ours

### 4. Incomplete Fix
- The fix was marked as "resolved" but doesn't actually work
- The property is read but the plugin still creates the dependency
- This matches our findings exactly

## Next Steps

1. ✅ **Tested with Kotlin 2.0.20** (the exact fix version) - **STILL BROKEN!**
2. ⚠️ **Check YouTrack issue comments** for similar reports of the fix not working
3. ⚠️ **File a new issue** or comment on KT-69330 that the fix doesn't work
4. ✅ **Documented our findings** that the "resolved" issue is still broken

## Recommendation

Since the issue is marked as "resolved" but we're still experiencing it:
1. ✅ **Tested with Kotlin 2.0.20** (the fix version) - **STILL BROKEN!**
2. ✅ **Conclusion**: The fix **never worked** - even in the version where it was supposedly fixed
3. ⚠️ **File a follow-up issue** or comment on KT-69330 with our findings
4. ⚠️ **Document that KT-69330 is incorrectly marked as "resolved"**

## Test Results Summary

### Versions Where Issue Persists
- Kotlin 1.9.24, 2.0.0, **2.0.20 (fix version!)**, 2.1.0, 2.2.0, 2.2.20, 2.2.21
- Gradle 8.0, 8.5, 8.10, 9.2.0

**Critical Finding**: Even **Kotlin 2.0.20** (the version where the fix was supposedly introduced) **still has the bug!**

### Key Finding
The property `kotlin.build.archivesTaskOutputAsFriendModule=false`:
- Is documented as the solution
- Is marked as "resolved" in KT-69330
- Is read correctly by Gradle
- **Still doesn't work** in any tested version

This suggests the "fix" in KT-69330 either:
- Never actually worked
- Doesn't cover our use case
- Was broken in a later version

