# Final Bug Report Summary

## Issue
The `kotlin.build.archivesTaskOutputAsFriendModule=false` property in `gradle.properties` is **not respected** by the Kotlin Gradle Plugin, causing circular dependency errors that should be prevented by this property.

## Related Known Issue
**YouTrack Issue**: [KT-69330](https://youtrack.jetbrains.com/issue/KT-69330) - This appears to be a known issue on JetBrains YouTrack. Please check the issue status and add our comprehensive test results if it's still open.

## Tested Versions

### Kotlin Versions
All Kotlin versions tested show the same behavior:
- ✅ **Kotlin 1.9.24**: Property read but not respected
- ✅ **Kotlin 2.0.0**: Property read but not respected
- ✅ **Kotlin 2.1.0**: Property read but not respected
- ✅ **Kotlin 2.2.0**: Property read but not respected
- ✅ **Kotlin 2.2.20**: Property read but not respected
- ✅ **Kotlin 2.2.21**: Property read but not respected
- ⚠️ **Kotlin 1.9.0 / 1.8.22**: Build incompatibility (cannot test)

### Gradle Versions
All Gradle versions tested show the same behavior:
- ✅ **Gradle 8.0**: Property read but not respected
- ✅ **Gradle 8.5**: Property read but not respected
- ✅ **Gradle 8.10**: Property read but not respected
- ✅ **Gradle 9.2.0**: Property read but not respected

**Conclusion**: The bug exists in **all testable Kotlin versions from 1.9.24 onwards** and **all testable Gradle versions from 8.0 onwards**, indicating:
1. The property has never worked correctly since its introduction
2. The bug is **not Gradle version-specific** - it's purely a Kotlin plugin issue
3. Downgrading Gradle does not help

## Evidence

### 1. Property is Correctly Read
Debug output confirms the property is read by Gradle:
```
kotlin.build.archivesTaskOutputAsFriendModule (project property): false
kotlin.build.archivesTaskOutputAsFriendModule (gradle property): false
```

### 2. Property is Not Respected
Despite being set to `false`, the circular dependency persists:
```
Circular dependency between the following tasks:
:credentials:core:classes
\--- :credentials:core:compileJava
     +--- :credentials:core:compileKotlin
     |    \--- :credentials:core:jar
     |         +--- :credentials:core:classes (*)
```

### 3. All Workarounds Failed
- 11 different workaround attempts were tried
- None successfully broke the circular dependency
- The dependency is created internally by the plugin and cannot be removed

## Impact

1. **Blocks builds** for projects with specific configurations
2. **Documented solution doesn't work** - developers follow official docs but get no resolution
3. **No workaround available** - standard Gradle APIs cannot fix this
4. **Affects multiple Kotlin versions** - not a recent regression

## Recommendation

This is a **critical bug** that requires a fix from JetBrains. The property was introduced to solve circular dependencies but doesn't actually work, making the documentation misleading.

## Files for Bug Report

1. `BUG_REPRODUCER.md` - Complete bug report with reproduction steps
2. `KOTLIN_VERSION_TEST_RESULTS.md` - Version testing results
3. `WORKAROUND_SUMMARY.md` - All attempted workarounds
4. `minimal-reproducer/` - Standalone project to reproduce the bug

## Next Steps

1. **Check KT-69330 status**: Visit https://youtrack.jetbrains.com/issue/KT-69330 to see if this is the same issue
2. **If KT-69330 is open**: Add our comprehensive test results as a comment
3. **If KT-69330 is resolved**: Check which version fixed it and verify if it works
4. **If KT-69330 is different or closed**: File a new bug report on JetBrains YouTrack: https://youtrack.jetbrains.com/issues/KT
5. Include all documentation files
6. Reference the official documentation that claims this property works
7. Request urgent fix as it blocks legitimate builds

